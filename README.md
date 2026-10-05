# Hold'em

No-Limit Texas Hold'em for players on the same Wi-Fi or LAN, written in Java.

One computer, the host machine, runs the server and the database. Everyone else runs the desktop
app, types the host machine's IP address, logs in, and creates a room or joins one with a
6-character code.

- What it must do: [docs/SPEC.md](docs/SPEC.md)
- Build order and progress: [docs/PLAN.md](docs/PLAN.md)

**Status:** Phase 7 of 8. The app is feature-complete; its hands-on checklist is `docs/QA.md`. The rules engine plays complete hands of Hold'em in memory (blinds,
betting, side pots, showdown) and is tested on 100,000 random hands. Every message the app and
server will exchange is defined and tested as JSON. The server has a database, an API and live
rooms over WebSocket: players join by code, take seats, and play real hands with turn timers,
sitting out, rebuys and reconnecting. Every hand is saved to the database and to readable text
files, and the API serves hand history, replays and a leaderboard. The desktop app does the
whole game: connect, register or log in, create or join a room, play hands at the table against
people or bots, chat, sit out, rebuy, reconnect by itself after a dropped connection, and look
back over your hand history and the leaderboard.

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

Type the server's address (`127.0.0.1` on the host machine itself), create an account, then create
a room. To fill the room, run the bots with its code:

```bash
./mvnw -pl poker-bot-client -am compile exec:java -Dexec.args="--server 127.0.0.1 --room ABC234 --bots 3 --hands 0"
```

The app keeps its settings and a remembered login in `~/.holdem`.

To run a second server for testing while one is already running, give it its own ports:

```bash
./mvnw -pl poker-server -am -DskipTests package cargo:run -Dholdem.port=18080 -Dholdem.shutdown.port=18205 -Dholdem.ajp.port=18009
```

Then use `127.0.0.1:18080` as the server address.

## Watching bots play

With the server running, this makes six bots create a room and play 100 hands against each other,
over the same connection a person's app will use:

```bash
./mvnw -pl poker-bot-client -am compile exec:java -Dexec.args="--server 127.0.0.1 --bots 6"
```

It ends with a line such as `Finished: 6 bots played 104 hands in room ABC234 with no problems`.
At normal speed a hand takes a few seconds. For a fast run of hundreds of hands, start the server
with no waits between hands instead:

```bash
./mvnw -pl poker-server -am -DskipTests package cargo:run -Dholdem.config=$PWD/poker-bot-client/soak-server.properties
```

```bash
./mvnw -pl poker-bot-client -am compile exec:java -Dexec.args="--server 127.0.0.1 --bots 6 --hands 500 --think 0"
```

Add `--room ABC234` to make the bots join a room you are hosting instead of creating their own.
Bots pause for a second or two before each action, a little longer before a bet or raise, so you
can follow what they do; `--think 300-900` changes the range (in milliseconds) and `--think 0`
makes them act at once.
The bots use accounts named `bot_1` to `bot_9`, which they register the first time.

## Where hands are kept

Every finished hand is saved in three places, all under `data/` on the host machine:

| Where | What |
|---|---|
| The database | Each hand with its players and actions, for history, replays and the leaderboard |
| `data/hand-history/room-<code>/<date>.txt` | A text file anyone can read, one per room per day. It shows only what was public at the table |
| `data/failed-hands/` | Normally empty. If the database is down, hands are written here as JSON instead of being lost |

The server log is `data/logs/server.log`.

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
