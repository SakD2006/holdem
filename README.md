# Hold'em

No-Limit Texas Hold'em for players on the same Wi-Fi or LAN, written in Java.

One computer, the host machine, runs the server and the database. Everyone else runs the desktop
app, types the host machine's IP address, logs in, and creates a room or joins one with a
6-character code.

- What it must do: [docs/SPEC.md](docs/SPEC.md)
- Build order and progress: [docs/PLAN.md](docs/PLAN.md)

**Status:** Phase 3 of 8. The rules engine plays complete hands of Hold'em in memory (blinds,
betting, side pots, showdown) and is tested on 100,000 random hands. Every message the app and
server will exchange is defined and tested as JSON. The server starts and answers a ping, and the
desktop app opens an empty window. Nothing is playable over the network yet.

## Modules

| Module | What it is |
|---|---|
| `poker-common` | Cards, actions, protocol messages, error codes, exceptions |
| `poker-engine` | Pure Hold'em rules: betting, pots, hand evaluator. No I/O, no threads |
| `poker-server` | Runs on the host machine in Tomcat 10.1: JSON API, WebSocket game, rooms, JDBC, JSP pages |
| `poker-client-fx` | JavaFX desktop app |
| `poker-bot-client` | Headless test bots that join a room like a human |

## Setting up the host machine

You need:

- JDK 21 or newer
- Docker (for PostgreSQL), or a local PostgreSQL 16

Maven is not needed; the project brings its own (`./mvnw`, or `mvnw.cmd` on Windows).

```bash
docker compose up -d
```

```bash
cp server.properties.example server.properties
```

```bash
./mvnw -q clean verify
```

## Running

Start the server. It listens on port 8080 on every network interface; stop it with Ctrl+C.

```bash
./mvnw -pl poker-server -am -DskipTests package cargo:run
```

Check it from the host machine, or from another PC using the host machine's IP address:

```bash
curl http://127.0.0.1:8080/poker/api/ping
```

Start the desktop app:

```bash
./mvnw -pl poker-client-fx -am compile javafx:run
```

## Giving the app to other players

```bash
./mvnw -pl poker-client-fx -am -DskipTests package
```

This builds `poker-client-fx/target/holdem-client`. Copy that whole folder to the other PC, which
needs only Java 21 or newer:

- Windows: double-click `run.bat`
- macOS or Linux: `sh run.sh`

## Watching the engine play a hand

Prints one hand between six random players:

```bash
./mvnw -q -pl poker-engine -am test-compile
```

```bash
java -cp poker-common/target/classes:poker-engine/target/classes:poker-engine/target/test-classes com.saksham.poker.engine.hand.EngineConsoleDemo
```

## Tests

```bash
./mvnw -q clean verify
```

Tests that need the database or measure speed are tagged `db` and `perf` and skipped by default.
To run them too:

```bash
./mvnw verify -DexcludedTags=
```
