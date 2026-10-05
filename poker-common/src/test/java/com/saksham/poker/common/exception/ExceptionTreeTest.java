package com.saksham.poker.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.error.ErrorCode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExceptionTreeTest {

    /** Every checked exception, with the code it must report. */
    private static Map<PokerException, ErrorCode> checked() {
        Map<PokerException, ErrorCode> map = new LinkedHashMap<>();
        map.put(new InvalidActionException("m"), ErrorCode.INVALID_ACTION);
        map.put(new NotYourTurnException("m"), ErrorCode.NOT_YOUR_TURN);
        map.put(new InvalidAmountException("m"), ErrorCode.INVALID_AMOUNT);
        map.put(new RoomNotFoundException("m"), ErrorCode.ROOM_NOT_FOUND);
        map.put(new RoomFullException("m"), ErrorCode.ROOM_FULL);
        map.put(new RoomClosedException("m"), ErrorCode.ROOM_CLOSED);
        map.put(new SeatTakenException("m"), ErrorCode.SEAT_TAKEN);
        map.put(new NotHostException("m"), ErrorCode.NOT_HOST);
        map.put(new GameAlreadyStartedException("m"), ErrorCode.GAME_ALREADY_STARTED);
        map.put(new NotEnoughPlayersException("m"), ErrorCode.NOT_ENOUGH_PLAYERS);
        map.put(new RebuyNotAllowedException("m"), ErrorCode.REBUY_NOT_ALLOWED);
        map.put(new NotInRoomException("m"), ErrorCode.NOT_IN_ROOM);
        map.put(new AlreadyInRoomException("m"), ErrorCode.ALREADY_IN_ROOM);
        map.put(new InvalidCredentialsException("m"), ErrorCode.INVALID_CREDENTIALS);
        map.put(new UsernameTakenException("m"), ErrorCode.USERNAME_TAKEN);
        map.put(new UnauthorizedException("m"), ErrorCode.UNAUTHORIZED);
        map.put(new ProtocolException("m"), ErrorCode.MALFORMED_MESSAGE);
        map.put(new InvalidRequestException("m"), ErrorCode.INVALID_REQUEST);
        return map;
    }

    @Test
    void everyCheckedExceptionReportsItsCodeAndMessage() {
        checked().forEach((exception, code) -> {
            assertThat(exception.code()).as(exception.getClass().getSimpleName()).isEqualTo(code);
            assertThat(exception).hasMessage("m");
        });
    }

    @Test
    void exceptionsAreGroupedUnderTheirParents() {
        assertThat(new InvalidActionException("m")).isInstanceOf(GameRuleException.class);
        assertThat(new NotYourTurnException("m")).isInstanceOf(GameRuleException.class);
        assertThat(new InvalidAmountException("m")).isInstanceOf(GameRuleException.class);
        assertThat(new RoomFullException("m")).isInstanceOf(RoomException.class);
        assertThat(new AlreadyInRoomException("m")).isInstanceOf(RoomException.class);
        assertThat(new UsernameTakenException("m")).isInstanceOf(AuthException.class);
        assertThat(new UnauthorizedException("m")).isInstanceOf(AuthException.class);
        assertThat(new ProtocolException("m")).isInstanceOf(PokerException.class);
    }

    @Test
    void storageAndDatabaseFailuresAreUncheckedAndInternal() {
        Throwable cause = new java.io.IOException("disk");
        PersistenceException persistence = new PersistenceException("m", cause);
        StorageException storage = new StorageException("m", cause);

        assertThat(persistence).isInstanceOf(RuntimeException.class).hasCause(cause);
        assertThat(storage).isInstanceOf(RuntimeException.class).hasCause(cause);
        assertThat(persistence.code()).isEqualTo(ErrorCode.INTERNAL);
        assertThat(storage.code()).isEqualTo(ErrorCode.INTERNAL);
    }

    @Test
    void everyErrorCodeHasAnException() {
        Set<ErrorCode> covered = EnumSet.of(ErrorCode.INTERNAL);
        covered.addAll(checked().values());

        assertThat(covered).containsExactlyInAnyOrder(ErrorCode.values());
    }
}
