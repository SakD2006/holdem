package com.saksham.poker.ai.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Java port agrees with the trainer: every example in {@code blueprint-fixtures.txt}, written by
 * {@code trainer fixtures}, is worked out again here and must come out the same, bit for bit.
 */
class TrainerFixturesTest {

    static List<String[]> lines(String kind) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (var in = TrainerFixturesTest.class.getResourceAsStream("/blueprint-fixtures.txt");
                var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(" ");
                if (fields[0].equals(kind)) {
                    out.add(fields);
                }
            }
        }
        return out;
    }

    static int[] ints(String list) {
        return list.equals("-") ? new int[0] : Arrays.stream(list.split(",")).mapToInt(Integer::parseInt).toArray();
    }

    @Test
    void starting_hands_canonical_keys_and_river_strength_match() throws IOException {
        List<String[]> deals = lines("deal");
        assertThat(deals).hasSize(300);
        for (String[] f : deals) {
            int[] c = new int[7];
            for (int i = 0; i < 7; i++) {
                c[i] = Integer.parseInt(f[1 + i]);
            }
            int[] board = Arrays.copyOfRange(c, 2, 7);
            assertThat(Cards.preflopClass(c[0], c[1])).isEqualTo(Integer.parseInt(f[8]));
            assertThat(Canon.key(c[0], c[1], board, 3)).isEqualTo(Long.parseUnsignedLong(f[9]));
            assertThat(Canon.key(c[0], c[1], board, 4)).isEqualTo(Long.parseUnsignedLong(f[10]));
            assertThat(Float.floatToRawIntBits(Cards.riverStrength(c[0], c[1], board)))
                    .isEqualTo(Integer.parseUnsignedInt(f[11], 16));
        }
    }

    @Test
    void every_betting_state_matches() throws IOException {
        List<String[]> states = lines("state");
        assertThat(states).hasSizeGreaterThan(1000);
        for (String[] f : states) {
            AbstractHand hand = AbstractHand.start();
            for (int action : ints(f[1])) {
                hand = hand.apply(action);
            }
            String where = "after " + f[1];
            assertThat(hand.street()).as(where).isEqualTo(Integer.parseInt(f[2]));
            assertThat(hand.toAct()).as(where).isEqualTo(Integer.parseInt(f[3]));
            assertThat(hand.pot()).as(where).isEqualTo(Long.parseLong(f[4]));
            assertThat(hand.toCall(hand.toAct())).as(where).isEqualTo(Long.parseLong(f[5]));
            int[] legal = hand.legalActions();
            assertThat(legal).as(where).containsExactly(ints(f[6]));
            int[] raiseTo = ints(f[7]);
            for (int i = 0; i < legal.length; i++) {
                long expected = raiseTo[i];
                long actual = legal[i] == AbstractHand.FOLD || legal[i] == AbstractHand.CALL ? 0 : hand.raiseTo(legal[i]);
                assertThat(actual).as(where + ", action " + legal[i]).isEqualTo(expected);
            }
            assertThat(hand.key(0)).as(where).isEqualTo(Long.parseUnsignedLong(f[8]));
            assertThat(hand.key(37)).as(where).isEqualTo(Long.parseUnsignedLong(f[9]));
        }
    }

    @Test
    void a_river_solved_from_uniform_ranges_matches() throws IOException {
        String[] solve = lines("solve").get(0);
        int[] board = new int[5];
        for (int i = 0; i < 5; i++) {
            board[i] = Integer.parseInt(solve[1 + i]);
        }
        int iterations = Integer.parseInt(solve[6]);
        long used = 0;
        for (int c : board) {
            used |= 1L << c;
        }
        double[] range = new double[Cards.COMBOS];
        for (int i = 0; i < Cards.COMBOS; i++) {
            int[] hole = Cards.combo(i);
            range[i] = (used & (1L << hole[0] | 1L << hole[1])) == 0 ? 1 : 0;
        }
        AbstractHand root = AbstractHand.start();
        while (root.street() < 3) {
            root = root.apply(AbstractHand.CALL);
        }
        RiverSolver.Solution solution = RiverSolver.solve(root, board, new double[][] {range, range}, iterations);
        double expected = Double.longBitsToDouble(Long.parseUnsignedLong(solve[7], 16));
        // Only floating-point rounding (Java's and Rust's pow may differ in the last bit) separates them.
        assertThat(solution.exploitability(new double[][] {range, range}))
                .isCloseTo(expected, org.assertj.core.api.Assertions.within(1e-9));
        List<String[]> probs = lines("probs");
        assertThat(probs).isNotEmpty();
        for (String[] f : probs) {
            AbstractHand state = AbstractHand.start();
            for (int action : ints(f[1])) {
                state = state.apply(action);
            }
            double[] mine = solution.probabilities(state, Integer.parseInt(f[2]));
            String[] bits = f[3].split(",");
            assertThat(mine).as("after %s, hand %s", f[1], f[2]).hasSize(bits.length);
            for (int a = 0; a < bits.length; a++) {
                double theirs = Double.longBitsToDouble(Long.parseUnsignedLong(bits[a], 16));
                assertThat(mine[a]).as("after %s, hand %s, action %d", f[1], f[2], a)
                        .isCloseTo(theirs, org.assertj.core.api.Assertions.within(1e-9));
            }
        }
    }

    @Test
    void every_pair_of_cards_has_its_own_number() {
        boolean[] seen = new boolean[Cards.COMBOS];
        for (int b = 1; b < 52; b++) {
            for (int a = 0; a < b; a++) {
                int i = Cards.comboIndex(a, b);
                assertThat(seen[i]).isFalse();
                seen[i] = true;
                assertThat(Cards.combo(i)).containsExactly(a, b);
            }
        }
    }
}
