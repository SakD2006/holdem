package com.saksham.poker.server.room;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.action.AllIn;
import com.saksham.poker.common.action.Bet;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.Check;
import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.common.exception.GameAlreadyStartedException;
import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.common.exception.InvalidAmountException;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.NotEnoughPlayersException;
import com.saksham.poker.common.exception.NotHostException;
import com.saksham.poker.common.exception.NotInRoomException;
import com.saksham.poker.common.exception.NotYourTurnException;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.common.exception.RebuyNotAllowedException;
import com.saksham.poker.common.exception.RoomClosedException;
import com.saksham.poker.common.exception.RoomFullException;
import com.saksham.poker.common.exception.SeatTakenException;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.HandInfo;
import com.saksham.poker.common.protocol.dto.LeaveReason;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.common.protocol.server.ChatPosted;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.GameState;
import com.saksham.poker.common.protocol.server.HostChanged;
import com.saksham.poker.common.protocol.server.PlayerJoined;
import com.saksham.poker.common.protocol.server.PlayerLeft;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.engine.card.DeckFactory;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.BlindPosted;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.event.HandCompleted;
import com.saksham.poker.engine.event.PlayerActed;
import com.saksham.poker.engine.event.PotAwarded;
import com.saksham.poker.engine.event.StreetDealt;
import com.saksham.poker.engine.hand.HandConfig;
import com.saksham.poker.engine.hand.HandResult;
import com.saksham.poker.engine.hand.HoldemHand;
import com.saksham.poker.engine.hand.Street;
import com.saksham.poker.engine.rules.LegalActions;
import com.saksham.poker.server.db.HandRecord;
import com.saksham.poker.server.player.ActionRequest;
import com.saksham.poker.server.player.SeatController;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One room: who is in it, who sits where, and the hand being played.
 *
 * <p>A room is not thread-safe and does not need to be. Exactly one thread, its {@link RoomActor},
 * calls {@link #run}; everything else reaches the room by queueing a {@link RoomCommand}. The only
 * things other threads may read are {@link #state()} and {@link #seatedCount()}.
 */
public final class Room {

    private static final Logger log = LoggerFactory.getLogger(Room.class);

    static final int MAX_CHAT_LENGTH = 200;
    static final long CHAT_INTERVAL_MS = 1_000;

    private final String code;
    private final RoomSettings settings;
    private final RoomTimings timings;
    private final DeckFactory decks;
    private final Clock clock;
    private final RoomScheduler scheduler;
    private final RoomListener listener;

    /** Everyone in the room, in the order they joined. */
    private final Map<Long, RoomMember> members = new LinkedHashMap<>();
    /** Who sits where; null for an empty seat. */
    private final RoomMember[] seats;

    private volatile RoomState state = RoomState.WAITING;
    private volatile int seatedCount;
    private long hostUserId;

    // ---- the hand in progress; all null or empty between hands
    private HoldemHand hand;
    private HandView view;
    /** Seat to user for the players dealt into the current hand. A player who leaves is removed. */
    private Map<Integer, Long> handPlayers = Map.of();
    private long handNo;
    private int buttonSeat = -1;
    /** Everyone dealt into the current hand, including anyone who has since left. For the record. */
    private Map<Integer, RoomMember> handRoster = Map.of();
    private final List<HandRecord.ActionRecord> handActions = new ArrayList<>();
    private Instant handStartedAt;

    /** Engine events not yet shown to the players. */
    private final Deque<GameEvent> pending = new ArrayDeque<>();
    private boolean deliveryPaused;
    private long deliveryToken;

    // ---- the turn in progress
    private long turnId;
    private ActionRequest currentTurn;
    private RoomScheduler.Cancellable turnTimer;

    private boolean nextHandScheduled;
    private long idleGeneration;

    public Room(String code, long hostUserId, RoomSettings settings, RoomTimings timings, DeckFactory decks,
            Clock clock, RoomScheduler scheduler, RoomListener listener) {
        this.code = code;
        this.hostUserId = hostUserId;
        this.settings = settings;
        this.timings = timings;
        this.decks = decks;
        this.clock = clock;
        this.scheduler = scheduler;
        this.listener = listener;
        this.seats = new RoomMember[settings.maxPlayers()];
    }

    // =====================================================================================
    // Running commands
    // =====================================================================================

    /**
     * Carries out one command. A command the rules refuse is reported to whoever sent it; anything
     * unexpected is logged. Either way the room carries on: one bad command never takes a room down.
     */
    public void run(RoomCommand command) {
        try {
            if (state == RoomState.CLOSED) {
                throw new RoomClosedException("This room has closed.");
            }
            command.execute(this);
            deliverPending();
        } catch (PokerException e) {
            command.failed(this, e);
        } catch (RuntimeException e) {
            log.error("Room {}: {} failed", code, command.getClass().getSimpleName(), e);
        }
    }

    /** Called once when the room is created, before anyone has joined. */
    void opened() {
        scheduleIdleCheck();
    }

    public String code() {
        return code;
    }

    /** Safe to read from any thread. */
    public RoomState state() {
        return state;
    }

    /** How many seats are taken. Safe to read from any thread. */
    public int seatedCount() {
        return seatedCount;
    }

    // =====================================================================================
    // Joining, seats and leaving
    // =====================================================================================

    void join(long userId, String username, SeatController controller) throws PokerException {
        RoomMember existing = members.get(userId);
        if (existing != null) {
            // Already here: treat it as coming back.
            reconnected(userId, controller);
            return;
        }
        if (members.size() >= settings.maxPlayers()) {
            throw new RoomFullException("This room is full: all " + settings.maxPlayers() + " places are taken.");
        }
        RoomMember member = new RoomMember(userId, username, controller);
        members.put(userId, member);
        idleGeneration++;
        controller.onEvent(snapshotFor(member));
        broadcastExcept(member, new PlayerJoined(infoOf(member)));
    }

    /** A join was refused, so the user is free to try another room. */
    void joinRefused(long userId) {
        if (!members.containsKey(userId)) {
            listener.released(userId, code);
        }
    }

    void takeSeat(long userId, int seat) throws PokerException {
        RoomMember member = requireMember(userId);
        if (seat < 0 || seat >= seats.length) {
            throw new InvalidRequestException(
                    "Seat " + seat + " does not exist. This room has seats 0 to " + (seats.length - 1) + ".");
        }
        if (seats[seat] == member) {
            return;
        }
        if (seats[seat] != null) {
            throw new SeatTakenException("Seat " + seat + " is taken. Choose another seat.");
        }
        if (member.seated()) {
            if (state != RoomState.WAITING) {
                throw new InvalidRequestException("You cannot change seats once the game has started.");
            }
            seats[member.seat] = null;
        } else {
            member.stack = settings.startingStack();
            // Sitting down in a running game means waiting for the big blind, like everyone else.
            member.waitingForBigBlind = state != RoomState.WAITING;
        }
        member.seat = seat;
        seats[seat] = member;
        countSeats();
        broadcast(new SeatUpdate(infoOf(member)));
        scheduleNextHand(timings.betweenHandsMs());
    }

    void leave(long userId) throws PokerException {
        remove(requireMember(userId), LeaveReason.LEFT);
    }

    void kick(long hostId, long targetId) throws PokerException {
        requireHost(hostId, "remove a player");
        if (state != RoomState.WAITING) {
            throw new InvalidRequestException("Players can only be removed before the game starts.");
        }
        if (hostId == targetId) {
            throw new InvalidRequestException("You cannot remove yourself. Leave the room instead.");
        }
        RoomMember target = members.get(targetId);
        if (target == null) {
            throw new InvalidRequestException("That player is not in this room.");
        }
        remove(target, LeaveReason.KICKED);
    }

    private void remove(RoomMember member, LeaveReason reason) {
        if (member.seated()) {
            if (inCurrentHand(member)) {
                // Leaving in the middle of a hand folds it. The chips already bet stay in the pot.
                int seat = member.seat;
                boolean wasTheirTurn = currentTurn != null && currentTurn.seat() == seat;
                handPlayers.remove(seat);
                if (wasTheirTurn) {
                    clearTurn();
                }
                pending.addAll(hand.forceFold(seat));
            }
            seats[member.seat] = null;
            member.seat = PlayerInfo.NO_SEAT;
        }
        // The leaver is told too, so their app knows it is out.
        broadcast(new PlayerLeft(member.userId, reason));
        members.remove(member.userId);
        countSeats();
        listener.released(member.userId, code);

        if (members.isEmpty()) {
            close();
            return;
        }
        if (member.userId == hostUserId) {
            passHost();
        }
        if (noOneConnected()) {
            scheduleIdleCheck();
        }
    }

    /** The host has gone: the next seated player becomes host, or failing that anyone still here. */
    private void passHost() {
        RoomMember next = null;
        for (RoomMember seat : seats) {
            if (seat != null) {
                next = seat;
                break;
            }
        }
        if (next == null) {
            next = members.values().iterator().next();
        }
        hostUserId = next.userId;
        broadcast(new HostChanged(hostUserId));
    }

    // =====================================================================================
    // Host controls
    // =====================================================================================

    void startGame(long userId) throws PokerException {
        requireHost(userId, "start the game");
        if (state != RoomState.WAITING) {
            throw new GameAlreadyStartedException("The game has already started.");
        }
        if (seatedCount < 2) {
            throw new NotEnoughPlayersException("At least 2 players must be seated before the game can start.");
        }
        setState(RoomState.PLAYING);
        startNextHand();
    }

    void pauseGame(long userId) throws PokerException {
        requireHost(userId, "pause the game");
        if (state != RoomState.PLAYING) {
            throw new InvalidRequestException("The game is not running, so it cannot be paused.");
        }
        // The hand being played finishes; no new one starts.
        setState(RoomState.PAUSED);
    }

    void resumeGame(long userId) throws PokerException {
        requireHost(userId, "resume the game");
        if (state != RoomState.PAUSED) {
            throw new InvalidRequestException("The game is not paused.");
        }
        setState(RoomState.PLAYING);
        scheduleNextHand(0);
    }

    void endRoom(long userId) throws PokerException {
        requireHost(userId, "end the room");
        close();
    }

    private void close() {
        if (state == RoomState.CLOSED) {
            return;
        }
        clearTurn();
        pending.clear();
        hand = null;
        view = null;
        handPlayers = Map.of();
        handRoster = Map.of();
        setState(RoomState.CLOSED);
        for (RoomMember member : members.values()) {
            listener.released(member.userId, code);
        }
        members.clear();
        Arrays.fill(seats, null);
        countSeats();
        listener.closed(code);
    }

    private void setState(RoomState newState) {
        state = newState;
        broadcast(new GameState(newState));
        listener.stateChanged(code, newState);
    }

    // =====================================================================================
    // Sitting out, rebuying, chat
    // =====================================================================================

    void sitOut(long userId) throws PokerException {
        RoomMember member = requireSeated(userId);
        if (!member.sittingOut) {
            member.sittingOut = true;
            broadcast(new SeatUpdate(infoOf(member)));
        }
    }

    void sitIn(long userId) throws PokerException {
        RoomMember member = requireSeated(userId);
        if (member.stack == 0 && !inCurrentHand(member)) {
            throw new InvalidRequestException("You have no chips. Rebuy before sitting back in.");
        }
        member.autoAct = false;
        if (member.sittingOut) {
            member.sittingOut = false;
            // Coming back in costs a wait for the big blind, unless the player never missed a hand.
            member.waitingForBigBlind = state != RoomState.WAITING && !inCurrentHand(member);
            broadcast(new SeatUpdate(infoOf(member)));
        }
        scheduleNextHand(timings.betweenHandsMs());
    }

    void rebuy(long userId) throws PokerException {
        RoomMember member = requireSeated(userId);
        if (!settings.rebuyAllowed()) {
            throw new RebuyNotAllowedException("This room does not allow rebuys.");
        }
        if (inCurrentHand(member) || member.stack > 0) {
            throw new RebuyNotAllowedException("You can only rebuy after losing all your chips.");
        }
        member.stack = settings.startingStack();
        member.sittingOut = false;
        member.autoAct = false;
        member.waitingForBigBlind = state != RoomState.WAITING;
        broadcast(new SeatUpdate(infoOf(member)));
        scheduleNextHand(timings.betweenHandsMs());
    }

    void chat(long userId, String text) throws PokerException {
        RoomMember member = requireMember(userId);
        String clean = text == null ? "" : text.replaceAll("\\p{Cntrl}", " ").trim();
        if (clean.isEmpty()) {
            throw new InvalidRequestException("The message is empty.");
        }
        if (clean.length() > MAX_CHAT_LENGTH) {
            throw new InvalidRequestException(
                    "A message can be up to " + MAX_CHAT_LENGTH + " characters; this one has " + clean.length() + ".");
        }
        long now = clock.millis();
        if (now - member.lastChatAtMs < CHAT_INTERVAL_MS) {
            throw new InvalidRequestException("You are sending messages too quickly. Wait a second and try again.");
        }
        member.lastChatAtMs = now;
        broadcast(new ChatPosted(userId, member.username, clean));
    }

    void sendSnapshot(long userId) throws PokerException {
        RoomMember member = requireMember(userId);
        member.controller.onEvent(snapshotFor(member));
    }

    // =====================================================================================
    // Connections
    // =====================================================================================

    void disconnected(long userId) {
        RoomMember member = members.get(userId);
        if (member == null || !member.connected) {
            return;
        }
        member.connected = false;
        long generation = ++member.connectionGeneration;
        broadcast(new SeatUpdate(infoOf(member)));
        scheduler.schedule(new RoomCommands.ReconnectGraceExpired(userId, generation), timings.reconnectGraceMs());
        if (noOneConnected()) {
            scheduleIdleCheck();
        }
    }

    void reconnected(long userId, SeatController controller) throws PokerException {
        RoomMember member = requireMember(userId);
        member.controller = controller;
        member.connected = true;
        member.connectionGeneration++;
        member.autoAct = false;
        idleGeneration++;
        controller.onEvent(snapshotFor(member));
        broadcastExcept(member, new SeatUpdate(infoOf(member)));
        if (currentTurn != null && currentTurn.seat() == member.seat && inCurrentHand(member)) {
            // It is still their turn: remind them, with the same turn and deadline.
            controller.onActionRequested(currentTurn);
        }
    }

    /** The grace period after a disconnect ran out without the player coming back. */
    void reconnectGraceExpired(long userId, long generation) {
        RoomMember member = members.get(userId);
        if (member == null || member.connected || member.connectionGeneration != generation) {
            return;
        }
        sitOutAndActFor(member);
    }

    /** Nobody has been connected for the idle period: the room closes. */
    void idleCheck(long generation) {
        if (generation == idleGeneration && noOneConnected()) {
            log.info("Room {} closed: nobody connected", code);
            close();
        }
    }

    private void scheduleIdleCheck() {
        scheduler.schedule(new RoomCommands.IdleCheck(++idleGeneration), timings.idleCloseMs());
    }

    private boolean noOneConnected() {
        for (RoomMember member : members.values()) {
            if (member.connected) {
                return false;
            }
        }
        return true;
    }

    // =====================================================================================
    // Playing hands
    // =====================================================================================

    /** Asks for the next hand to start after a delay, unless one is running or already asked for. */
    private void scheduleNextHand(long delayMs) {
        if (state != RoomState.PLAYING || hand != null || nextHandScheduled) {
            return;
        }
        if (delayMs <= 0) {
            startNextHand();
            return;
        }
        nextHandScheduled = true;
        scheduler.schedule(new RoomCommands.StartNextHand(), delayMs);
    }

    void startNextHand() {
        nextHandScheduled = false;
        if (state != RoomState.PLAYING || hand != null) {
            return;
        }
        List<RoomMember> players = choosePlayers();
        if (players == null) {
            // Fewer than two players can play. The next seat taken, sit-in or rebuy tries again.
            return;
        }
        Map<Integer, Long> stacks = new TreeMap<>();
        handPlayers = new HashMap<>();
        handRoster = new TreeMap<>();
        handActions.clear();
        handStartedAt = clock.instant();
        for (RoomMember player : players) {
            stacks.put(player.seat, player.stack);
            handPlayers.put(player.seat, player.userId);
            handRoster.put(player.seat, player);
            player.autoAct = false;
        }
        handNo++;
        view = new HandView();
        hand = new HoldemHand(
                new HandConfig(settings.smallBlind(), settings.bigBlind(), buttonSeat, stacks), decks.create());
        pending.addAll(hand.start());
    }

    /**
     * Picks who is dealt into the next hand and moves the button.
     *
     * <p>The button moves to the next player clockwise. A player waiting for the big blind is dealt
     * in only when they sit between the small blind and the player who would have been big blind:
     * they then post the big blind. Everyone else waiting keeps waiting, and the button reaches them
     * within one orbit.
     *
     * @return the players, or null if fewer than two can play
     */
    private List<RoomMember> choosePlayers() {
        List<RoomMember> ready = new ArrayList<>();
        for (RoomMember seat : seats) {
            if (seat != null && seat.readyToPlay()) {
                ready.add(seat);
            }
        }
        if (ready.size() < 2) {
            return null;
        }
        List<RoomMember> active = new ArrayList<>();
        for (RoomMember member : ready) {
            if (!member.waitingForBigBlind) {
                active.add(member);
            }
        }
        if (active.size() < 2) {
            // Not enough players to keep anyone waiting.
            ready.forEach(member -> member.waitingForBigBlind = false);
            active = ready;
        }

        List<RoomMember> afterOldButton = clockwiseAfter(buttonSeat, active);
        buttonSeat = afterOldButton.get(0).seat;
        List<RoomMember> afterButton = clockwiseAfter(buttonSeat, active);
        int from = afterButton.get(0).seat;
        int to = afterButton.get(1).seat;

        RoomMember joiner = null;
        int closest = Integer.MAX_VALUE;
        for (RoomMember member : ready) {
            int distance = Math.floorMod(member.seat - from, seats.length);
            if (member.waitingForBigBlind && distance > 0 && distance < Math.floorMod(to - from, seats.length)
                    && distance < closest) {
                joiner = member;
                closest = distance;
            }
        }
        List<RoomMember> players = new ArrayList<>(active);
        if (joiner != null) {
            joiner.waitingForBigBlind = false;
            players.add(joiner);
        }
        return players;
    }

    /** The given members in seat order, starting with the first one after {@code seat}. */
    private static List<RoomMember> clockwiseAfter(int seat, List<RoomMember> inSeatOrder) {
        int start = 0;
        for (int i = 0; i < inSeatOrder.size(); i++) {
            if (inSeatOrder.get(i).seat > seat) {
                start = i;
                break;
            }
        }
        List<RoomMember> ordered = new ArrayList<>(inSeatOrder.size());
        for (int i = 0; i < inSeatOrder.size(); i++) {
            ordered.add(inSeatOrder.get((start + i) % inSeatOrder.size()));
        }
        return ordered;
    }

    void playerAction(long userId, long answeredTurnId, ActionType type, long amount) throws PokerException {
        RoomMember member = requireMember(userId);
        if (currentTurn == null || !inCurrentHand(member) || currentTurn.seat() != member.seat) {
            throw new NotYourTurnException("It is not your turn.");
        }
        if (answeredTurnId != currentTurn.turnId()) {
            // An answer to a turn that has already passed, usually one that timed out. Ignore it.
            return;
        }
        int seat = member.seat;
        List<GameEvent> events = hand.apply(seat, toAction(type, amount));
        clearTurn();
        pending.addAll(events);
    }

    private static PlayerAction toAction(ActionType type, long amount) throws GameRuleException {
        if (type == null) {
            throw new InvalidAmountException("The action is missing.");
        }
        switch (type) {
            case FOLD:
                return new Fold();
            case CHECK:
                return new Check();
            case CALL:
                return new Call();
            case ALL_IN:
                return new AllIn();
            case BET:
            case RAISE:
                if (amount <= 0) {
                    throw new InvalidAmountException("A bet or raise needs an amount above 0.");
                }
                return type == ActionType.BET ? new Bet(amount) : new Raise(amount);
            default:
                throw new InvalidAmountException("Unknown action " + type + ".");
        }
    }

    void turnTimeout(long timedOutTurnId) {
        if (currentTurn == null || currentTurn.turnId() != timedOutTurnId) {
            return; // the player answered in time, or the hand moved on
        }
        RoomMember member = seats[currentTurn.seat()];
        if (member != null && inCurrentHand(member)) {
            // Someone who lets the clock run out is sat out, so they do not hold up every hand.
            sitOutAndActFor(member);
        } else {
            actFor(currentTurn.seat());
        }
    }

    /** Sits a player out and, for the rest of this hand, checks or folds for them at once. */
    private void sitOutAndActFor(RoomMember member) {
        member.sittingOut = true;
        member.autoAct = true;
        broadcast(new SeatUpdate(infoOf(member)));
        if (currentTurn != null && currentTurn.seat() == member.seat && inCurrentHand(member)) {
            actFor(member.seat);
        }
    }

    /** Acts for an absent player: check when that is free, fold otherwise. Never calls or bets. */
    private void actFor(int seat) {
        LegalActions legal = hand.legalActionsFor(seat);
        clearTurn();
        try {
            pending.addAll(hand.apply(seat, legal.canCheck() ? new Check() : new Fold()));
        } catch (GameRuleException e) {
            log.error("Room {}: could not act for seat {}; folding it", code, seat, e);
            pending.addAll(hand.forceFold(seat));
        }
    }

    // =====================================================================================
    // Showing the players what happened
    // =====================================================================================

    /** Shows the players every event that is ready, stopping at a pause. */
    private void deliverPending() {
        while (!pending.isEmpty() && !deliveryPaused) {
            deliver(pending.poll());
        }
    }

    void continueDelivery(long token) {
        if (token == deliveryToken) {
            deliveryPaused = false;
        }
    }

    private void deliver(GameEvent event) {
        if (event instanceof ActionRequested requested) {
            beginTurn(requested);
            return;
        }
        view.apply(event);
        recordForHistory(event);
        if (event instanceof PotAwarded) {
            return; // reported as part of HAND_ENDED
        }
        for (RoomMember member : members.values()) {
            Optional<ServerMessage> message = EventRouter.messageFor(event, seatInHand(member), handNo);
            message.ifPresent(member.controller::onEvent);
        }
        if (event instanceof HandCompleted completed) {
            finishHand(completed.result());
        } else if (event instanceof StreetDealt && timings.runOutPauseMs() > 0 && !pending.isEmpty()
                && !(pending.peek() instanceof ActionRequested)) {
            // Nobody acts on this street: everyone is all-in. Pause so the card can be seen.
            deliveryPaused = true;
            scheduler.schedule(new RoomCommands.ContinueDelivery(++deliveryToken), timings.runOutPauseMs());
        }
    }

    private void beginTurn(ActionRequested requested) {
        clearTurn();
        int seat = requested.seat();
        RoomMember member = seats[seat];
        long id = ++turnId;
        long turnMs = settings.turnSeconds() * 1_000L;
        currentTurn = new ActionRequest(seat, id, requested.legal(), clock.millis() + turnMs);
        if (member == null || !inCurrentHand(member) || member.autoAct) {
            actFor(seat);
            return;
        }
        turnTimer = scheduler.schedule(new RoomCommands.TurnTimeout(id), turnMs);
        for (RoomMember each : members.values()) {
            if (each == member) {
                each.controller.onActionRequested(currentTurn);
            } else {
                each.controller.onEvent(currentTurn.toMessage());
            }
        }
    }

    private void clearTurn() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
        currentTurn = null;
    }

    /** Notes blinds and actions as they are shown, for the hand's permanent record. */
    private void recordForHistory(GameEvent event) {
        if (event instanceof BlindPosted blind) {
            handActions.add(new HandRecord.ActionRecord(handActions.size() + 1, handRoster.get(blind.seat()).userId,
                    blind.seat(), Street.PREFLOP.name(), blind.bigBlind() ? "POST_BB" : "POST_SB", blind.amount(),
                    blind.amount(), blind.allIn()));
        } else if (event instanceof PlayerActed acted) {
            handActions.add(new HandRecord.ActionRecord(handActions.size() + 1, handRoster.get(acted.seat()).userId,
                    acted.seat(), acted.street().name(), acted.type().name(), acted.amount(), acted.streetBet(),
                    acted.allIn()));
        }
    }

    private HandRecord recordOf(HandResult result) {
        List<HandRecord.PlayerRecord> players = new ArrayList<>();
        for (Map.Entry<Integer, RoomMember> entry : handRoster.entrySet()) {
            int seat = entry.getKey();
            players.add(new HandRecord.PlayerRecord(entry.getValue().userId, entry.getValue().username, seat,
                    result.holeCards().get(seat), result.startStacks().get(seat), result.endStacks().get(seat),
                    result.net(seat), result.shownSeats().contains(seat), result.winners().contains(seat)));
        }
        return new HandRecord(code, settings.name(), handNo, settings.smallBlind(), settings.bigBlind(),
                view.buttonSeat(), result.board(), result.totalPot(), handStartedAt, clock.instant(), players,
                handActions);
    }

    private void finishHand(HandResult result) {
        HandRecord record = recordOf(result);
        List<RoomMember> changed = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : handPlayers.entrySet()) {
            RoomMember member = seats[entry.getKey()];
            if (member == null || member.userId != entry.getValue()) {
                continue; // left during the hand
            }
            member.stack = result.endStacks().get(entry.getKey());
            member.autoAct = false;
            if (member.stack == 0) {
                // Out of chips: they stay and watch, and may rebuy if the room allows it. Everyone is
                // told, even if the player was already sitting out, so no client misses the bust.
                member.sittingOut = true;
                changed.add(member);
            }
        }
        clearTurn();
        hand = null;
        view = null;
        handPlayers = Map.of();
        handRoster = Map.of();
        for (RoomMember member : changed) {
            broadcast(new SeatUpdate(infoOf(member)));
        }
        // Saving is someone else's job, on another thread: the room only hands the record over.
        listener.handFinished(record);
        scheduleNextHand(timings.betweenHandsMs());
    }

    // =====================================================================================
    // Messages
    // =====================================================================================

    /** Tells one user their request was refused. */
    void sendError(long userId, PokerException reason) {
        RoomMember member = members.get(userId);
        if (member != null) {
            member.controller.onEvent(new ErrorMessage(reason.code(), reason.getMessage()));
        }
    }

    private void broadcast(ServerMessage message) {
        for (RoomMember member : members.values()) {
            member.controller.onEvent(message);
        }
    }

    private void broadcastExcept(RoomMember excluded, ServerMessage message) {
        for (RoomMember member : members.values()) {
            if (member != excluded) {
                member.controller.onEvent(message);
            }
        }
    }

    private PlayerInfo infoOf(RoomMember member) {
        long stack = inCurrentHand(member) && view.hasSeat(member.seat) ? view.stack(member.seat) : member.stack;
        return new PlayerInfo(member.userId, member.username, member.seat, stack, member.sittingOut,
                member.connected);
    }

    private RoomSnapshot snapshotFor(RoomMember member) {
        List<PlayerInfo> players = new ArrayList<>();
        for (RoomMember each : members.values()) {
            players.add(infoOf(each));
        }
        HandInfo handInfo = null;
        if (hand != null) {
            handInfo = new HandInfo(handNo, view.buttonSeat(), view.smallBlindSeat(), view.bigBlindSeat(),
                    view.street(), view.board(), view.pots(), view.seatInfos(),
                    currentTurn == null ? null : currentTurn.toTurnInfo());
        }
        return new RoomSnapshot(code, settings.toInfo(), state, hostUserId, players, handInfo, member.userId,
                member.seat, inCurrentHand(member) ? view.holeCards(member.seat) : List.of());
    }

    // =====================================================================================
    // Small helpers
    // =====================================================================================

    private boolean inCurrentHand(RoomMember member) {
        Long playing = member.seated() ? handPlayers.get(member.seat) : null;
        return hand != null && playing != null && playing == member.userId;
    }

    /** The member's seat if they were dealt into the current hand, otherwise -1. */
    private int seatInHand(RoomMember member) {
        return inCurrentHand(member) ? member.seat : -1;
    }

    private void countSeats() {
        int count = 0;
        for (RoomMember seat : seats) {
            if (seat != null) {
                count++;
            }
        }
        seatedCount = count;
    }

    private RoomMember requireMember(long userId) throws NotInRoomException {
        RoomMember member = members.get(userId);
        if (member == null) {
            throw new NotInRoomException("You are not in this room. Join it with its code first.");
        }
        return member;
    }

    private RoomMember requireSeated(long userId) throws PokerException {
        RoomMember member = requireMember(userId);
        if (!member.seated()) {
            throw new InvalidRequestException("Take a seat first.");
        }
        return member;
    }

    private void requireHost(long userId, String what) throws PokerException {
        requireMember(userId);
        if (userId != hostUserId) {
            throw new NotHostException("Only the host can " + what + ".");
        }
    }
}
