# QA checklist: desktop app

A walk through the app by hand. Run it before a demo, and after any change to the app. It needs the
host machine plus, ideally, two more laptops on the same Wi-Fi; where you are short of people, the
bots can fill seats.

Tick a box only if you saw it happen. Seats are numbered from 1 on screen.

## Before you start

- [ ] On the host machine: `docker compose up -d`, then start the server. It prints
      `Players can connect to: http://<address>:8080/poker`. Note the address.
- [ ] Each laptop has the app (the `holdem-client` folder) and Java 21 or newer.

## 1. Connecting

- [ ] Start the app. The connect screen appears.
- [ ] Type a wrong address and press **Test connection**: a message says the server could not be
      reached and what to check. The app stays usable.
- [ ] Type the host's address and press **Test connection**: it says a Hold'em server was found.
- [ ] Paste the whole address from the server's log (`http://...:8080/poker`): it is accepted.
- [ ] Press **Continue**: the login screen appears, showing the server's address.

## 2. Accounts

- [ ] **Create account** with a 2-letter username: refused, with the rule stated.
- [ ] **Create account** with a 5-character password: refused, with the rule stated.
- [ ] **Create account** with a good username and password: you arrive at Home, signed in.
- [ ] Log out. **Create account** with the same username: refused as already taken.
- [ ] **Log in** with the wrong password: refused, without saying whether the username exists.
- [ ] **Log in** correctly with "Remember me" ticked. Close the app and reopen it: you arrive at
      Home without typing anything.
- [ ] Log out, close and reopen: the connect or login screen appears, not Home.

## 3. Creating and joining a room

- [ ] **Create room**: change every setting. Try a big blind smaller than the small blind: the
      dialog stays open and says why.
- [ ] Create the room: the waiting room shows its name, settings and a 6-character code.
- [ ] **Copy** puts the code on the clipboard (paste it somewhere to check).
- [ ] On a second laptop, **Join room**: typing lower-case letters gives upper case. A wrong code
      says there is no such room. The right code shows the room's name, host and seats taken.
- [ ] Join: both laptops now list both players.
- [ ] Each player picks a seat. Picking a taken seat is refused with a message.
- [ ] The second player sees no Start button, only "Waiting for <host>".
- [ ] The host sees **Start game** greyed out with one player seated, and usable with two.
- [ ] The host presses **Remove** on a player: that player is sent to Home with a message.
- [ ] While in this room, try to join another from the same account on another laptop: refused.

## 4. Playing

With three players (people or bots) seated, the host presses **Start game**.

- [ ] Everyone moves to the table. Your own seat is at the bottom on every laptop.
- [ ] You see your two cards face up and everyone else's face down.
- [ ] The dealer button, both blinds and the pot are shown, and the pot total is right.
- [ ] The countdown ring runs on whoever is to act, on every laptop, and turns red near the end.
- [ ] On your turn only legal buttons appear: no **Check** when there is a bet to call.
- [ ] **Fold**, **Check**, **Call** each work and the hand moves on.
- [ ] The slider and the amount box move together. **Min**, **1/2 pot**, **Pot** and **All-in** set
      sensible amounts. Typing an amount below the minimum disables the raise button.
- [ ] Keys **F**, **C** and **R** act as Fold, Check or Call, and Raise. They do nothing while you
      are typing in the chat box.
- [ ] Clicking a button twice quickly sends only one action.
- [ ] Bets appear in front of players and move to the pot when the street ends.
- [ ] Flop, turn and river slide in.
- [ ] At showdown the remaining players' cards turn face up with the hand each made, and the winner
      is highlighted with the amount won. Stacks are right afterwards.
- [ ] A hand won by everyone else folding shows no cards.
- [ ] After a few seconds the next hand starts and the button has moved one seat.
- [ ] The hand log on the right follows every action. Your own lines read "You raise", "You win".

## 5. All-ins and side pots

- [ ] Two players go all-in before the flop: the rest of the board is dealt a street at a time with
      a pause, with nobody asked to act.
- [ ] A short stack goes all-in and two bigger stacks keep betting: a main pot and a side pot are
      shown, and the right players win each.
- [ ] A bet nobody calls is returned to the bettor (their stack goes back up).

## 6. Timing out, sitting out, going broke

- [ ] Let your turn run out: you are checked or folded, then shown as sitting out, and the strip
      under the table offers **Sit in**.
- [ ] Press **Sit in**: you are dealt in again within a hand or two (when the big blind reaches you).
- [ ] Press **Sit out** in the top bar: you finish the current hand and are not dealt the next.
- [ ] Lose all your chips: the strip offers **Rebuy** (in a room that allows it). Rebuying gives you
      the starting stack.
- [ ] In a room created with rebuys off, a broke player is told there are no rebuys and can watch.
- [ ] Someone sits down while the game is running: they are not dealt in until the big blind
      reaches them.

## 7. The host's controls

- [ ] **Pause**: the current hand finishes, no new hand starts, and everyone sees "Paused".
- [ ] **Resume**: the next hand is dealt.
- [ ] Only the host sees Pause and End room.
- [ ] The host leaves the room: another player becomes host and gets the host's buttons.
- [ ] **End room** asks first; on confirming, everyone is sent to Home with "The room has closed".

## 8. Chat

- [ ] A message typed by one player appears on every laptop, with the sender's name ("You" on
      their own).
- [ ] Two messages within a second: the second is refused with a "too quickly" message.
- [ ] A long message wraps inside the chat panel instead of running off the side.

## 9. Leaving and reconnecting

- [ ] **Leave room** in the middle of a hand asks first; on confirming, your hand is folded and the
      others play on.
- [ ] Turn a laptop's Wi-Fi off during a hand: within a few seconds "Reconnecting..." covers the
      table. The others see that player as Offline.
- [ ] Turn Wi-Fi back on: the overlay goes by itself and the table is right, cards and all.
- [ ] Stay off for more than a minute: you return sitting out, with your seat and chips kept.
- [ ] Close the app during a game and reopen it: join the same code and you have your seat back.
- [ ] Log in as the same player on a second laptop and join the room: the first laptop is told it
      was disconnected because you connected from somewhere else.
- [ ] Stop the server during a game: the app shows "Reconnecting...". Start it again: the app says
      the room is no longer open and returns to Home.

## 10. History, leaderboard and export

- [ ] **My hand history** lists the hands you played, newest first, a page at a time.
- [ ] Choosing a hand shows it step by step. Your own cards are shown; another player's are shown
      only if they reached showdown.
- [ ] **Export to a file...** asks where to save and writes a text file with one line per hand and
      a total at the end. Open it to check.
- [ ] **Leaderboard** lists every player by chips won less chips lost, with your own line marked.

## 11. Three laptops, thirty hands

The Phase 7 target: three laptops (or three windows on one PC) create or join one room by code and
play 30 hands, including:

- [ ] an all-in with a side pot,
- [ ] some chat,
- [ ] a player sitting out and back in,
- [ ] a reconnect after closing the app.

Afterwards, on the host machine:

- [ ] `data/hand-history/room-<code>/<date>.txt` has all 30 hands.
- [ ] The leaderboard's nets add up to zero.
- [ ] `data/logs/server.log` has no ERROR lines.
