# PLAN — Build order for Claude Code (LAN version)

One phase per Claude Code session. Each phase: goal, tasks, concepts, "Done when", what you'll
have at the end, prompt.
§ refers to `docs/SPEC.md`.

**How to use**
1. Open this folder (already created, with `CLAUDE.md` in the root and `docs/` beside it).
2. Run `claude` in that folder, switch to plan mode (Shift+Tab), paste the phase prompt.
3. Review the plan, let it build, run the "Done when" checks yourself, commit.

```
0 Scaffold → 1 Cards & evaluator → 2 Hand engine → 3 Protocol → 4 Server: DB, accounts, rooms API
→ 5 Live rooms over WebSocket → 6 Hand records → 7 Desktop app (FIRST PLAYABLE) → 8 JSP pages + LAN demo
```

---

## Phase 0 — Scaffold

**Concepts:** packages.

- [x] Parent POM (Java 21, dependency management, Surefire tags `db`, `perf`), Maven wrapper.
- [x] Modules: `poker-common`, `poker-engine`, `poker-server` (war), `poker-client-fx`, `poker-bot-client`.
- [x] `.gitignore`, `docker-compose.yml` for Postgres (port 5433).
- [x] Add AssertJ to the parent POM.
- [x] Cargo plugin runs Tomcat 10.1 bound to `0.0.0.0:8080`; `/poker/api/ping` returns `{"status":"ok"}`.
- [x] `server.properties.example` (DB URL/user/password, ports).
- [x] JavaFX window "Hold'em"; bot-client main prints its args.
- [x] Client `package` builds a folder that runs on Windows, macOS and Linux (JavaFX libraries for
      each) with a start script; needs only Java 21 on the other PC.
- [x] Run every command in CLAUDE.md. Goals run with `-am` (`cargo:run`, `javafx:run`,
      `exec:java`) may try to run in every module; fix the build or the commands so each works.
- [x] `README.md` (setup on the host machine), Logback configs.

**Done when:** `./mvnw -q clean verify` passes, server answers `/poker/api/ping`, the window opens.

**You'll have:** an empty project where the server starts, a blank "Hold'em" window opens, and every command works. No poker yet.

> **Prompt:** Read CLAUDE.md and docs/PLAN.md Phase 0, then scaffold the project so every command
> in CLAUDE.md works. Plan first.

---

## Phase 1 — Cards & hand evaluator (§2.1, §2.8, §3)

**Concepts:** packages, enums, collections, `Comparable`, abstract class + inheritance (`PlayerAction`), user-defined exceptions (tree).

- [x] `poker-common`: `Suit`, `Rank`, `Card` (parse/format `"Ah"`), `ActionType`, abstract
      `PlayerAction` + 6 subclasses, `ErrorCode`, full `PokerException` tree (§9).
- [x] `poker-engine`: `Deck`, `DeckFactory`, `SecureDeckFactory`, `StackedDeckFactory`.
- [x] `HandCategory`, `HandValue` (Comparable), `HandEvaluator`.
- [x] Tests: every category, kickers, wheel, board-plays split, 20,000 random cross-checks vs a
      second, independently written reference evaluator in test code.

**Done when:** all evaluator tests green.

**You'll have:** cards, a deck, and code that correctly says which of two poker hands wins. Tests only, nothing to click.

> **Prompt:** Implement Phase 1 (SPEC §2.1, §2.8, §3). Tests first, including a brute-force
> reference evaluator in test code. Plan first.

---

## Phase 2 — Hold'em hand engine (§2, §3)

**Concepts:** abstract class (`GameEvent`), inheritance, polymorphism, collections, exceptions.

- [x] `HandConfig`, `SeatState`, `Street`, `LegalActions`, `ActionValidator`.
- [x] `HoldemHand`: blinds (heads-up, short stack), burns, action order, min-raise, incomplete
      all-in rule, street completion, win on folds, auto run-out, uncalled bet, `forceFold`;
      `apply` throws `GameRuleException`.
- [x] `PotCalculator`, showdown order, odd chip, `GameEvent` subclasses, `HandResult`.
- [x] Chip-conservation assertion.
- [x] Scenario tests (stacked decks): heads-up order; 3-way all-in with 2 side pots; incomplete
      all-in doesn't reopen; uncalled bet; split with odd chip; fold to BB; check to showdown;
      short stack on blind.
- [x] 100,000-hand random simulation (2–9 seats): no exceptions, chips conserved.
- [x] `EngineConsoleDemo` (test sources) prints one simulated hand.

**Done when:** scenarios and simulation pass.

**You'll have:** a complete, correct game of Hold'em running in memory; a demo prints one full hand in the terminal.

> **Prompt:** Implement Phase 2 (SPEC §2, §3). Engine stays pure: no I/O, threads or clock.
> Write the PLAN Phase 2 scenario tests before implementing. Plan first.

---

## Phase 3 — Protocol (§5)

**Concepts:** abstract class + inheritance (`Message`), polymorphism (Jackson typing).

- [x] Abstract `Message`; one class per message in §5; `ErrorCode` payloads.
- [x] `MessageCodec` with polymorphic typing and 8 KB limit; `ProtocolException`.
- [x] Round-trip tests for every message; unknown type rejected.

**Done when:** codec tests green; `poker-common` depends on nothing else in the project.

**You'll have:** the full list of messages the app and server will send each other, as tested JSON. Still nothing to click.

> **Prompt:** Implement Phase 3 (SPEC §5) in poker-common. Plan first.

---

## Phase 4 — Server: database, accounts, rooms API (§4.1, §6, §7, §8, §11)

**Concepts:** JDBC, Servlets, file handling (`server.properties`), abstract classes (`BaseDao`, `BaseServlet`), exceptions.

- [x] `ServerConfig`, `AppContextListener`, `NetworkInfo` (logs the LAN URL on startup).
- [x] `DataSourceProvider` (HikariCP), `MigrationRunner`, `V1__init.sql` (§7).
- [x] `BaseDao`, `UserDao`, `AuthTokenDao`, `RoomDao`.
- [x] `PasswordHasher` (PBKDF2), `SessionService`.
- [x] `BaseServlet` (JSON in/out, maps `PokerException` → `ErrorCode` + HTTP status).
- [x] API: ping, register, login, logout, create room (returns code), room preview.
- [x] `RoomCodeGenerator` (§4.1 alphabet, unique among open rooms); `AuthFilter`, `CharsetFilter`.
- [x] Room codes are never reused (`rooms.code` is `UNIQUE`).
- [x] Tests: DAOs (`@Tag("db")`), hasher, register/login errors (`USERNAME_TAKEN`,
      `INVALID_CREDENTIALS`), code generator uniqueness.

**Done when:** with Postgres running, `curl` can register, log in, create a room and preview it
by code, both from the host machine and from another PC on the LAN.

*Checked with `curl` on the host machine, through both `127.0.0.1` and its LAN address. Not yet
tried from a second PC.*

**You'll have:** a real server with a database: you can register, log in and create a room with a code, using `curl` from any PC on the Wi-Fi.

> **Prompt:** Implement Phase 4 (SPEC §4.1, §6 API, §7, §8 server.properties, §11). Plain JDBC with
> PreparedStatement and explicit transactions. Plan first.

---

## Phase 5 — Live rooms over WebSocket (§4, §5)

**Concepts:** multithreading (actors, scheduler, outbound queues), concurrent collections, abstract class + polymorphism (`RoomCommand`, `SeatController`), exceptions.

- [x] `RoomManager` (`ConcurrentHashMap<code, RoomActor>`), `RoomActor` loop over
      `LinkedBlockingQueue<RoomCommand>`, all commands in §4.3.
- [x] Waiting room: join by code, choose seat, host start/kick/end, host hand-over.
- [x] One room per user (`ALREADY_IN_ROOM`); joining a playing room is allowed, dealt in at the
      big blind; a busted player stays sat out and watching, and may rebuy if the room allows.
- [x] Game loop: start hands with ≥ 2 active players, 3 s between hands, run-out pauses,
      pause/resume, sit out/in, rebuy, leave mid-hand = fold, room close rules.
- [x] `TurnTimer` with `turnId` (timeout = check if legal, else fold); reconnect with 60 s grace
      and `ROOM_SNAPSHOT`.
- [x] `SeatController` + `RemoteHumanController`; `EventRouter` with hole-card filtering.
- [x] `GameEndpoint`, `AuthHandshakeConfigurator`, `Connection` (sender virtual thread),
      `ConnectionRegistry`; chat with rate limit.
- [x] Tests: `RoomActor` with fake controllers (start, timeout check and timeout fold,
      disconnect/reconnect, host leaves, kick, rebuy, bust without rebuy, join mid-game, leave
      mid-hand); 20-thread concurrent command test.
- [x] `poker-bot-client` v1: register/login bots, join a room by code, take seats, host-bot starts,
      random legal actions; asserts no foreign hole cards.

**Done when:** 6 bots play 500 hands in one room with no errors and correct chip totals.

**You'll have:** live multiplayer poker on the server; you watch 6 bots play 500 hands against each other in the terminal.

> **Prompt:** Implement Phase 5 (SPEC §4, §5). Follow golden rules 1–3, 7 and 8 strictly.
> Build poker-bot-client v1 to prove it. Plan first.

---

## Phase 6 — Hand records: DB + files (§7, §8)

**Concepts:** JDBC transactions, producer–consumer thread, file handling.

- [x] `HandRecord` (blind posts included as actions); `HandRecordWriter` thread (one transaction per hand, 3 retries, then
      `data/failed-hands/*.json`).
- [x] `HandHistoryFileWriter` (readable text per room per day).
- [x] API: my hands (paged), one hand (hides others' folded cards), leaderboard.
- [x] Tests: DB-down fallback to file; card-visibility rules.

**Done when:** after a 500-hand bot run, DB counts match, files exist, and `SUM(net)` per hand = 0.

**You'll have:** every hand saved to the database and to readable text files, plus hand history and leaderboard data from the API.

> **Prompt:** Implement Phase 6 (SPEC §7, §8). Plan first.

---

## Phase 7 — JavaFX desktop app (§10) — FIRST PLAYABLE

**Concepts:** JavaFX, multithreading (`Platform.runLater`, `Task`), file handling (client.properties, session.dat, export), collections (`ObservableList`), polymorphism (message dispatch).

Built in three steps, with a check between them.

*Step 7a: getting in and the waiting room (done)*
- [x] Connect screen (IP + Test connection); login/register; remember me.
- [x] Home: create room dialog (all settings), join room dialog (code).
- [x] Waiting room with big room code + copy button, seat picker, host controls.
- [x] `ApiClient`, `GameSocket`, `RoomState` with unit tests for its updates.

*Step 7b: playing (done)*
- [x] Table screen and action panel per §10; timer ring; animations; showdown + winners.
- [x] "Sit in" and "Rebuy" in the action panel, so a player who timed out or went broke can get back in.

*Step 7c: everything round the table (done)*
- [x] Chat, sit out, host pause/resume/end.
- [x] Reconnecting overlay + snapshot restore.
- [x] Home: history, leaderboard; export hand history to file.
- [x] `docs/QA.md` manual checklist.

**Done when:** 3 instances (on one PC or 3 laptops) create/join one room by code and play 30
hands including an all-in with side pot, chat, sit out, and a reconnect after closing the app.

*Not done yet: this is section 11 of `docs/QA.md`, and it needs people at keyboards.*

**You'll have:** the actual game: open the app, log in, create or join a room with a code and play poker with friends on the same Wi-Fi.

> **Prompt:** Implement Phase 7 (SPEC §10). Never block the JavaFX Application Thread; network
> events go through Platform.runLater. Draw cards/chips with shapes. Split into sub-steps and
> check with me between them. Plan first.

---

## Phase 8 — JSP pages, LAN discovery, demo polish (§4.6, §6)

**Concepts:** JSP, Servlets, multithreading (UDP discovery thread), file handling.

- [ ] JSP pages from §6 (home, leaderboard, room results, hand replay), shared header/footer,
      plain CSS.
- [ ] `DiscoveryResponder` (server) + "Find server" in the desktop app.
- [ ] `docs/LAN-SETUP.md`: install Postgres, configure `server.properties`, run server, firewall
      rules (8080 TCP, 8888 UDP), copy the client to friends' PCs (they need Java 21),
      troubleshooting.
- [ ] Sounds (your turn, chips, deal), small UI polish, friendly message for every `ErrorCode`.
- [ ] README with screenshots and the SPEC §9 concept table.

**Done when:** following `LAN-SETUP.md` on a fresh laptop works; "Find server" finds the host;
the JSP leaderboard and replays show the demo's hands from any browser on the LAN.

**You'll have:** the demo-ready project: "Find server" button, sounds, browser pages for leaderboard and hand replays, and a setup guide for a new laptop.

> **Prompt:** Implement Phase 8 (SPEC §4.6, §6 JSP pages). JSPs in WEB-INF/views, JSTL/EL only,
> c:out for user text. Plan first.

---

## Later — AI agents (separate plan)

`AiController extends SeatController`, "add bot to seat" for the host, rule-based → Monte Carlo
→ smarter agents. Phases 0–8 shouldn't need changes for it.
