package com.saksham.poker.server.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Who may see whose hole cards in a finished hand. */
class StoredHandTest {

    private static final long ASHA = 1;
    private static final long RAVI = 2;
    private static final long MEERA = 3;

    /** Asha folded, so her cards were never shown; Ravi and Meera went to showdown. */
    private static final StoredHand HAND = new StoredHand(7, "ABC234", "Friday game", 12, 0,
            Card.parseAll("2c 5d 9h Js 3s"), 600, 1_000, 2_000,
            List.of(new HandPlayerInfo(0, ASHA, "asha", Card.parseAll("7c 2d"), 10_000, 10_000, 0, false, false),
                    new HandPlayerInfo(1, RAVI, "ravi", Card.parseAll("Kh Kd"), 10_000, 9_700, -300, true, false),
                    new HandPlayerInfo(2, MEERA, "meera", Card.parseAll("Ah Ad"), 10_000, 10_300, 300, true, true)),
            List.of(new HandActionInfo(1, 0, "asha", "PREFLOP", "FOLD", 0)));

    private static List<Card> cardsOf(HandDetail detail, int seat) {
        return detail.players().get(seat).holeCards();
    }

    @Test
    void aPlayerSeesTheirOwnFoldedCards() {
        HandDetail forAsha = HAND.viewFor(ASHA);

        assertThat(cardsOf(forAsha, 0)).isEqualTo(Card.parseAll("7c 2d"));
    }

    @Test
    void nobodyElseSeesAFoldedPlayersCards() {
        assertThat(cardsOf(HAND.viewFor(RAVI), 0)).isEmpty();
        assertThat(cardsOf(HAND.viewFor(MEERA), 0)).isEmpty();
        assertThat(cardsOf(HAND.viewFor(99), 0)).isEmpty();
        assertThat(cardsOf(HAND.viewFor(-1), 0)).isEmpty();
    }

    @Test
    void cardsShownAtShowdownAreVisibleToEveryone() {
        for (long viewer : new long[] {ASHA, RAVI, MEERA, 99, -1}) {
            HandDetail detail = HAND.viewFor(viewer);
            assertThat(cardsOf(detail, 1)).as("viewer " + viewer).isEqualTo(Card.parseAll("Kh Kd"));
            assertThat(cardsOf(detail, 2)).as("viewer " + viewer).isEqualTo(Card.parseAll("Ah Ad"));
        }
    }

    @Test
    void everythingButHiddenCardsIsTheSameForEveryViewer() {
        HandDetail forRavi = HAND.viewFor(RAVI);

        assertThat(forRavi.id()).isEqualTo(7);
        assertThat(forRavi.roomCode()).isEqualTo("ABC234");
        assertThat(forRavi.handNo()).isEqualTo(12);
        assertThat(forRavi.board()).isEqualTo(Card.parseAll("2c 5d 9h Js 3s"));
        assertThat(forRavi.totalPot()).isEqualTo(600);
        assertThat(forRavi.actions()).isEqualTo(HAND.actions());
        HandPlayerInfo asha = forRavi.players().get(0);
        assertThat(asha.username()).isEqualTo("asha");
        assertThat(asha.net()).isZero();
        assertThat(asha.showedDown()).isFalse();
        assertThat(asha.startStack()).isEqualTo(10_000);
    }

    @Test
    void theStoredHandItselfIsNotChanged() {
        HAND.viewFor(RAVI);

        assertThat(HAND.players().get(0).holeCards()).isEqualTo(Card.parseAll("7c 2d"));
    }
}
