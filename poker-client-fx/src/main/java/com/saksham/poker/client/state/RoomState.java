package com.saksham.poker.client.state;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.HandInfo;
import com.saksham.poker.common.protocol.dto.HandSeatInfo;
import com.saksham.poker.common.protocol.dto.LeaveReason;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.ShownHandInfo;
import com.saksham.poker.common.protocol.dto.TurnInfo;
import com.saksham.poker.common.protocol.server.ActionRequired;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.ChatPosted;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.GameState;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.HostChanged;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PlayerJoined;
import com.saksham.poker.common.protocol.server.PlayerLeft;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * The room as this player currently knows it, rebuilt from the server's messages. The screens draw
 * from it and never from the messages themselves, so after a reconnect a single snapshot puts
 * everything right.
 *
 * <p>Each kind of message has its own {@code on...} method; {@link #apply} picks the one for a
 * message. Like everything the screens bind to, it must only be changed on the JavaFX thread.
 */
public final class RoomState {

    private static final int MAX_LOG_LINES = 500;

    // ---- the room
    private final StringProperty code = new SimpleStringProperty("");
    private final ObjectProperty<RoomSettingsInfo> settings = new SimpleObjectProperty<>();
    private final ObjectProperty<com.saksham.poker.common.protocol.dto.RoomState> stage =
            new SimpleObjectProperty<>(com.saksham.poker.common.protocol.dto.RoomState.WAITING);
    private final LongProperty hostUserId = new SimpleLongProperty(-1);
    private final LongProperty yourUserId = new SimpleLongProperty(-1);
    private final IntegerProperty yourSeat = new SimpleIntegerProperty(PlayerInfo.NO_SEAT);
    private final ObservableList<PlayerInfo> players = FXCollections.observableArrayList();
    private final ObservableList<SeatViewModel> seats = FXCollections.observableArrayList();
    /** True once this player is no longer in the room; {@link #goneReason} says why. */
    private final BooleanProperty gone = new SimpleBooleanProperty();
    private final StringProperty goneReason = new SimpleStringProperty("");

    // ---- the hand being played
    private final BooleanProperty handInProgress = new SimpleBooleanProperty();
    private final LongProperty handNo = new SimpleLongProperty();
    private final StringProperty street = new SimpleStringProperty("");
    private final ObservableList<Card> board = FXCollections.observableArrayList();
    private final ObservableList<PotInfo> pots = FXCollections.observableArrayList();
    private final ObjectProperty<TurnInfo> turn = new SimpleObjectProperty<>();
    /** When the current turn runs out, by this computer's clock; 0 when nobody is to act. */
    private final LongProperty turnEndsAtMs = new SimpleLongProperty();
    private final ObservableList<Card> yourCards = FXCollections.observableArrayList();
    private final IntegerProperty playersInHand = new SimpleIntegerProperty();

    // ---- the side panel
    private final ObservableList<String> handLog = FXCollections.observableArrayList();
    private final ObservableList<ChatLine> chat = FXCollections.observableArrayList();
    /** The newest refusal from the server. Set afresh each time, even if the text is the same. */
    private final ObjectProperty<ErrorMessage> lastError = new SimpleObjectProperty<>();
    /** Told of each moment worth a sound. Does nothing until a screen asks to listen. */
    private Consumer<Cue> cueListener = cue -> { };

    // =====================================================================================
    // Applying messages
    // =====================================================================================

    /**
     * Names who is told of the moments worth marking with a sound. There is one listener at a time:
     * the screen now showing the table.
     */
    public void onCue(Consumer<Cue> listener) {
        cueListener = listener == null ? cue -> { } : listener;
    }

    /** Updates the state for one message from the server. Messages it does not know are ignored. */
    public void apply(ServerMessage message) {
        if (message instanceof RoomSnapshot m) {
            onSnapshot(m);
        } else if (message instanceof PlayerJoined m) {
            onPlayerJoined(m);
        } else if (message instanceof PlayerLeft m) {
            onPlayerLeft(m);
        } else if (message instanceof SeatUpdate m) {
            onSeatUpdate(m);
        } else if (message instanceof HostChanged m) {
            onHostChanged(m);
        } else if (message instanceof GameState m) {
            onGameState(m);
        } else if (message instanceof HandStarted m) {
            onHandStarted(m);
        } else if (message instanceof BlindPosted m) {
            onBlindPosted(m);
        } else if (message instanceof HoleCards m) {
            onHoleCards(m);
        } else if (message instanceof ActionRequired m) {
            onActionRequired(m);
        } else if (message instanceof PlayerActed m) {
            onPlayerActed(m);
        } else if (message instanceof StreetDealt m) {
            onStreetDealt(m);
        } else if (message instanceof BetReturned m) {
            onBetReturned(m);
        } else if (message instanceof PotsUpdated m) {
            onPotsUpdated(m);
        } else if (message instanceof Showdown m) {
            onShowdown(m);
        } else if (message instanceof HandEnded m) {
            onHandEnded(m);
        } else if (message instanceof ChatPosted m) {
            chat.add(new ChatLine(m.userId(), m.username(), m.text()));
        } else if (message instanceof ErrorMessage m) {
            lastError.set(m);
        }
    }

    /** Replaces everything with what the server says is true now. */
    private void onSnapshot(RoomSnapshot snapshot) {
        code.set(snapshot.code());
        settings.set(snapshot.settings());
        stage.set(snapshot.state());
        hostUserId.set(snapshot.hostUserId());
        yourUserId.set(snapshot.yourUserId());
        yourSeat.set(snapshot.yourSeat());
        gone.set(false);

        if (seats.size() != snapshot.settings().maxPlayers()) {
            List<SeatViewModel> fresh = new ArrayList<>();
            for (int i = 0; i < snapshot.settings().maxPlayers(); i++) {
                fresh.add(new SeatViewModel(i));
            }
            seats.setAll(fresh);
        }
        seats.forEach(SeatViewModel::clear);
        players.setAll(snapshot.players());
        for (PlayerInfo player : snapshot.players()) {
            seat(player);
        }

        clearHand();
        HandInfo hand = snapshot.hand();
        if (hand != null) {
            handInProgress.set(true);
            handNo.set(hand.handNo());
            street.set(hand.street());
            board.setAll(hand.board());
            pots.setAll(hand.pots());
            for (HandSeatInfo info : hand.seats()) {
                SeatViewModel seat = seatAt(info.seat());
                if (seat == null) {
                    continue;
                }
                seat.inHandProperty().set(true);
                seat.stackProperty().set(info.stack());
                seat.streetBetProperty().set(info.streetBet());
                seat.foldedProperty().set(info.folded());
                seat.allInProperty().set(info.allIn());
                seat.buttonProperty().set(info.seat() == hand.buttonSeat());
            }
            setTurn(hand.turn());
            if (hand.turn() != null) {
                // Joining part-way through a turn: go by the server's deadline, but never show more
                // than a full turn, in case the two computers' clocks disagree.
                long left = hand.turn().deadlineEpochMs() - System.currentTimeMillis();
                turnEndsAtMs.set(System.currentTimeMillis() + Math.max(0, Math.min(left, turnMs())));
            }
            yourCards.setAll(snapshot.yourCards());
            SeatViewModel mine = seatAt(snapshot.yourSeat());
            if (mine != null) {
                mine.cards().setAll(snapshot.yourCards());
            }
        }
    }

    private void onPlayerJoined(PlayerJoined joined) {
        upsert(joined.player());
        log(joined.player().username() + " joined the room");
    }

    private void onPlayerLeft(PlayerLeft left) {
        PlayerInfo player = player(left.userId());
        String name = player == null ? "A player" : player.username();
        players.removeIf(each -> each.userId() == left.userId());
        for (SeatViewModel seat : seats) {
            if (seat.userIdProperty().get() == left.userId()) {
                // Their cards and bets stay on show until the hand ends; only the name goes.
                seat.occupiedProperty().set(false);
                seat.userIdProperty().set(-1);
                seat.usernameProperty().set("");
            }
        }
        if (left.userId() == yourUserId.get()) {
            yourSeat.set(PlayerInfo.NO_SEAT);
            goneReason.set(switch (left.reason()) {
                case KICKED -> "The host removed you from the room.";
                case DISCONNECTED -> "You were disconnected from the room.";
                case LEFT -> "You left the room.";
            });
            gone.set(true);
        } else {
            log(name + (left.reason() == LeaveReason.KICKED ? " was removed by the host" : " left the room"));
        }
    }

    private void onSeatUpdate(SeatUpdate update) {
        PlayerInfo player = update.player();
        upsert(player);
        if (player.userId() == yourUserId.get()) {
            yourSeat.set(player.seat());
        }
    }

    private void onHostChanged(HostChanged changed) {
        hostUserId.set(changed.hostUserId());
        PlayerInfo host = player(changed.hostUserId());
        if (host != null) {
            log(host.username() + " is now the host");
        }
    }

    private void onGameState(GameState state) {
        stage.set(state.state());
        switch (state.state()) {
            case PLAYING -> log("The game is on");
            case PAUSED -> log("The host paused the game");
            case CLOSED -> {
                goneReason.set("The room has closed.");
                gone.set(true);
            }
            default -> { }
        }
    }

    private void onHandStarted(HandStarted started) {
        clearHand();
        handInProgress.set(true);
        handNo.set(started.handNo());
        street.set("PREFLOP");
        // Work out the order of the deal first, so each seat knows its turn before its cards appear:
        // clockwise, starting with the player on the button's left.
        List<Integer> dealt = new ArrayList<>(started.stacks().keySet());
        dealt.sort((a, b) -> Integer.compare(Math.floorMod(a - started.buttonSeat() - 1, 1_000),
                Math.floorMod(b - started.buttonSeat() - 1, 1_000)));
        playersInHand.set(dealt.size());
        for (int i = 0; i < dealt.size(); i++) {
            SeatViewModel seat = seatAt(dealt.get(i));
            if (seat != null) {
                seat.dealPositionProperty().set(i);
            }
        }
        started.stacks().forEach((seatNo, stack) -> {
            SeatViewModel seat = seatAt(seatNo);
            if (seat != null) {
                seat.inHandProperty().set(true);
                seat.stackProperty().set(stack);
                seat.buttonProperty().set(seatNo == started.buttonSeat());
            }
        });
        log("Hand #" + started.handNo() + " - blinds " + started.smallBlind() + "/" + started.bigBlind());
        cueListener.accept(Cue.DEAL);
    }

    private void onBlindPosted(BlindPosted blind) {
        SeatViewModel seat = seatAt(blind.seat());
        if (seat == null) {
            return;
        }
        seat.stackProperty().set(seat.stackProperty().get() - blind.amount());
        seat.streetBetProperty().set(seat.streetBetProperty().get() + blind.amount());
        seat.allInProperty().set(blind.allIn());
        log(nameAt(blind.seat()) + (isYou(blind.seat()) ? " post the " : " posts the ")
                + (blind.bigBlind() ? "big" : "small") + " blind " + blind.amount());
    }

    private void onHoleCards(HoleCards dealt) {
        yourCards.setAll(dealt.cards());
        SeatViewModel seat = seatAt(dealt.seat());
        if (seat != null) {
            seat.cards().setAll(dealt.cards());
        }
        log("You are dealt " + cardText(dealt.cards()));
    }

    private void onActionRequired(ActionRequired required) {
        setTurn(new TurnInfo(required.seat(), required.turnId(), required.canCheck(), required.callAmount(),
                required.canBet(), required.canRaise(), required.minRaiseTo(), required.maxRaiseTo(),
                required.deadlineEpochMs()));
        // A turn that has just been announced has its full time left. Counting from now, on this
        // computer's clock, keeps the timer right even if the server's clock is set differently.
        turnEndsAtMs.set(System.currentTimeMillis() + turnMs());
        if (isYou(required.seat())) {
            cueListener.accept(Cue.YOUR_TURN);
        }
    }

    private long turnMs() {
        RoomSettingsInfo current = settings.get();
        return (current == null ? 25 : current.turnSeconds()) * 1_000L;
    }

    private void onPlayerActed(PlayerActed acted) {
        SeatViewModel seat = seatAt(acted.seat());
        if (seat != null) {
            seat.stackProperty().set(acted.stack());
            seat.streetBetProperty().set(acted.streetBet());
            seat.allInProperty().set(acted.allIn());
            seat.foldedProperty().set(seat.foldedProperty().get() || acted.action() == ActionType.FOLD);
            seat.lastActionProperty().set(shortAction(acted));
        }
        TurnInfo current = turn.get();
        if (current != null && current.seat() == acted.seat()) {
            setTurn(null);
        }
        boolean you = isYou(acted.seat());
        log(nameAt(acted.seat()) + " " + longAction(acted, you)
                + (acted.allIn() ? (you ? " and are all-in" : " and is all-in") : ""));
        cueListener.accept(switch (acted.action()) {
            case FOLD -> Cue.FOLD;
            case CHECK -> Cue.CHECK;
            default -> Cue.CHIPS;
        });
    }

    private void onStreetDealt(StreetDealt dealt) {
        street.set(dealt.street());
        board.setAll(dealt.board());
        for (SeatViewModel seat : seats) {
            seat.streetBetProperty().set(0);
            seat.lastActionProperty().set("");
        }
        String name = dealt.street().charAt(0) + dealt.street().substring(1).toLowerCase();
        log(name + ": " + cardText(dealt.cards()));
        cueListener.accept(Cue.BOARD);
    }

    private void onBetReturned(BetReturned returned) {
        SeatViewModel seat = seatAt(returned.seat());
        if (seat != null) {
            seat.stackProperty().set(seat.stackProperty().get() + returned.amount());
            seat.streetBetProperty().set(Math.max(0, seat.streetBetProperty().get() - returned.amount()));
            seat.allInProperty().set(false);
        }
        log(returned.amount() + " returned to " + (isYou(returned.seat()) ? "you" : nameAt(returned.seat())));
    }

    private void onPotsUpdated(PotsUpdated updated) {
        pots.setAll(updated.pots());
        seats.forEach(seat -> seat.streetBetProperty().set(0));
    }

    private void onShowdown(Showdown showdown) {
        street.set("SHOWDOWN");
        setTurn(null);
        int order = 0;
        for (ShownHandInfo shown : showdown.hands()) {
            SeatViewModel seat = seatAt(shown.seat());
            String kind = handName(shown.category());
            if (seat != null) {
                // Set before the cards, so the screen knows when in the sequence to turn them over.
                seat.revealOrderProperty().set(order++);
                seat.cards().setAll(shown.cards());
                seat.shownHandProperty().set(kind);
            }
            log(nameAt(shown.seat()) + (isYou(shown.seat()) ? " show " : " shows ") + cardText(shown.cards())
                    + " (" + kind.toLowerCase() + ")");
        }
    }

    private void onHandEnded(HandEnded ended) {
        setTurn(null);
        handInProgress.set(false);
        pots.clear();
        ended.stacks().forEach((seatNo, stack) -> {
            SeatViewModel seat = seatAt(seatNo);
            if (seat != null) {
                seat.stackProperty().set(stack);
                seat.streetBetProperty().set(0);
            }
        });
        for (PayoutInfo payout : ended.payouts()) {
            SeatViewModel seat = seatAt(payout.seat());
            if (seat != null) {
                seat.wonProperty().set(seat.wonProperty().get() + payout.amount());
            }
        }
        for (SeatViewModel seat : seats) {
            if (seat.wonProperty().get() > 0) {
                log(nameAt(seat.seat()) + (isYou(seat.seat()) ? " win " : " wins ") + seat.wonProperty().get());
                if (isYou(seat.seat())) {
                    cueListener.accept(Cue.YOU_WIN);
                }
            }
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private void clearHand() {
        handInProgress.set(false);
        street.set("");
        board.clear();
        pots.clear();
        yourCards.clear();
        setTurn(null);
        seats.forEach(SeatViewModel::clearHand);
    }

    private void setTurn(TurnInfo newTurn) {
        if (newTurn == null) {
            turnEndsAtMs.set(0);
        }
        turn.set(newTurn);
        for (SeatViewModel seat : seats) {
            seat.turnProperty().set(newTurn != null && newTurn.seat() == seat.seat());
        }
    }

    /** Adds a player or replaces what is known about them, and puts them in the right seat. */
    private void upsert(PlayerInfo player) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).userId() == player.userId()) {
                players.set(i, player);
                seat(player);
                return;
            }
        }
        players.add(player);
        seat(player);
    }

    private void seat(PlayerInfo player) {
        for (SeatViewModel seat : seats) {
            if (seat.userIdProperty().get() == player.userId() && seat.seat() != player.seat()) {
                seat.clear(); // they moved, or stood up
            }
        }
        SeatViewModel seat = seatAt(player.seat());
        if (seat == null) {
            return;
        }
        seat.occupiedProperty().set(true);
        seat.userIdProperty().set(player.userId());
        seat.usernameProperty().set(player.username());
        seat.stackProperty().set(player.stack());
        seat.sittingOutProperty().set(player.sittingOut());
        seat.connectedProperty().set(player.connected());
    }

    /** The view model of a seat, or null if the number is not a seat in this room. */
    public SeatViewModel seatAt(int seatNo) {
        return seatNo >= 0 && seatNo < seats.size() ? seats.get(seatNo) : null;
    }

    /** What is known about a user, or null if they are not in the room. */
    public PlayerInfo player(long userId) {
        for (PlayerInfo player : players) {
            if (player.userId() == userId) {
                return player;
            }
        }
        return null;
    }

    /** "You" for your own seat, otherwise the player's name, or the seat number if nobody is named. */
    private String nameAt(int seatNo) {
        if (isYou(seatNo)) {
            return "You";
        }
        SeatViewModel seat = seatAt(seatNo);
        if (seat != null && !seat.usernameProperty().get().isEmpty()) {
            return seat.usernameProperty().get();
        }
        return "Seat " + (seatNo + 1);
    }

    private boolean isYou(int seatNo) {
        SeatViewModel seat = seatAt(seatNo);
        return seat != null && seat.userIdProperty().get() >= 0 && seat.userIdProperty().get() == yourUserId.get();
    }

    private void log(String line) {
        handLog.add(line);
        if (handLog.size() > MAX_LOG_LINES) {
            handLog.remove(0, handLog.size() - MAX_LOG_LINES);
        }
    }

    private static String shortAction(PlayerActed acted) {
        return switch (acted.action()) {
            case FOLD -> "Fold";
            case CHECK -> "Check";
            case CALL -> "Call " + acted.amount();
            case BET -> "Bet " + acted.amount();
            default -> "Raise " + acted.streetBet();
        };
    }

    /** The action as a phrase: "raises to 300" for someone else, "raise to 300" after "You". */
    private static String longAction(PlayerActed acted, boolean you) {
        String s = you ? "" : "s";
        return switch (acted.action()) {
            case FOLD -> "fold" + s;
            case CHECK -> "check" + s;
            case CALL -> "call" + s + " " + acted.amount();
            case BET -> "bet" + s + " " + acted.amount();
            default -> "raise" + s + " to " + acted.streetBet();
        };
    }

    /** "TWO_PAIR" as "Two pair". */
    static String handName(String category) {
        String words = category.replace('_', ' ').toLowerCase();
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String cardText(List<Card> cards) {
        StringJoiner joined = new StringJoiner(" ");
        cards.forEach(card -> joined.add(card.toString()));
        return joined.toString();
    }

    // =====================================================================================
    // What the screens read
    // =====================================================================================

    public StringProperty codeProperty() {
        return code;
    }

    public ObjectProperty<RoomSettingsInfo> settingsProperty() {
        return settings;
    }

    /** WAITING, PLAYING, PAUSED or CLOSED. */
    public ObjectProperty<com.saksham.poker.common.protocol.dto.RoomState> stageProperty() {
        return stage;
    }

    public boolean waiting() {
        return stage.get() == com.saksham.poker.common.protocol.dto.RoomState.WAITING;
    }

    public boolean paused() {
        return stage.get() == com.saksham.poker.common.protocol.dto.RoomState.PAUSED;
    }

    public LongProperty hostUserIdProperty() {
        return hostUserId;
    }

    public LongProperty yourUserIdProperty() {
        return yourUserId;
    }

    public boolean youAreHost() {
        return hostUserId.get() >= 0 && hostUserId.get() == yourUserId.get();
    }

    /** Your seat number, or {@link PlayerInfo#NO_SEAT}. */
    public IntegerProperty yourSeatProperty() {
        return yourSeat;
    }

    public boolean youAreSeated() {
        return yourSeat.get() != PlayerInfo.NO_SEAT;
    }

    /** Everyone in the room, seated or not. */
    public ObservableList<PlayerInfo> players() {
        return players;
    }

    /** One view model per seat, in seat order. */
    public ObservableList<SeatViewModel> seats() {
        return seats;
    }

    public int seatedCount() {
        int count = 0;
        for (PlayerInfo player : players) {
            if (player.seated()) {
                count++;
            }
        }
        return count;
    }

    public BooleanProperty goneProperty() {
        return gone;
    }

    public StringProperty goneReasonProperty() {
        return goneReason;
    }

    public BooleanProperty handInProgressProperty() {
        return handInProgress;
    }

    public LongProperty handNoProperty() {
        return handNo;
    }

    public StringProperty streetProperty() {
        return street;
    }

    public ObservableList<Card> board() {
        return board;
    }

    public ObservableList<PotInfo> pots() {
        return pots;
    }

    /** Whose turn it is and what they may do, or null. */
    public ObjectProperty<TurnInfo> turnProperty() {
        return turn;
    }

    /** When the current turn runs out, in this computer's time; 0 when nobody is to act. */
    public LongProperty turnEndsAtMsProperty() {
        return turnEndsAtMs;
    }

    /** All chips in the middle: the pots plus the bets not yet collected. */
    public long chipsInPlay() {
        long total = 0;
        for (PotInfo pot : pots) {
            total += pot.amount();
        }
        for (SeatViewModel seat : seats) {
            total += seat.streetBetProperty().get();
        }
        return total;
    }

    /** How many players were dealt into the current hand. */
    public int playersInHand() {
        return playersInHand.get();
    }

    /** How many hands were turned over at the last showdown; 0 if the hand ended without one. */
    public int handsShown() {
        int count = 0;
        for (SeatViewModel seat : seats) {
            if (seat.revealOrderProperty().get() >= 0) {
                count++;
            }
        }
        return count;
    }

    public boolean yourTurn() {
        TurnInfo current = turn.get();
        return current != null && youAreSeated() && current.seat() == yourSeat.get();
    }

    public ObservableList<Card> yourCards() {
        return yourCards;
    }

    public ObservableList<String> handLog() {
        return handLog;
    }

    public ObservableList<ChatLine> chat() {
        return chat;
    }

    public ObjectProperty<ErrorMessage> lastErrorProperty() {
        return lastError;
    }
}
