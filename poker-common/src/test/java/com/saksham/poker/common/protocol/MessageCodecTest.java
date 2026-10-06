package com.saksham.poker.common.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.ProtocolException;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.AddBot;
import com.saksham.poker.common.protocol.client.Kick;
import com.saksham.poker.common.protocol.dto.BotLevel;
import com.saksham.poker.common.protocol.client.LeaveRoom;
import com.saksham.poker.common.protocol.client.PauseGame;
import com.saksham.poker.common.protocol.client.Ping;
import com.saksham.poker.common.protocol.client.Rebuy;
import com.saksham.poker.common.protocol.client.RequestSnapshot;
import com.saksham.poker.common.protocol.client.ResumeGame;
import com.saksham.poker.common.protocol.client.SendChat;
import com.saksham.poker.common.protocol.client.SitIn;
import com.saksham.poker.common.protocol.client.SitOut;
import com.saksham.poker.common.protocol.client.StartGame;
import com.saksham.poker.common.protocol.client.SubmitAction;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.dto.HandInfo;
import com.saksham.poker.common.protocol.dto.HandSeatInfo;
import com.saksham.poker.common.protocol.dto.LeaveReason;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
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
import com.saksham.poker.common.protocol.server.Pong;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    private static final RoomSettingsInfo SETTINGS =
            new RoomSettingsInfo("Friday game", 6, 50, 100, 10_000, 25, true);
    private static final PlayerInfo ASHA = new PlayerInfo(11, "asha", 2, 9_900, false, true);
    private static final TurnInfo TURN = new TurnInfo(2, 41, false, 100, false, true, 300, 9_900, 1_790_000_000_000L);

    /** One of every message a client can send. */
    private static List<ClientMessage> clientMessages() {
        return List.of(
                new JoinRoom("ABC234"),
                new TakeSeat(4),
                new LeaveRoom(),
                new StartGame(),
                new PauseGame(),
                new ResumeGame(),
                new Kick(17),
                new AddBot(BotLevel.EASY),
                new EndRoom(),
                new SubmitAction(41, ActionType.RAISE, 300),
                new SitOut(),
                new SitIn(),
                new Rebuy(),
                new SendChat("nice hand"),
                new RequestSnapshot(),
                new Ping());
    }

    /** One of every message the server can send. */
    private static List<ServerMessage> serverMessages() {
        HandInfo hand = new HandInfo(12, 1, 2, 3, "FLOP", Card.parseAll("2c 5d 9h"),
                List.of(new PotInfo(300, List.of(1, 2, 3))),
                List.of(new HandSeatInfo(1, 9_900, 0, false, false), new HandSeatInfo(2, 9_700, 200, false, false),
                        new HandSeatInfo(3, 0, 0, true, false)),
                TURN);
        return List.of(
                new RoomSnapshot("ABC234", SETTINGS, RoomState.PLAYING, 11,
                        List.of(ASHA, new PlayerInfo(12, "ravi", PlayerInfo.NO_SEAT, 0, false, true)),
                        hand, 11, 2, Card.parseAll("Ah Kd")),
                new PlayerJoined(ASHA),
                new PlayerLeft(12, LeaveReason.KICKED),
                new SeatUpdate(ASHA),
                new HostChanged(12),
                new GameState(RoomState.PAUSED),
                new HandStarted(12, 1, 2, 3, 50, 100, Map.of(1, 10_000L, 2, 9_900L, 3, 10_100L)),
                new BlindPosted(3, 100, true, false),
                new HoleCards(2, Card.parseAll("Ah Kd")),
                new ActionRequired(2, 41, false, 100, false, true, 300, 9_900, 1_790_000_000_000L),
                new PlayerActed(2, ActionType.RAISE, 300, 300, 9_600, false),
                new StreetDealt("TURN", Card.parseAll("Js"), Card.parseAll("2c 5d 9h Js")),
                new BetReturned(2, 200),
                new PotsUpdated(List.of(new PotInfo(1_200, List.of(1, 2, 3)), new PotInfo(800, List.of(2, 3)))),
                new Showdown(List.of(new ShownHandInfo(2, Card.parseAll("Ah Kd"), "PAIR"),
                        new ShownHandInfo(3, Card.parseAll("7c 7d"), "THREE_OF_A_KIND"))),
                new HandEnded(List.of(new PayoutInfo(0, 3, 1_200), new PayoutInfo(1, 3, 800)),
                        Map.of(1, -400L, 2, -800L, 3, 1_200L), Map.of(1, 9_600L, 2, 9_100L, 3, 11_300L)),
                new ChatPosted(11, "asha", "nice hand"),
                new ErrorMessage(ErrorCode.NOT_YOUR_TURN, "It is seat 3's turn, not yours."),
                new Pong());
    }

    // ---- the wire format

    @Test
    void aMessageIsAnEnvelopeOfTypeSeqAndPayload() throws Exception {
        assertThat(codec.encode(new JoinRoom("ABC234"), 7))
                .isEqualTo("{\"type\":\"JOIN_ROOM\",\"seq\":7,\"payload\":{\"code\":\"ABC234\"}}");
        assertThat(codec.encode(new Ping(), 8)).isEqualTo("{\"type\":\"PING\",\"seq\":8,\"payload\":{}}");
        assertThat(codec.encode(new SubmitAction(41, ActionType.RAISE, 300), 9)).isEqualTo(
                "{\"type\":\"ACTION\",\"seq\":9,\"payload\":{\"turnId\":41,\"action\":\"RAISE\",\"amount\":300}}");
    }

    @Test
    void cardsAreWrittenAsShortText() throws Exception {
        assertThat(codec.encode(new HoleCards(2, Card.parseAll("Ah Kd")), 1))
                .isEqualTo("{\"type\":\"HOLE_CARDS\",\"seq\":1,\"payload\":{\"seat\":2,\"cards\":[\"Ah\",\"Kd\"]}}");
    }

    @Test
    void seatNumbersAreMapKeys() throws Exception {
        String json = codec.encode(new HandStarted(12, 1, 2, 3, 50, 100, Map.of(3, 10_100L, 1, 10_000L)), 1);

        assertThat(json).contains("\"stacks\":{\"1\":10000,\"3\":10100}");
    }

    // ---- round trips

    @Test
    void everyClientMessageSurvivesARoundTrip() throws Exception {
        long seq = 100;
        for (ClientMessage message : clientMessages()) {
            String json = codec.encode(message, ++seq);

            Envelope<ClientMessage> decoded = codec.decodeClient(json);

            assertThat(decoded.seq()).as(json).isEqualTo(seq);
            assertThat(decoded.message()).as(json).isExactlyInstanceOf(message.getClass());
            assertThat(codec.encode(decoded.message(), seq)).isEqualTo(json);
        }
    }

    @Test
    void everyServerMessageSurvivesARoundTrip() throws Exception {
        long seq = 200;
        for (ServerMessage message : serverMessages()) {
            String json = codec.encode(message, ++seq);

            Envelope<ServerMessage> decoded = codec.decodeServer(json);

            assertThat(decoded.seq()).as(json).isEqualTo(seq);
            assertThat(decoded.message()).as(json).isExactlyInstanceOf(message.getClass());
            assertThat(codec.encode(decoded.message(), seq)).isEqualTo(json);
        }
    }

    @Test
    void theSamplesCoverEveryMessageInBothDirections() {
        assertThat(classesOf(clientMessages())).isEqualTo(registered(ClientMessage.class));
        assertThat(classesOf(serverMessages())).isEqualTo(registered(ServerMessage.class));
    }

    @Test
    void typeNamesAreUniqueWithinADirection() {
        for (Class<? extends Message> family : List.of(ClientMessage.class, ServerMessage.class)) {
            Set<String> names = new HashSet<>();
            for (Class<?> type : registered(family)) {
                String name = type.getAnnotation(JsonTypeName.class).value();
                assertThat(names.add(name)).as(name + " is used twice in " + family.getSimpleName()).isTrue();
            }
        }
    }

    @Test
    void fieldsComeBackWithTheirValues() throws Exception {
        SubmitAction action = (SubmitAction) codec
                .decodeClient(codec.encode(new SubmitAction(41, ActionType.RAISE, 300), 1)).message();
        assertThat(action.turnId()).isEqualTo(41);
        assertThat(action.action()).isEqualTo(ActionType.RAISE);
        assertThat(action.amount()).isEqualTo(300);

        RoomSnapshot snapshot = (RoomSnapshot) codec.decodeServer(codec.encode(serverMessages().get(0), 1)).message();
        assertThat(snapshot.code()).isEqualTo("ABC234");
        assertThat(snapshot.settings()).isEqualTo(SETTINGS);
        assertThat(snapshot.state()).isEqualTo(RoomState.PLAYING);
        assertThat(snapshot.players()).hasSize(2).first().isEqualTo(ASHA);
        assertThat(snapshot.players().get(1).seated()).isFalse();
        assertThat(snapshot.hand().board()).isEqualTo(Card.parseAll("2c 5d 9h"));
        assertThat(snapshot.hand().pots()).containsExactly(new PotInfo(300, List.of(1, 2, 3)));
        assertThat(snapshot.hand().seats()).hasSize(3);
        assertThat(snapshot.hand().turn()).isEqualTo(TURN);
        assertThat(snapshot.yourSeat()).isEqualTo(2);
        assertThat(snapshot.yourCards()).isEqualTo(Card.parseAll("Ah Kd"));

        HandEnded ended = (HandEnded) codec.decodeServer(codec.encode(serverMessages().get(15), 1)).message();
        assertThat(ended.netBySeat()).isEqualTo(Map.of(1, -400L, 2, -800L, 3, 1_200L));
        assertThat(ended.payouts()).containsExactly(new PayoutInfo(0, 3, 1_200), new PayoutInfo(1, 3, 800));
    }

    @Test
    void aSnapshotBetweenHandsHasNoHand() throws Exception {
        RoomSnapshot waiting = new RoomSnapshot("ABC234", SETTINGS, RoomState.WAITING, 11, List.of(ASHA),
                null, 11, 2, List.of());

        RoomSnapshot decoded = (RoomSnapshot) codec.decodeServer(codec.encode(waiting, 1)).message();

        assertThat(decoded.hand()).isNull();
        assertThat(decoded.yourCards()).isEmpty();
    }

    @Test
    void everyErrorCodeCanBeSent() throws Exception {
        for (ErrorCode code : ErrorCode.values()) {
            ErrorMessage decoded = (ErrorMessage) codec
                    .decodeServer(codec.encode(new ErrorMessage(code, "m"), 1)).message();
            assertThat(decoded.code()).isEqualTo(code);
            assertThat(decoded.message()).isEqualTo("m");
        }
    }

    @Test
    void messagesKnowTheirTypeName() {
        assertThat(new JoinRoom("ABC234").type()).isEqualTo("JOIN_ROOM");
        assertThat(new SendChat("hi").type()).isEqualTo("CHAT");
        assertThat(new ChatPosted(1, "asha", "hi").type()).isEqualTo("CHAT");
        assertThat(new Pong()).hasToString("PONG");
    }

    // ---- reading what others send

    @Test
    void theSeqAndAnEmptyPayloadMayBeLeftOut() throws Exception {
        Envelope<ClientMessage> ping = codec.decodeClient("{\"type\":\"PING\"}");

        assertThat(ping.message()).isInstanceOf(Ping.class);
        assertThat(ping.seq()).isZero();
        assertThat(codec.decodeClient("{\"type\":\"PING\",\"payload\":null}").message()).isInstanceOf(Ping.class);
    }

    @Test
    void fieldsAddedByANewerVersionAreIgnored() throws Exception {
        Envelope<ClientMessage> decoded = codec.decodeClient(
                "{\"type\":\"JOIN_ROOM\",\"seq\":3,\"payload\":{\"code\":\"ABC234\",\"theme\":\"dark\"},\"extra\":1}");

        assertThat(((JoinRoom) decoded.message()).code()).isEqualTo("ABC234");
    }

    // ---- refusing what cannot be read

    @Test
    void anUnknownTypeIsRefused() {
        assertMalformed(() -> codec.decodeClient("{\"type\":\"DEAL_ME_ACES\",\"seq\":1,\"payload\":{}}"));
    }

    @Test
    void aMessageFromTheWrongDirectionIsRefused() throws Exception {
        String pong = codec.encode(new Pong(), 1);
        String join = codec.encode(new JoinRoom("ABC234"), 1);

        assertMalformed(() -> codec.decodeClient(pong));
        assertMalformed(() -> codec.decodeServer(join));
    }

    @Test
    void chatMeansSomethingDifferentInEachDirection() throws Exception {
        assertThat(codec.decodeClient(codec.encode(new SendChat("hi"), 1)).message()).isInstanceOf(SendChat.class);
        assertThat(codec.decodeServer(codec.encode(new ChatPosted(1, "asha", "hi"), 1)).message())
                .isInstanceOf(ChatPosted.class);
        // The server's CHAT needs a sender, so a client's CHAT is not a valid one.
        assertMalformed(() -> codec.decodeServer(codec.encode(new SendChat("hi"), 1)));
    }

    @Test
    void textThatIsNotAJsonObjectIsRefused() {
        for (String bad : List.of("", "   ", "hello", "{", "[1,2]", "42", "null", "\"JOIN_ROOM\"",
                "{\"type\":\"PING\"} trailing")) {
            assertMalformed(() -> codec.decodeClient(bad));
        }
        assertMalformed(() -> codec.decodeClient(null));
    }

    @Test
    void aBrokenEnvelopeIsRefused() {
        assertMalformed(() -> codec.decodeClient("{\"seq\":1,\"payload\":{}}"));
        assertMalformed(() -> codec.decodeClient("{\"type\":7}"));
        assertMalformed(() -> codec.decodeClient("{\"type\":\"PING\",\"seq\":\"soon\"}"));
        assertMalformed(() -> codec.decodeClient("{\"type\":\"PING\",\"seq\":1.5}"));
        assertMalformed(() -> codec.decodeClient("{\"type\":\"PING\",\"payload\":[1]}"));
    }

    @Test
    void aMissingOrWronglyTypedFieldIsRefused() {
        assertMalformed(() -> codec.decodeClient("{\"type\":\"JOIN_ROOM\",\"payload\":{}}"));
        assertMalformed(() -> codec.decodeClient("{\"type\":\"TAKE_SEAT\",\"payload\":{\"seat\":\"window\"}}"));
        assertMalformed(() -> codec.decodeClient("{\"type\":\"TAKE_SEAT\",\"payload\":{\"seat\":null}}"));
        assertMalformed(() -> codec.decodeClient(
                "{\"type\":\"ACTION\",\"payload\":{\"turnId\":1,\"action\":\"BLUFF\",\"amount\":0}}"));
        assertMalformed(() -> codec.decodeServer(
                "{\"type\":\"HOLE_CARDS\",\"payload\":{\"seat\":1,\"cards\":[\"Ah\",\"Zz\"]}}"));
    }

    @Test
    void aMessageOverEightKilobytesIsRefusedBothWays() {
        String longText = "x".repeat(MessageCodec.MAX_BYTES);

        assertMalformed(() -> codec.encode(new SendChat(longText), 1));
        assertMalformed(() -> codec.decodeClient(
                "{\"type\":\"CHAT\",\"seq\":1,\"payload\":{\"text\":\"" + longText + "\"}}"));
    }

    @Test
    void theSizeLimitCountsBytesNotCharacters() {
        // Each of these characters takes three bytes in UTF-8.
        String text = "₹".repeat(MessageCodec.MAX_BYTES / 3);

        assertThat(text.length()).isLessThan(MessageCodec.MAX_BYTES);
        assertMalformed(() -> codec.encode(new SendChat(text), 1));
    }

    @Test
    void theBiggestRealisticSnapshotFitsInTheLimit() throws Exception {
        List<PlayerInfo> players = new ArrayList<>();
        List<HandSeatInfo> seats = new ArrayList<>();
        List<PotInfo> pots = new ArrayList<>();
        List<Integer> everySeat = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        for (int seat : everySeat) {
            players.add(new PlayerInfo(1_000_000_000L + seat, "a_username_of_24_chars_" + seat,
                    seat, 999_999_999_999L, false, true));
            seats.add(new HandSeatInfo(seat, 999_999_999_999L, 999_999_999_999L, false, true));
            pots.add(new PotInfo(999_999_999_999L, everySeat.subList(seat, 9)));
        }
        HandInfo hand = new HandInfo(999_999, 8, 0, 1, "SHOWDOWN", Card.parseAll("2c 5d 9h Js 3s"), pots, seats,
                new TurnInfo(8, 999_999_999L, false, 999_999_999_999L, false, true, 999_999_999_999L,
                        999_999_999_999L, 1_790_000_000_000L));
        RoomSnapshot snapshot = new RoomSnapshot("ABC234",
                new RoomSettingsInfo("A room name that is forty characters ..", 9, 500_000, 1_000_000,
                        999_999_999_999L, 60, true),
                RoomState.PLAYING, 1_000_000_000L, players, hand, 1_000_000_008L, 8, Card.parseAll("Ah Kd"));

        String json = codec.encode(snapshot, 999_999_999L);

        assertThat(json.length()).isLessThan(MessageCodec.MAX_BYTES / 2);
    }

    // ---- helpers

    @FunctionalInterface
    private interface Decoding {
        void run() throws ProtocolException;
    }

    private static void assertMalformed(Decoding decoding) {
        assertThatThrownBy(decoding::run)
                .isInstanceOf(ProtocolException.class)
                .extracting(e -> ((ProtocolException) e).code())
                .isEqualTo(ErrorCode.MALFORMED_MESSAGE);
    }

    private static Set<Class<?>> classesOf(List<? extends Message> messages) {
        Set<Class<?>> classes = new HashSet<>();
        for (Message message : messages) {
            classes.add(message.getClass());
        }
        return classes;
    }

    private static Set<Class<?>> registered(Class<? extends Message> family) {
        Set<Class<?>> classes = new HashSet<>();
        for (JsonSubTypes.Type type : family.getAnnotation(JsonSubTypes.class).value()) {
            classes.add(type.value());
        }
        return classes;
    }
}
