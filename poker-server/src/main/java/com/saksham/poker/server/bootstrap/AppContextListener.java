package com.saksham.poker.server.bootstrap;

import com.saksham.poker.server.auth.PasswordHasher;
import com.saksham.poker.server.auth.SessionService;
import com.saksham.poker.server.db.AuthTokenDao;
import com.saksham.poker.server.db.DataSourceProvider;
import com.saksham.poker.server.db.MigrationRunner;
import com.saksham.poker.server.db.RoomDao;
import com.saksham.poker.server.db.UserDao;
import com.saksham.poker.server.io.ServerConfig;
import com.saksham.poker.server.lan.NetworkInfo;
import com.saksham.poker.server.room.RoomCodeGenerator;
import com.saksham.poker.server.room.RoomService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds everything the server needs when Tomcat starts the web app, and shuts it down cleanly. */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppContextListener.class);

    private DataSourceProvider database;

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

        SessionService sessions = new SessionService(users, tokens, new PasswordHasher(), clock,
                config.tokenLifetime());
        RoomService roomService = new RoomService(rooms, users, new RoomCodeGenerator());
        new AppContext(config, sessions, roomService).storeIn(servletContext);

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
        if (database != null) {
            database.close();
            log.info("Hold'em server stopped");
        }
    }
}
