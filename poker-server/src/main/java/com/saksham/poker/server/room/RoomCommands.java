package com.saksham.poker.server.room;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.server.player.SeatController;

/**
 * Every command a room understands (SPEC §4.3). Each is a small {@link RoomCommand} that calls one
 * method on the room; the room's thread runs them one at a time, in the order they were queued.
 */
public final class RoomCommands {

    private RoomCommands() {
    }

    /** A user enters the room. */
    public static final class JoinRoom extends RoomCommand {

        private final long userId;
        private final String username;
        private final SeatController controller;

        public JoinRoom(long userId, String username, SeatController controller) {
            this.userId = userId;
            this.username = username;
            this.controller = controller;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.join(userId, username, controller);
        }

        @Override
        public long userId() {
            return userId;
        }

        /** The user may not be a member, so the refusal goes straight to their connection. */
        @Override
        public void failed(Room room, PokerException reason) {
            controller.onEvent(new ErrorMessage(reason.code(), reason.getMessage()));
            room.joinRefused(userId);
        }
    }

    /** A member sits down at a seat. */
    public static final class TakeSeat extends RoomCommand {

        private final long userId;
        private final int seat;

        public TakeSeat(long userId, int seat) {
            this.userId = userId;
            this.seat = seat;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.takeSeat(userId, seat);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A member leaves; in the middle of a hand this folds it. */
    public static final class LeaveRoom extends RoomCommand {

        private final long userId;

        public LeaveRoom(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.leave(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** The host starts dealing. */
    public static final class StartGame extends RoomCommand {

        private final long userId;

        public StartGame(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.startGame(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** The host stops new hands from starting. */
    public static final class PauseGame extends RoomCommand {

        private final long userId;

        public PauseGame(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.pauseGame(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** The host starts dealing again. */
    public static final class ResumeGame extends RoomCommand {

        private final long userId;

        public ResumeGame(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.resumeGame(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** The host removes a player before the game starts. */
    public static final class KickPlayer extends RoomCommand {

        private final long userId;
        private final long targetUserId;

        public KickPlayer(long userId, long targetUserId) {
            this.userId = userId;
            this.targetUserId = targetUserId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.kick(userId, targetUserId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** The host closes the room. */
    public static final class EndRoom extends RoomCommand {

        private final long userId;

        public EndRoom(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.endRoom(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A player's answer to their turn. */
    public static final class PlayerActionCmd extends RoomCommand {

        private final long userId;
        private final long turnId;
        private final ActionType action;
        private final long amount;

        public PlayerActionCmd(long userId, long turnId, ActionType action, long amount) {
            this.userId = userId;
            this.turnId = turnId;
            this.action = action;
            this.amount = amount;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.playerAction(userId, turnId, action, amount);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A player stops being dealt in from the next hand. */
    public static final class SitOut extends RoomCommand {

        private final long userId;

        public SitOut(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.sitOut(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A player asks to be dealt in again. */
    public static final class SitIn extends RoomCommand {

        private final long userId;

        public SitIn(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.sitIn(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A player with no chips buys back to the starting stack. */
    public static final class Rebuy extends RoomCommand {

        private final long userId;

        public Rebuy(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.rebuy(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A member says something to the room. */
    public static final class Chat extends RoomCommand {

        private final long userId;
        private final String text;

        public Chat(long userId, String text) {
            this.userId = userId;
            this.text = text;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.chat(userId, text);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A member asks for the whole room state again. */
    public static final class SendSnapshot extends RoomCommand {

        private final long userId;

        public SendSnapshot(long userId) {
            this.userId = userId;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.sendSnapshot(userId);
        }

        @Override
        public long userId() {
            return userId;
        }
    }

    /** A member's connection was lost. */
    public static final class Disconnected extends RoomCommand {

        private final long lostUserId;

        public Disconnected(long lostUserId) {
            this.lostUserId = lostUserId;
        }

        @Override
        public void execute(Room room) {
            room.disconnected(lostUserId);
        }
    }

    /** A member connected again and gets a fresh snapshot. */
    public static final class Reconnected extends RoomCommand {

        private final long userId;
        private final SeatController controller;

        public Reconnected(long userId, SeatController controller) {
            this.userId = userId;
            this.controller = controller;
        }

        @Override
        public void execute(Room room) throws PokerException {
            room.reconnected(userId, controller);
        }

        @Override
        public long userId() {
            return userId;
        }

        /** The user may not be a member, so the refusal goes straight to their connection. */
        @Override
        public void failed(Room room, PokerException reason) {
            controller.onEvent(new ErrorMessage(reason.code(), reason.getMessage()));
        }
    }

    /** A turn's time ran out. Ignored if that turn has already been answered. */
    public static final class TurnTimeout extends RoomCommand {

        private final long turnId;

        public TurnTimeout(long turnId) {
            this.turnId = turnId;
        }

        @Override
        public void execute(Room room) {
            room.turnTimeout(turnId);
        }
    }

    /** A disconnected member did not come back in time. */
    public static final class ReconnectGraceExpired extends RoomCommand {

        private final long lostUserId;
        private final long generation;

        public ReconnectGraceExpired(long lostUserId, long generation) {
            this.lostUserId = lostUserId;
            this.generation = generation;
        }

        @Override
        public void execute(Room room) {
            room.reconnectGraceExpired(lostUserId, generation);
        }
    }

    /** The pause between hands is over. */
    public static final class StartNextHand extends RoomCommand {

        public StartNextHand() {
        }

        @Override
        public void execute(Room room) {
            room.startNextHand();
        }
    }

    /** The pause before the next run-out card is over. */
    public static final class ContinueDelivery extends RoomCommand {

        private final long token;

        public ContinueDelivery(long token) {
            this.token = token;
        }

        @Override
        public void execute(Room room) {
            room.continueDelivery(token);
        }
    }

    /** Closes the room if nobody has been connected since this was scheduled. */
    public static final class IdleCheck extends RoomCommand {

        private final long generation;

        public IdleCheck(long generation) {
            this.generation = generation;
        }

        @Override
        public void execute(Room room) {
            room.idleCheck(generation);
        }
    }
}
