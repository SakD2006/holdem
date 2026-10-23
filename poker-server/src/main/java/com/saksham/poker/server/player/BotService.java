package com.saksham.poker.server.player;

import com.saksham.poker.ai.Strategies;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.NotInRoomException;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.common.protocol.dto.BotLevel;
import com.saksham.poker.server.db.BotAccount;
import com.saksham.poker.server.db.BotAccountDao;
import com.saksham.poker.server.room.RoomCommand;
import com.saksham.poker.server.room.RoomManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts computer players into rooms when a host asks. Each bot has an account of its own, so its
 * hands are recorded like anyone's; accounts are reused from room to room and a new one is made
 * only when every existing one of that level is busy.
 */
public final class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    /**
     * First names for bots, after people who shaped computing and mathematics. Each level has names
     * of its own, so a name always means the same strength.
     */
    static final Map<BotLevel, List<String>> NAMES = Map.of(
            BotLevel.EASY, List.of("Ada", "Babbage", "Curie", "Dijkstra", "Euler", "Fermat"),
            BotLevel.MEDIUM, List.of("Gauss", "Hopper", "Knuth", "Lovelace", "Noether", "Turing"));
    /** How many account names are tried before giving up; far more than any server needs. */
    private static final int MAX_ACCOUNTS = 500;

    private final BotAccountDao accounts;
    private final RoomManager rooms;
    private final ScheduledExecutorService thinkers;
    private final long thinkMinMs;
    private final long thinkMaxMs;

    /**
     * @param accounts where bot accounts are kept
     * @param rooms the open rooms
     * @param thinkers the threads bots think on
     * @param thinkMinMs the shortest pause a bot takes before acting, in milliseconds
     * @param thinkMaxMs the longest
     */
    public BotService(BotAccountDao accounts, RoomManager rooms, ScheduledExecutorService thinkers, long thinkMinMs,
            long thinkMaxMs) {
        this.accounts = accounts;
        this.rooms = rooms;
        this.thinkers = thinkers;
        this.thinkMinMs = thinkMinMs;
        this.thinkMaxMs = thinkMaxMs;
    }

    /**
     * Seats a bot of the given level in the room the host is in. Whether the asker really is the
     * host, and whether there is a free seat, is decided by the room, which tells the host if not.
     *
     * @throws NotInRoomException if the asker is not in a room
     * @throws InvalidRequestException if no level was given
     */
    public void add(long hostUserId, BotLevel level) throws PokerException {
        if (level == null) {
            throw new InvalidRequestException("Choose how well the bot should play.");
        }
        if (rooms.roomOf(hostUserId).isEmpty()) {
            throw new NotInRoomException("You are not in a room. Join one with its code first.");
        }
        // First an account that already exists and is not playing anywhere.
        for (BotAccount account : accounts.findByLevel(level)) {
            if (rooms.roomOf(account.id()).isEmpty() && seat(hostUserId, account)) {
                return;
            }
        }
        // All busy, or none yet: make another. A name a person has taken is skipped.
        for (int number = 0; number < MAX_ACCOUNTS; number++) {
            Optional<BotAccount> created = accounts.create(nameFor(level, number), level);
            if (created.isPresent() && seat(hostUserId, created.get())) {
                log.info("Created bot account {} ({})", created.get().username(), level);
                return;
            }
        }
        throw new InvalidRequestException("No bot is available right now. Try again in a moment.");
    }

    private boolean seat(long hostUserId, BotAccount account) throws NotInRoomException {
        AiController controller = new AiController(account.id(), account.username(),
                Strategies.forLevel(account.level()), new Random(), this::submit, thinkers, thinkMinMs, thinkMaxMs);
        return rooms.addBot(hostUserId, account.id(), account.username(), controller);
    }

    /** A bot's command for its room. If the bot has just been removed, there is nothing to do. */
    private void submit(long userId, RoomCommand command) {
        try {
            rooms.submit(userId, command);
        } catch (NotInRoomException e) {
            log.debug("Bot {} is no longer in a room; dropped {}", userId, command.getClass().getSimpleName());
        }
    }

    /** Ada_bot, Babbage_bot, ... then Ada_bot2, Babbage_bot2, and so on. */
    static String nameFor(BotLevel level, int number) {
        List<String> names = NAMES.get(level);
        String name = names.get(number % names.size()) + "_bot";
        int round = number / names.size();
        return round == 0 ? name : name + (round + 1);
    }
}
