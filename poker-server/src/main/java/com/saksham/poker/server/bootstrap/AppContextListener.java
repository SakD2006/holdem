package com.saksham.poker.server.bootstrap;

import com.saksham.poker.common.protocol.MessageCodec;
import com.saksham.poker.engine.card.SecureDeckFactory;
import com.saksham.poker.server.auth.PasswordHasher;
import com.saksham.poker.server.auth.SessionService;
import com.saksham.poker.server.db.AuthTokenDao;
import com.saksham.poker.server.db.DataSourceProvider;
import com.saksham.poker.server.db.HandDao;
import com.saksham.poker.server.db.HandRecordWriter;
import com.saksham.poker.server.db.MigrationRunner;
import com.saksham.poker.server.db.RoomDao;
import com.saksham.poker.server.db.UserDao;
import com.saksham.poker.server.io.HandHistoryFileWriter;
import com.saksham.poker.server.io.ServerConfig;
import com.saksham.poker.server.lan.NetworkInfo;
import com.saksham.poker.server.room.AsyncRoomStore;
import com.saksham.poker.server.room.RoomCodeGenerator;
import com.saksham.poker.server.room.RoomManager;
import com.saksham.poker.server.room.RoomService;
import com.saksham.poker.server.ws.ConnectionRegistry;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds everything the server needs when Tomcat starts the web app, and shuts it down cleanly. */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppContextListener.class);

    /** Turn timers, the pause between hands and reconnect grace all share these threads. */
    private static final int TIMER_THREADS = 2;
    /** A hand the database refuses is tried this many times before it is written to a file instead. */
    private static final int SAVE_ATTEMPTS = 3;
    private static final long SAVE_RETRY_MS = 500;

    private DataSourceProvider database;
    private ScheduledExecutorService timers;
    private AsyncRoomStore roomStore;
    private HandRecordWriter handWriter;
    private RoomManager roomManager;
    private ConnectionRegistry connections;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext servletContext = event.getServletContext();
        ServerConfig config = ServerConfig.load();
        try {
            database = new DataSourceProvider(config);
        } catch (RuntimeException e) {
            log.error("Could not connect to PostgreSQL at {} as user \"{}\". Start it with "
                    + "\"docker compose up -d\", or correct db.url, db.user and db.password in "
                    + "server.properties, then start the server again.", config.dbUrl(), config.dbUser());
            throw e;
        }
        DataSource dataSource = database.dataSource();
        new MigrationRunner(dataSource).migrate();

        Clock clock = Clock.systemUTC();
        UserDao users = new UserDao(dataSource);
        AuthTokenDao tokens = new AuthTokenDao(dataSource);
        RoomDao rooms = new RoomDao(dataSource);

        // Rooms live in memory, so any the database still shows as open did not survive a restart.
        int closed = rooms.closeAllOpen(clock.instant());
        if (closed > 0) {
            log.info("Closed {} room(s) left open when the server last stopped", closed);
        }
        tokens.deleteExpired(clock.instant());

        AtomicInteger timerNumber = new AtomicInteger();
        timers = Executors.newScheduledThreadPool(TIMER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "room-timer-" + timerNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        roomStore = new AsyncRoomStore(rooms, clock);
        HandDao hands = new HandDao(dataSource);
        handWriter = new HandRecordWriter(hands::save,
                new HandHistoryFileWriter(config.dataFolder().resolve("hand-history"), ZoneId.systemDefault()),
                config.dataFolder().resolve("failed-hands"), SAVE_ATTEMPTS, SAVE_RETRY_MS);
        roomManager = new RoomManager(timers, roomStore, handWriter::submit, config.roomTimings(),
                new SecureDeckFactory(), clock);
        connections = new ConnectionRegistry();

        SessionService sessions = new SessionService(users, tokens, new PasswordHasher(), clock,
                config.tokenLifetime());
        RoomService roomService = new RoomService(rooms, users, new RoomCodeGenerator(), roomManager);
        new AppContext(config, sessions, roomService, roomManager, hands, connections, new MessageCodec())
                .storeIn(servletContext);

        List<String> urls = NetworkInfo.serverUrls(config.httpPort(), servletContext.getContextPath());
        if (urls.isEmpty()) {
            log.warn("Hold'em server started, but this computer is not on a local network. "
                    + "Only this computer can connect, at http://127.0.0.1:{}{}",
                    config.httpPort(), servletContext.getContextPath());
        }
        for (String url : urls) {
            log.info("Players can connect to: {}", url);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        AppContext.clear();
        // Stop taking work first, then let what is already queued for the database finish.
        if (connections != null) {
            connections.closeAll();
        }
        if (roomManager != null) {
            roomManager.shutdown();
        }
        if (timers != null) {
            timers.shutdownNow();
        }
        if (roomStore != null) {
            roomStore.close();
        }
        if (handWriter != null) {
            // Every hand already finished is saved before the database connection goes.
            handWriter.close();
        }
        if (database != null) {
            database.close();
            log.info("Hold'em server stopped");
        }
    }
}
