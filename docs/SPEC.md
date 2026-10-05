# SPEC — LAN No-Limit Texas Hold'em (Java)

Source of truth for behaviour. Section numbers are referenced from `docs/PLAN.md`.

---

## 1. Scope

**Setup:** the server (Tomcat) and PostgreSQL run on one "host machine". Players on the same
Wi-Fi/LAN run the desktop app and connect to `http://<host-ip>:8080/poker`.

**Players can:**
- Connect to the server: type the host IP, or press "Find server" (LAN discovery, §4.6).
- Register and log in inside the desktop app (username + password).
- **Create a room** with: room name, max players (2–9), small/big blind, starting stack,
  turn time (10–60 s), rebuy allowed (yes/no). They get a 6-character **room code** and become
  the room host.
- **Join a room** by entering its code. They take a free seat (they can choose which).
- See everyone in the waiting room; the host presses **Start game** once ≥ 2 players are seated.
- Play hands in real time: fold, check, call, bet, raise, all-in; turn timer; raise presets.
- Chat in the room; see the hand log, showdown cards and winners.
- Sit out / sit back in; rebuy to the starting stack when busted (if the room allows).
- Join a room that is already playing: they take a free seat and are dealt in when the big blind
  reaches them.
- Leave the room. Rejoin with the same code after a disconnect and get their seat back.
- See their own hand history and a simple leaderboard (desktop app and JSP pages).
- Export their hand history to a text file.

**Host-only:** start game, pause/resume between hands, kick a player while the room is waiting,
end the room. If the host leaves, host passes to the next seated player.

**Not in scope:** internet play, hosting, public lobby, bankroll/chip economy, spectators,
tournaments, other variants, installers, AI agents (seam only, §13).

**Chips:** each room is self-contained. Everyone starts with the room's starting stack; results
are recorded per hand (net won/lost) for stats. No persistent bankroll.

**Busted players:** a player with no chips stays in the room, sat out and watching. They can
rebuy if the room allows it; otherwise they watch until they leave.

**One room at a time:** a user can be in only one room. Joining another room first requires
leaving the current one (`ALREADY_IN_ROOM`).

---

## 2. Hold'em rules the engine must implement exactly

2.1 **Deck:** 52 cards, `Collections.shuffle(list, SecureRandom)`; tests inject a stacked deck.

2.2 **Button & blinds:** button moves one active seat clockwise each hand. SB = first active seat
left of button, BB = next. **Heads-up:** button posts SB, acts first preflop, last after.
A player who can't cover a blind posts what they have and is all-in. A player returning from
sit-out waits for the big blind.

2.3 **Streets:** PREFLOP (2 cards each) → FLOP (burn, 3) → TURN (burn, 1) → RIVER (burn, 1) → SHOWDOWN.

2.4 **Action order:** preflop starts left of BB; later streets start first active left of button.

2.5 **Legal actions:**
- No bet facing: CHECK or BET (min = BB, max = stack).
- Facing a bet: FOLD, CALL (capped at stack), RAISE (raise TO an amount).
- Min raise increment = largest bet/raise increment this street (at least BB). All-in for less is allowed.
- An all-in raise smaller than a full min raise does **not** reopen betting for players who
  already acted; they may only call or fold. Betting reopens for a player once the bet has gone
  up by at least a full raise since they last acted, whether from one raise or several short
  all-ins added together.
- A player may bet or raise only if another player still has chips to respond with.
- `ALL_IN` is converted by the engine into the correct BET/CALL/RAISE.
- Street ends when every non-folded, non-all-in player has acted and matched the high bet.
- One player left → wins immediately, no cards shown.
- All but ≤ 1 remaining players all-in → remaining streets dealt automatically.

2.6 **Uncalled bet** returned before pots are built. A bet made by a player who then folded is
not returned; it stays in the pot.

2.7 **Pots:** main + side pots from total contributions, each with eligible (non-folded) players.

2.8 **Showdown:** last river aggressor shows first, else first active left of button, then
clockwise; v1 reveals all remaining hands. Best 5 of 7; categories high card → straight flush
(wheel A-2-3-4-5 counts). Ties split; odd chip to first winner clockwise from button. Each pot
awarded separately.

2.9 **Invariant:** stacks + pots identical before and after every hand (assert + tests).

---

## 3. Engine (`poker-engine`)

```
com.saksham.poker.engine
├── card    Deck, DeckFactory (interface), SecureDeckFactory, StackedDeckFactory (tests)
├── eval    HandEvaluator, HandValue (Comparable), HandCategory
├── hand    HoldemHand (state machine), Street, BettingRound, SeatState, HandConfig
├── pot     Pot, PotCalculator, Payout
├── event   GameEvent (abstract) + subclasses
└── rules   ActionValidator, LegalActions
```

- `HoldemHand` API: `List<GameEvent> start()`, `LegalActions legalActionsFor(int seat)`,
  `List<GameEvent> apply(int seat, PlayerAction a) throws GameRuleException`,
  `List<GameEvent> forceFold(int seat)`, `boolean isComplete()`, `HandResult result()`.
- `forceFold` works whether or not it is that seat's turn (a player leaving mid-hand). If the
  fold is out of turn, the player being waited on gets a new `ActionRequested` only when their
  options changed.
- Seats are numbers; clockwise means the next higher seat number, wrapping round. The button
  must be one of the seats dealt into the hand.
- The engine has no notion of a timeout. The room decides what a timed-out player does (§4.4)
  and calls `apply(seat, Check)` or `forceFold(seat)`.
- Events: `HandStarted`, `BlindPosted`, `HoleCardsDealt` (private), `ActionRequested`,
  `PlayerActed`, `StreetDealt`, `BetsCollected`, `UncalledBetReturned`, `ShowdownRevealed`,
  `PotAwarded`, `HandCompleted`.
- `HandEvaluator`: all 21 five-card combos of 7 cards → `HandValue` (category + kickers).

`poker-common`: `Card`, `Suit`, `Rank`, `ActionType`, abstract `PlayerAction`
(`Fold`, `Check`, `Call`, `Bet`, `Raise`, `AllIn`), `ErrorCode`, `PokerException` tree, messages,
and `MessageCodec` (shared by the server, the desktop app and the bots).

---

## 4. Server (`poker-server`) — rooms, threads, LAN

```
com.saksham.poker.server
├── room       RoomManager, RoomActor, Room, Seat, RoomSettings, RoomCodeGenerator,
│              RoomCommand (abstract) + subclasses, EventRouter, TurnTimer
├── player     SeatController (abstract), RemoteHumanController
├── ws         GameEndpoint, AuthHandshakeConfigurator, Connection, ConnectionRegistry
├── web        API servlets, JSP controller servlets, filters
├── auth       PasswordHasher (PBKDF2), SessionService
├── db         DataSourceProvider, MigrationRunner, BaseDao + DAOs, HandRecordWriter
├── io         ServerConfig, HandHistoryFileWriter
├── lan        DiscoveryResponder (UDP), NetworkInfo (prints LAN IP)
└── bootstrap  AppContextListener
```

4.1 **Room codes:** 6 chars from `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no 0/O/1/I/L), never reused:
unique among all rooms, open or closed. Room states: `WAITING → PLAYING ⇄ PAUSED → CLOSED`. A room closes when the host ends
it, when everyone leaves, or after 30 min with nobody connected.

4.2 **Threads:**

| Thread | Owns | Gets work via |
|---|---|---|
| Tomcat HTTP/WebSocket threads | nothing shared | decode → `RoomManager.submit(code, cmd)` |
| One `RoomActor` per room (single-thread executor) | `Room` + current `HoldemHand` | `LinkedBlockingQueue<RoomCommand>` |
| Shared `ScheduledExecutorService` (2 threads) | turn timers, next-hand delay, run-out pauses, reconnect grace | schedules commands into the room queue |
| Per-connection sender (virtual thread) | one WebSocket session | `Connection.send()` queue |
| `HandRecordWriter` | JDBC writes of finished hands | `BlockingQueue<HandRecord>` |
| `DiscoveryResponder` | UDP socket on port 8888 | replies to LAN broadcasts |

4.3 **Commands** (subclasses of abstract `RoomCommand` with `execute(Room)`): `JoinRoom`,
`TakeSeat`, `LeaveRoom`, `StartGame`, `PauseGame`, `ResumeGame`, `KickPlayer`, `EndRoom`,
`PlayerActionCmd`, `SitOut`, `SitIn`, `Rebuy`, `Chat`, `TurnTimeout`, `Disconnected`,
`Reconnected`, `StartNextHand`. Stale timeouts ignored via `turnId`.

4.4 **Timing** (`server.properties`): turn time from room settings (default 25 s), 3 s between
hands, 1 s pause per run-out street, 60 s reconnect grace (sat out after grace).

**When a turn times out** (connected or not): the room checks for the player if checking is
legal, otherwise folds them. It never calls or bets for them.

4.5 **SeatController seam:**
```java
public abstract class SeatController {
    protected final long userId;
    public abstract void onActionRequested(ActionRequest request);
    public abstract void onEvent(PlayerView event);   // already filtered for this player
    public abstract boolean isConnected();
}
```

4.6 **LAN:** server binds to `0.0.0.0:8080`; on startup it logs
`Players can connect to: http://192.168.x.x:8080/poker`. `DiscoveryResponder` listens on UDP
8888 for `HOLDEM_DISCOVER` and replies `HOLDEM_SERVER <ip> 8080`. `docs/LAN-SETUP.md` explains
Windows Firewall rules for ports 8080 (TCP) and 8888 (UDP) and that everyone must be on the same
network.

---

## 5. Protocol (WebSocket, JSON)

Endpoint `ws://<host-ip>:8080/poker/ws/game?token=<token>`; bad token → close 4401.
Envelope `{ "type", "seq", "payload" }`.

**Client → server:** `JOIN_ROOM {code}`, `TAKE_SEAT {seat}`, `LEAVE_ROOM`, `START_GAME`,
`PAUSE_GAME`, `RESUME_GAME`, `KICK {userId}`, `END_ROOM`, `ACTION {turnId, type, amount}`,
`SIT_OUT`, `SIT_IN`, `REBUY`, `CHAT {text}`, `REQUEST_SNAPSHOT`, `PING`.

**Server → client:** `ROOM_SNAPSHOT` (settings, state, host, seats, stacks, board, pots, your
cards, your legal actions if your turn), `PLAYER_JOINED`, `PLAYER_LEFT`, `SEAT_UPDATE`,
`HOST_CHANGED`, `GAME_STATE` (started/paused/resumed/closed), `HAND_STARTED`, `BLIND_POSTED`,
`HOLE_CARDS` (owner only), `ACTION_REQUIRED {seat, turnId, canCheck, callAmount, minRaiseTo,
maxRaiseTo, deadlineEpochMs}`, `PLAYER_ACTED`, `STREET_DEALT`, `POTS_UPDATED`, `SHOWDOWN`,
`HAND_ENDED {payouts, netBySeat}`, `CHAT`, `ERROR {code, message}`, `PONG`.

Abstract `Message` base, Jackson polymorphic on `type`. Max 8 KB. Chat ≤ 200 chars, 1/s.

`ErrorCode`: `NOT_YOUR_TURN, INVALID_ACTION, INVALID_AMOUNT, ROOM_NOT_FOUND, ROOM_FULL,
ROOM_CLOSED, SEAT_TAKEN, NOT_HOST, GAME_ALREADY_STARTED, NOT_ENOUGH_PLAYERS, REBUY_NOT_ALLOWED,
NOT_IN_ROOM, ALREADY_IN_ROOM, INVALID_CREDENTIALS, USERNAME_TAKEN, MALFORMED_MESSAGE,
UNAUTHORIZED, INTERNAL`.

`GAME_ALREADY_STARTED` answers a second `START_GAME`; it does not stop new players joining.
`UNAUTHORIZED` means a missing or expired token.

---

## 6. HTTP: Servlets + JSP

**JSON API (servlets) for the desktop app:** `GET /api/ping` (server name + version, used by
"Test connection") · `POST /api/auth/register` · `POST /api/auth/login` → `{token, user}` ·
`POST /api/auth/logout` · `POST /api/rooms` (create → `{code}`) · `GET /api/rooms/{code}`
(preview before joining) · `GET /api/hands?mine=true&page=` · `GET /api/hands/{id}` ·
`GET /api/leaderboard`.

**JSP pages** (served by the same server, open from any browser on the LAN; servlets are
controllers, JSPs in `WEB-INF/views/`, JSTL + EL only, `<c:out>` for user text):

| URL | Servlet | JSP |
|---|---|---|
| `/` | `HomeServlet` | `home.jsp` — server status, LAN address, open rooms count |
| `/leaderboard` | `LeaderboardServlet` | `leaderboard.jsp` — total net, hands played, hands won |
| `/rooms/{code}` | `RoomResultsServlet` | `room-results.jsp` — players and results of a room |
| `/hands/{id}` | `HandReplayServlet` | `hand-replay.jsp` — actions street by street |

Filters: `CharsetFilter`, `AuthFilter` (API routes except register/login/ping).
`AppContextListener` starts the DataSource, migrations, `RoomManager`, executors and discovery,
and stops them cleanly.

---

## 7. Database (PostgreSQL on the host machine)

Plain SQL migrations in `src/main/resources/db/` applied at startup by `MigrationRunner`.

```sql
users(id BIGSERIAL PK, username VARCHAR(24) UNIQUE NOT NULL, password_hash TEXT NOT NULL,
      created_at TIMESTAMPTZ DEFAULT now(), last_login_at TIMESTAMPTZ)
auth_tokens(token CHAR(64) PK, user_id BIGINT FK, expires_at TIMESTAMPTZ NOT NULL)
rooms(id BIGSERIAL PK, code CHAR(6) UNIQUE NOT NULL, name VARCHAR(40), host_user_id BIGINT FK,
      max_players SMALLINT, small_blind BIGINT, big_blind BIGINT, starting_stack BIGINT,
      turn_seconds SMALLINT, rebuy_allowed BOOLEAN, status VARCHAR(10),
      created_at TIMESTAMPTZ DEFAULT now(), closed_at TIMESTAMPTZ)
hands(id BIGSERIAL PK, room_id BIGINT FK, hand_no INT, button_seat SMALLINT, board VARCHAR(20),
      total_pot BIGINT, started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ)
hand_players(hand_id BIGINT FK, user_id BIGINT FK, seat SMALLINT, hole_cards CHAR(4),
      start_stack BIGINT, end_stack BIGINT, net BIGINT, showed_down BOOLEAN, won BOOLEAN,
      PRIMARY KEY(hand_id, user_id))
hand_actions(id BIGSERIAL PK, hand_id BIGINT FK, seq INT, user_id BIGINT FK, street VARCHAR(8),
      action VARCHAR(8), amount BIGINT)
```

- `hand_actions.action` is one of `POST_SB, POST_BB, FOLD, CHECK, CALL, BET, RAISE`. Blind posts
  are rows too, so a replay can show them. `amount` is the chips put in by that action.
- A finished hand is saved in ONE JDBC transaction (commit/rollback) by `HandRecordWriter`.
- Other players' folded hole cards are never returned by the API.
- Leaderboard: `SUM(net)`, `COUNT(*)`, `SUM(won::int)` grouped by user.

---

## 8. File handling

| File | Who | How |
|---|---|---|
| `server.properties` | server | `Properties`; DB URL/user/password, ports, timings |
| `data/hand-history/room-{code}/{yyyy-MM-dd}.txt` | `HandHistoryFileWriter` | readable text, appended per hand, `BufferedWriter` |
| `data/failed-hands/*.json` | `HandRecordWriter` | fallback when the DB write fails 3× |
| `~/.holdem/client.properties` | desktop app | last server IP, last username, sound on/off |
| `~/.holdem/session.dat` | desktop app | remember-me token via `ObjectOutputStream`, deleted on logout |
| user-chosen `.txt` | desktop app export | `FileChooser` + `BufferedWriter` |

try-with-resources everywhere; failures raise `StorageException`, logged, never crash a room.

---

## 9. Concept map

| Concept | Where |
|---|---|
| Packages | module/package layouts in §3, §4, §10 |
| Abstract class | `PlayerAction`, `GameEvent`, `Message`, `RoomCommand`, `SeatController`, `PokerException`, `BaseDao`, `BaseServlet` |
| Inheritance | all their subclasses; exception tree below |
| Polymorphism | `command.execute(room)`, `controller.onActionRequested(req)`, Jackson polymorphic messages, client dispatch per message type, `HandValue.compareTo` |
| User-defined exceptions | `PokerException` → `GameRuleException` (`InvalidActionException`, `NotYourTurnException`, `InvalidAmountException`), `RoomException` (`RoomNotFoundException`, `RoomFullException`, `RoomClosedException`, `SeatTakenException`, `NotHostException`, `GameAlreadyStartedException`, `NotEnoughPlayersException`, `RebuyNotAllowedException`, `NotInRoomException`, `AlreadyInRoomException`), `AuthException` (`InvalidCredentialsException`, `UsernameTakenException`, `UnauthorizedException`), `ProtocolException`; unchecked `PersistenceException`, `StorageException` — each maps to an `ErrorCode` |
| Collections | `ArrayList` deck + `Collections.shuffle`, `EnumMap` in evaluator, `ArrayDeque` action order, `List<Pot>`, `Set<Integer>` eligible seats, `ConcurrentHashMap` rooms/connections, `LinkedBlockingQueue`, `TreeMap` leaderboard, JavaFX `ObservableList` |
| File handling | §8 |
| JavaFX | §10 |
| JDBC | §7: HikariCP, DAOs, `PreparedStatement`, transactions, migrations |
| Servlets | §6 API + page controllers, filters, `ServletContextListener` |
| JSP | §6 pages with JSTL/EL |
| Multithreading | §4.2 room actors, scheduler, sender threads, async DB writer, UDP discovery thread; client network thread + `Platform.runLater`; bot-client with many concurrent bots |

---

## 10. Desktop app (`poker-client-fx`)

```
com.saksham.poker.client
├── app    HoldemApp, SceneRouter, AppConfig (client.properties)
├── net    ApiClient, GameSocket, ServerDiscovery (UDP broadcast), MessageDispatcher
├── state  RoomState, SeatViewModel (JavaFX properties)
├── view   ConnectView, LoginView, HomeView, CreateRoomDialog, JoinRoomDialog, WaitingRoomView,
│          TableView, ActionPanel, ChatPanel, HandLogPanel, HistoryView, LeaderboardView,
│          CardNode, ChipStackNode, SeatNode, TimerRing
└── util   Formatters, SoundPlayer, HandHistoryExporter
```

Screens:
1. **Connect:** host IP field + "Find server" (lists servers found by discovery) + "Test connection".
2. **Login / Register.**
3. **Home:** Create room · Join room (code field, uppercase, 6 chars) · My hand history · Leaderboard · Log out.
4. **Waiting room:** room name + big room code with "Copy" button, settings summary, seat list,
   choose seat, host sees Start / Kick / End.
5. **Table:** oval felt, up to 9 seats on an ellipse (you at bottom centre), names, stacks,
   dealer button, bets, pot(s), community cards, turn timer ring, winner highlight, showdown
   reveal, simple deal/chip animations. Host controls (pause/resume/end) in a menu.
6. **Action panel:** Fold · Check/Call X · Bet/Raise with slider + amount field + presets
   (min, ½ pot, pot, all-in); only legal options enabled; keyboard shortcuts F / C / R.
7. Side panel: chat + hand log. Sit out / sit in, rebuy, leave.

Cards and chips drawn with JavaFX shapes (no image assets). Network events → `Platform.runLater`;
HTTP calls off the UI thread (`Task`/`CompletableFuture`). On disconnect: "Reconnecting…"
overlay, retry every 2 s, rejoin room, apply `ROOM_SNAPSHOT`.

---

## 11. Security (basic, LAN-appropriate)

PBKDF2WithHmacSHA256 password hashing (salted); random 32-byte tokens with 7-day expiry;
server validates every action against `LegalActions` and `turnId`; one seat per user per room;
never send other players' cards or deck order; chat length-limited and escaped in JSP.

---

## 12. Testing

- **Engine:** evaluator truth tables + randomized cross-check vs a brute-force reference;
  scenario tests with stacked decks (heads-up order, 3-way all-in with side pots, incomplete
  all-in, uncalled bet, odd chip, fold to BB, short blind); 100,000-hand random simulation with
  chip conservation.
- **Server:** `RoomActor` tests with fake controllers (join, start, timeout, disconnect,
  reconnect, host leaves, rebuy); DAO tests against local Postgres (`@Tag("db")`); codec round trips.
- **Bots:** `poker-bot-client` joins a room by code with N bots playing random legal actions;
  asserts no foreign hole cards received. Target: 6 bots, 500 hands, no errors.
- **Manual:** `docs/QA.md` checklist for a 3-laptop LAN demo.

---

## 13. Future AI agents (seam only)

Agents will be `SeatController` subclasses (or external programs using `poker-bot-client`),
receive the same filtered events as humans, and a host will be able to "add bot to seat".
Keep `RoomSettings.allowBots = false` for now. Nothing else to build yet.
