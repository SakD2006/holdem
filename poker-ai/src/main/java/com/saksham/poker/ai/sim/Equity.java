package com.saksham.poker.ai.sim;

import java.util.List;
import java.util.Random;

/**
 * Works out a hand's chance of winning by Monte Carlo simulation: give each opponent a hand they
 * could plausibly hold, deal the rest of the board, see who wins, and repeat. The share of deals
 * won is the answer. More deals give a steadier answer; a few hundred is within a few percent.
 */
public final class Equity {

    /** How many times a hand that does not fit an opponent's range is thrown back before giving up. */
    private static final int MAX_REDRAWS = 30;

    private Equity() {
    }

    /**
     * @param hole the player's two cards, as card numbers
     * @param board the community cards so far: none, three, four or five
     * @param opponents one range for each opponent still in the hand
     * @param deals how many times to deal it out
     * @param random the source of chance
     * @return the expected share of the pot, 0 to 1; a tie counts as an equal share
     */
    public static double winChance(int[] hole, int[] board, List<Range> opponents, int deals, Random random) {
        int players = opponents.size();
        boolean[] used = new boolean[FastHand.CARDS];
        int[] mine = new int[7];
        int[] theirs = new int[7];
        int[][] held = new int[players][2];
        mine[0] = hole[0];
        mine[1] = hole[1];
        double won = 0;
        for (int deal = 0; deal < deals; deal++) {
            java.util.Arrays.fill(used, false);
            used[hole[0]] = true;
            used[hole[1]] = true;
            for (int card : board) {
                used[card] = true;
            }
            for (int p = 0; p < players; p++) {
                dealTo(held[p], opponents.get(p), board, used, random);
            }
            for (int i = 0; i < 5; i++) {
                int card = i < board.length ? board[i] : draw(used, random);
                mine[2 + i] = card;
                theirs[2 + i] = card;
            }
            int ours = FastHand.score(mine, 7);
            int ahead = 0;
            int level = 0;
            for (int p = 0; p < players; p++) {
                theirs[0] = held[p][0];
                theirs[1] = held[p][1];
                int other = FastHand.score(theirs, 7);
                if (other > ours) {
                    ahead++;
                    break;
                }
                if (other == ours) {
                    level++;
                }
            }
            if (ahead == 0) {
                won += 1.0 / (level + 1);
            }
        }
        return won / deals;
    }

    /** Gives an opponent two cards that fit their range, as far as a few tries allow. */
    private static void dealTo(int[] hand, Range range, int[] board, boolean[] used, Random random) {
        int a = 0;
        int b = 0;
        for (int tries = 0; tries <= MAX_REDRAWS; tries++) {
            a = draw(used, random);
            b = draw(used, random);
            boolean last = tries == MAX_REDRAWS;
            if (last || fits(a, b, range, board, random)) {
                break;
            }
            used[a] = false;
            used[b] = false;
        }
        hand[0] = a;
        hand[1] = b;
    }

    private static boolean fits(int a, int b, Range range, int[] board, Random random) {
        if (PreflopTable.place(a, b) > range.width()) {
            return false;
        }
        if (range.connected() <= 0 || board.length < 3) {
            return true;
        }
        // A player showing strength usually has something; let the rest through only now and then.
        return hitsBoard(a, b, board) || random.nextDouble() > range.connected();
    }

    /** True for a pair or better made with a hole card, or four cards to a flush or straight. */
    static boolean hitsBoard(int a, int b, int[] board) {
        int ra = FastHand.rank(a);
        int rb = FastHand.rank(b);
        if (ra == rb) {
            return true;
        }
        int ranks = 1 << ra | 1 << rb;
        int[] suits = new int[4];
        suits[FastHand.suit(a)]++;
        suits[FastHand.suit(b)]++;
        for (int card : board) {
            int rank = FastHand.rank(card);
            if (rank == ra || rank == rb) {
                return true;
            }
            ranks |= 1 << rank;
            suits[FastHand.suit(card)]++;
        }
        if (board.length < 5) {
            if (suits[FastHand.suit(a)] >= 4 || suits[FastHand.suit(b)] >= 4) {
                return true;
            }
            int withLowAce = ranks << 1 | ranks >>> 12 & 1;
            for (int low = 0; low <= 9; low++) {
                if (Integer.bitCount(withLowAce >>> low & 0x1F) >= 4) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int draw(boolean[] used, Random random) {
        while (true) {
            int card = random.nextInt(FastHand.CARDS);
            if (!used[card]) {
                used[card] = true;
                return card;
            }
        }
    }
}
