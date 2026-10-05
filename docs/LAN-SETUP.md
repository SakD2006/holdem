# Setting up a game on your network

This takes a group from nothing to playing: one computer runs the game (the **host machine**) and
everyone, the host included, plays in the desktop app. All the computers must be on the same Wi-Fi
or wired network. Nothing here needs the internet once the downloads are done.

Allow about fifteen minutes the first time.

## What each computer needs

| Computer | Needs |
|---|---|
| Host machine | Java 21 or newer, and PostgreSQL 16 (easiest through Docker Desktop). This project's folder. |
| Every other player | Java 21 or newer. Nothing else: they are given a folder to run. |

Java is free from <https://adoptium.net> (choose "Temurin 21"). To check it is installed, open a
terminal (on Windows: Command Prompt) and run:

```bash
java -version
```

It should print a version starting with 21 or higher.

## Part 1: the host machine

Run every command from this project's folder. On Windows, type `mvnw.cmd` wherever these steps say
`./mvnw`.

### 1. Start the database

With Docker Desktop installed and running:

```bash
docker compose up -d
```

That is all: the server's default settings match this database.

**Without Docker.** Install PostgreSQL 16, then create a database and a user for the game. In
`psql`, as the PostgreSQL administrator:

```sql
CREATE USER holdem WITH PASSWORD 'holdem';
CREATE DATABASE holdem OWNER holdem;
```

Then tell the server where the database is. Copy `server.properties.example` to
`server.properties` and set the address; an installed PostgreSQL normally listens on port 5432,
not the 5433 that Docker uses here:

```properties
db.url=jdbc:postgresql://localhost:5432/holdem
db.user=holdem
db.password=holdem
```

The server creates its own tables the first time it starts.

### 2. Start the server

```bash
./mvnw -pl poker-server -am -DskipTests package cargo:run
```

The first run downloads what it needs and takes a few minutes. It is ready when it prints a line
like this:

```
Players can connect to: http://192.168.1.20:8080/poker
```

The numbers before `:8080` are the host machine's address on your network. Note them down. Leave
this window open: closing it, or pressing Ctrl+C in it, stops the game for everyone.

### 3. Let other computers through the firewall

The host machine must accept two kinds of incoming connection:

| Port | Kind | What it is for |
|---|---|---|
| 8080 | TCP | The game itself, and the web pages |
| 8888 | UDP | The **Find server** button. Without it players can still type the address |

**Windows.** The first time the server starts, Windows may ask whether to allow Java on your
network: tick **Private networks** and press **Allow access**. If it did not ask, or players cannot
connect, open Command Prompt **as administrator** (right-click it, "Run as administrator") and run
these two commands:

```bat
netsh advfirewall firewall add rule name="Holdem game" dir=in action=allow protocol=TCP localport=8080
```

```bat
netsh advfirewall firewall add rule name="Holdem find server" dir=in action=allow protocol=UDP localport=8888
```

Also check that Windows treats your Wi-Fi as a **Private** network (Settings, Network & internet,
your Wi-Fi's properties). On a "Public" network Windows blocks other computers whatever the rules say.

**macOS.** If the firewall is on, the Mac asks "Do you want the application java to accept incoming
network connections?" when the server starts. Press **Allow**. If you pressed Deny by mistake,
change it under System Settings, Network, Firewall, Options.

**Linux** with `ufw`:

```bash
sudo ufw allow 8080/tcp
```

```bash
sudo ufw allow 8888/udp
```

### 4. Check it from the host machine

Open a browser on the host machine at <http://127.0.0.1:8080/poker/>. You should see a page saying
the server is running, with the address players use.

### 5. Build the app to hand out

In a second terminal window, in the project's folder:

```bash
./mvnw -pl poker-client-fx -am -DskipTests package
```

This makes the folder `poker-client-fx/target/holdem-client`. It works on Windows, macOS and Linux.
Copy the **whole folder** to each player: a USB stick, a shared drive or a zip in a chat all work.

The host plays with the same app. On the host machine, either run that folder too, or:

```bash
./mvnw -pl poker-client-fx -am compile javafx:run
```

## Part 2: every player

1. Make sure Java 21 or newer is installed (see the top of this page).
2. Open the `holdem-client` folder you were given.
   - Windows: double-click `run.bat`.
   - macOS or Linux: open a terminal in the folder and run `sh run.sh`.
3. On the first screen press **Find server**. The host's address appears in the box. Press
   **Continue**.
   - If nothing is found, type the address the host noted in step 2 of Part 1, such as
     `192.168.1.20`, and press **Continue**.
4. Press **Create account**, choose a username (3 to 24 letters, digits or underscores) and a
   password (at least 6 characters). These exist only on the host machine.
5. One player presses **Create room** and reads out the 6-character code. Everyone else presses
   **Join room** and types it.
6. Everyone takes a seat. The player who made the room presses **Start game**.

## The web pages

Any phone, tablet or computer on the same network can open the host's address in a browser, with
no login and no app:

```
http://192.168.1.20:8080/poker/
```

(Use your host machine's own numbers.) There is the leaderboard, each room's results, and a
replay of every hand, street by street. A player's cards appear in a replay only if they were
shown at the table.

## Stopping, and what is kept

Press Ctrl+C in the server's window. Rooms in progress end; accounts, the leaderboard and every
finished hand are kept in the database and are there next time. The Docker database keeps running
until you stop it:

```bash
docker compose stop
```

Hand histories as text files are under `data/hand-history/`, and the server's log is
`data/logs/server.log`.

## When something does not work

| What you see | What to do |
|---|---|
| The server stops at start-up with "Could not connect to PostgreSQL" | The database is not running. Run `docker compose up -d` and wait ten seconds, or check `db.url` in `server.properties`. |
| The server will not start and says port 8080 is in use | Another program, or an earlier copy of the server, has the port. Close it. To use another port, add `-Dholdem.port=9090` to the start command; players then type the address with `:9090` on the end. |
| The server log says `"Find server" will not work` | Another program on the host machine is using UDP port 8888. The game works; players type the address. |
| **Find server** says no server answered | The two computers are not on the same network, or the host's firewall is blocking UDP 8888 (step 3). Type the address instead. Networks at colleges, hotels and cafes, and "guest" Wi-Fi, often stop computers seeing each other at all: use a home router or a phone's hotspot. |
| "Could not reach a server" after typing the address | Check the address against the server's start-up line. Check the host's firewall allows TCP 8080 (step 3). From the player's computer, open `http://<address>:8080/poker/` in a browser: if that page does not load either, the network or firewall is in the way, not the app. |
| The server printed no "Players can connect to" line | The host machine is not on a network. Connect it to the Wi-Fi and start the server again. |
| The server printed two addresses | The host machine is on two networks (Wi-Fi and a cable, say). Players use the one that starts like their own computer's address. **Find server** picks the right one by itself. |
| `run.bat` says Java was not found | Install Java 21 from <https://adoptium.net>, then close and reopen the folder. |
| The app says the login has run out | Logins last seven days. Log in again. |
| A player's Wi-Fi drops mid-hand | The app reconnects by itself and puts them back in their seat. Away for more than a minute, they come back sitting out, with their seat and chips kept. |
| The host machine's address changed overnight | Routers hand out addresses afresh. Read the new one from the server's start-up line, or press **Find server**. |
| No sound | Sounds are on by default; check **Settings** on the home screen, and the computer's volume. |
