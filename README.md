# Hold'em

No-Limit Texas Hold'em for players on the same Wi-Fi or LAN, written in Java.

One computer, the host machine, runs the server and the database. Everyone else runs the desktop
app, types the host machine's IP address, logs in, and creates a room or joins one with a
6-character code.

- What it must do: [docs/SPEC.md](docs/SPEC.md)
- Build order and progress: [docs/PLAN.md](docs/PLAN.md)

**Status:** Phase 4 of 8. The rules engine plays complete hands of Hold'em in memory (blinds,
betting, side pots, showdown) and is tested on 100,000 random hands. Every message the app and
server will exchange is defined and tested as JSON. The server has a database and an API: you can
register, log in, create a room and look one up by its code. Rooms cannot be joined or played in
yet, and the desktop app is still an empty window.

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

`server.properties` is optional; without it the server uses defaults that match the Docker
database. To change the database address or password, copy the example and edit it:

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

When it starts, the server prints the address players should use, for example
`Players can connect to: http://192.168.1.20:8080/poker`. The same lines go to
`data/logs/server.log`.

Check it from the host machine, or from another PC using the host machine's IP address:

```bash
curl http://127.0.0.1:8080/poker/api/ping
```

### Trying the API with curl

Register (the answer contains a `token`):

```bash
curl -X POST -H 'Content-Type: application/json' -d '{"username":"asha","password":"choose-a-password"}' http://127.0.0.1:8080/poker/api/auth/register
```

Create a room, putting your token in place of `TOKEN` (the answer contains the room `code`):

```bash
curl -X POST -H 'Content-Type: application/json' -H 'Authorization: Bearer TOKEN' -d '{"name":"Friday game","maxPlayers":6,"smallBlind":50,"bigBlind":100,"startingStack":10000,"turnSeconds":25,"rebuyAllowed":true}' http://127.0.0.1:8080/poker/api/rooms
```

Look the room up, putting its code in place of `ABC234`:

```bash
curl -H 'Authorization: Bearer TOKEN' http://127.0.0.1:8080/poker/api/rooms/ABC234
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
To run them too, with the Docker database running:

```bash
./mvnw verify -DexcludedTags=
```
