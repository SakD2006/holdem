# CLAUDE.md — LAN Hold'em (Java)

Read at the start of every session. Requirements: `docs/SPEC.md`. Build order: `docs/PLAN.md`.

## What we are building

A **No-Limit Texas Hold'em** desktop game for players on the **same local network**.
One computer (the "host machine") runs the server and the database. Everyone else runs the
JavaFX desktop app, enters the host machine's IP, logs in, and either **creates a room** (gets a
6-character room code) or **joins a room with a code**. The room creator starts the game.

Keep it basic. No cloud hosting, no deployment, no internet features, no installers.
Texas Hold'em ONLY. Computer players (bots) are being added in phases 9 to 12: SPEC §13.

## Architecture (do not change without asking)

```
poker-common      cards, actions, protocol messages, error codes, exceptions (no I/O)
poker-engine      pure Hold'em rules: hand state machine, betting, pots, evaluator (no I/O, no threads)
poker-ai          computer players: what a bot sees and how it chooses (no I/O, no threads)
poker-server      runs on the host machine via Tomcat 10.1: JSON API servlets, WebSocket game
                  endpoint, rooms, JDBC, hand-history files, a few JSP pages
poker-client-fx   JavaFX desktop app: connect, login/register, create/join room, play
poker-bot-client  headless test bots that join a room like a human (testing alone; future AI host)
```

Dependencies: `common ← engine ← ai ← server`, `common ← client-fx`, `common ← bot-client`.
The engine never depends on server, database, files, sockets or JavaFX.

## Golden rules

1. **Server is authoritative.** Clients send intents; the server validates everything.
2. **Never leak hole cards.** Each player's cards go only to that player until showdown.
   Filtering happens in one place: `server.room.EventRouter`.
3. **One thread owns each room.** Only the room's actor thread mutates room/hand state. Other
   threads submit a `RoomCommand` to the room's queue.
4. **Engine is pure and deterministic.** Inject `DeckFactory`; no clock, randomness, or logging
   in rule logic.
5. **Chips are `long`** and are conserved every hand.
6. **All SQL via `PreparedStatement`** inside DAOs.
7. **WebSocket sends go through `Connection.send`** (per-connection outbound queue).
8. **Bots know only what a person would.** Rooms talk only to `abstract class SeatController`;
   `AiController` is fed the same filtered messages as `RemoteHumanController` and acts by
   queueing the same commands. Never hand a bot the hand, the deck or another seat's cards.

## Required Java concepts (college syllabus — keep visible in code)

Packages · abstract classes · inheritance · polymorphism · user-defined exceptions ·
file handling · collections · JavaFX · JDBC · Servlets · JSP · multithreading.
`docs/SPEC.md §9` maps each to classes. No Spring, Hibernate, JS frameworks or game engines.

## Tech stack

Java 21 · Maven multi-module · JUnit 5 + AssertJ · Jackson · PostgreSQL 16 (local install or
Docker, on the host machine) · HikariCP · Tomcat 10.1 via Cargo plugin (Servlet 6, JSP 3.1,
WebSocket 2.1, JSTL 3) · JavaFX 21 · JDK `java.net.http` (HTTP + WebSocket client) ·
SLF4J + Logback.

## Commands (host machine)

```bash
./mvnw -q clean verify                                          # build + tests
./mvnw -pl poker-engine -am test                                # engine tests only
./mvnw -pl poker-server -am -DskipTests package cargo:run       # server on http://0.0.0.0:8080/poker (Ctrl+C stops it)
./mvnw -pl poker-client-fx -am compile javafx:run               # desktop app
./mvnw -pl poker-client-fx -am -DskipTests package              # builds poker-client-fx/target/holdem-client
./mvnw -pl poker-bot-client -am compile exec:java -Dexec.args="--server 127.0.0.1 --room ABC234 --bots 3"
./mvnw -pl poker-bot-client -am compile exec:java -Dexec.args="--server 127.0.0.1 --bots 6 --hands 500 --think 0"   # soak: bots make their own room
java -cp poker-common/target/classes:poker-engine/target/classes:poker-engine/target/test-classes \
     com.saksham.poker.engine.hand.EngineConsoleDemo            # print one random hand (after test-compile)
./mvnw verify -DexcludedTags=                                   # also run tests tagged db and perf
docker compose up -d                                            # Postgres on localhost:5433 (db/user/password: holdem)
```

Always use the wrapper (`./mvnw`, or `mvnw.cmd` on Windows), not a system `mvn`.

- The server needs PostgreSQL: `docker compose up -d` first. Tests tagged `db` need it too and are
  skipped by default; each test class works in its own temporary schema, never the real tables.
- `server.properties` is optional (defaults match `docker-compose.yml`). The server log is
  `data/logs/server.log`.
- For a bot soak run, start the server with no waits between hands by adding
  `-Dholdem.config=$PWD/poker-bot-client/soak-server.properties` (an absolute path) to the
  `cargo:run` command. With `--room` the bots join a room a person hosts; without it they create
  one, start it, and close it when done. Exit code 0 means no problems.
- Room tests drive a `Room` directly through `RoomHarness` (no threads, timers fire on demand);
  `RoomManagerTest` runs rooms on their real threads.
- Finished hands go from a room to `HandRecordWriter` (thread `hand-writer`), which appends the text
  history under `data/hand-history/` and saves to the database. After a soak run, check the
  database agrees: `SELECT count(*) FROM hands` for the room should equal the hands the bots report.
- The desktop app cannot be clicked through from a terminal. To see its screens, run `ViewGallery`
  (client test sources), which renders each one to a PNG with sample data. To check its network
  code against a real server, run `ClientSmoke` (same place) and join its room with the bots.
  Both need the client's test classpath (`dependency:build-classpath` with `-Dmdep.includeScope=test`).
- Movement at the table is timed in `Motion` (client `view`). Views must still be right with
  `Motion.enabled = false`, which `ViewGallery` uses for its still pictures; it then films one hand
  with movement on (`film-*.png`) to catch cards mid-flight and mid-flip. Glow effects on the name
  plates are set in `holdem.css`, because a stylesheet's `-fx-effect` overrides `setEffect` in code.
- **Before starting a server for a test, check whether one is already running** (`lsof -nP -iTCP:8080`).
  The user may be running their own. Start test servers on other ports
  (`-Dholdem.port=18080 -Dholdem.shutdown.port=18205 -Dholdem.ajp.port=18009`), stop them by their own
  process id, and do not run `clean` while a server is up: it deletes the folder Tomcat runs from.
- The web pages are at `http://<host>:8080/poker/` (JSPs in `WEB-INF/views`, one `PageServlet`
  subclass each, data shaped in `server.web.view`). They need no login, so they must only ever show
  a hand through `StoredHand.viewFor(-1)`.
- "Find server" uses UDP 8888. Two servers on one computer cannot share it: give a test server its
  own with `discovery.port=18888` in the file passed as `-Dholdem.config`, and point the app at it
  with `-Dholdem.discovery.port=18888`. A server that cannot open the port logs a warning and runs on.
- Sounds are made in code by `SoundPlayer` (client `util`) and are off until `HoldemApp` turns them
  on, so tests and `ViewGallery` are silent. `RoomState` announces each `Cue`; `TableView` plays it.
- README pictures are in `docs/images`: the `app-*` ones are copied from a `ViewGallery` run.
- Bots: `poker-ai` holds the strategies (pure, tested alone); `server.player.AiController` runs one
  in a seat. `AiTableTest` plays hundreds of hands with real bots on real threads and is the test to
  run after touching either. `poker-bot-client` is something else: network test players for soak runs.
- A test server shares the real database unless told otherwise, and starting one marks the rooms
  of a server that is already running as closed. When the user's server is up, give the test
  server its own database: `CREATE DATABASE holdem_aitest OWNER holdem` in the Docker Postgres and
  `db.url=jdbc:postgresql://localhost:5433/holdem_aitest` in its config file.
- `ReconnectSmoke` (client test sources) rehearses a dropped connection with the real `RoomSession`.
- Stopping the server prints two harmless "Could not contact [localhost:8205]" lines from Cargo;
  the server has already shut down cleanly by then (the log ends with "Hold'em server stopped").

- The run goals (`cargo:run`, `javafx:run`, `exec:java`) need a phase before them (`compile` or
  `package`) so `-am` builds the modules they depend on. They are skipped in every module except
  the one that owns them (skip properties in the parent POM).
- `target/holdem-client` is the folder to copy to other PCs: `run.bat` on Windows, `sh run.sh` on
  macOS/Linux. It holds JavaFX for each OS; the other PC needs only Java 21 or newer.
Fix and update this section whenever a command changes.

## Conventions

- Base package `com.saksham.poker.<module>.<area>`.
- Immutable value objects; `record` for plain data; `abstract class` hierarchies where the spec
  says (PlayerAction, GameEvent, Message, RoomCommand, SeatController, PokerException, BaseDao,
  BaseServlet, PageServlet, BotStrategy).
- Checked `PokerException` tree for game/room errors; unchecked `PersistenceException`,
  `StorageException`.
- Every public engine method tested; bug fix = failing test first.
- SLF4J logging; try-with-resources for every stream and JDBC object.

## Session workflow

1. Find the first unchecked phase in `docs/PLAN.md`; read the referenced SPEC sections.
2. Plan mode first: files to create/change and how they'll be tested.
3. Implement in small steps; `./mvnw -q clean verify` must pass.
4. Meet every "Done when" check, tick the boxes, commit `phase N: <summary>`, stop and report.

## Do not

- Add hosting/deployment, internet play, installers, payments, tournaments, other variants.
- Put game rules outside `poker-engine`.
- Trust client-sent amounts, seats, turn order or hand ids.
- Block the JavaFX Application Thread or a room actor thread on I/O.
- Let a bot see or do anything a person in its seat could not.
- Do a bot's thinking on a room's thread: `AiController` schedules it on the bot-thinker threads.
