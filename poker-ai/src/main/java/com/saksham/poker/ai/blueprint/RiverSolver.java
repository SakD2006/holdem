package com.saksham.poker.ai.blueprint;

import com.saksham.poker.ai.sim.FastHand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * River re-solving (`trainer/src/holdem/resolve.rs`): when the river card is dealt, solve the rest
 * of the hand for every pair of hole cards both players could hold, instead of playing the
 * blueprint's river buckets.
 *
 * <p>Discounted CFR (alpha 1.5, beta 0, gamma 2) over all hands at once: every decision keeps
 * regrets for each hand, a fold is valued for all hands from the opponent weight on hands that share
 * no card, and a showdown by one sweep through the hands in order of strength. The ranges come from
 * the blueprint's play so far; this is unsafe re-solving, which trusts the opponent's range.
 */
public final class RiverSolver {

    private RiverSolver() {
    }

    /** The hands of a subgame: every pair of hole cards that misses the board and is kept. */
    private static final class Hands {
        final int[] combo;
        final int[][] cards;
        final int[] scores;
        final int[] ascending;
        final int[] descending;

        Hands(int[] board, java.util.function.IntPredicate keep) {
            long used = 0;
            for (int c : board) {
                used |= 1L << c;
            }
            List<Integer> kept = new ArrayList<>();
            for (int i = 0; i < Cards.COMBOS; i++) {
                int[] hole = Cards.combo(i);
                if ((used & (1L << hole[0] | 1L << hole[1])) == 0 && keep.test(i)) {
                    kept.add(i);
                }
            }
            int n = kept.size();
            combo = new int[n];
            cards = new int[n][];
            scores = new int[n];
            int[] seven = {0, 0, board[0], board[1], board[2], board[3], board[4]};
            for (int h = 0; h < n; h++) {
                combo[h] = kept.get(h);
                cards[h] = Cards.combo(combo[h]);
                seven[0] = cards[h][0];
                seven[1] = cards[h][1];
                scores[h] = FastHand.score(seven, 7);
            }
            Integer[] order = new Integer[n];
            for (int h = 0; h < n; h++) {
                order[h] = h;
            }
            Arrays.sort(order, (a, b) -> Integer.compare(scores[a], scores[b])); // stable, like Rust's sort_by_key
            ascending = new int[n];
            descending = new int[n];
            for (int h = 0; h < n; h++) {
                ascending[h] = order[h];
                descending[n - 1 - h] = order[h];
            }
        }

        int size() {
            return combo.length;
        }

        /** For each hand, the opponent weight on hands that share no card with it. */
        double[] compatible(double[] opponent) {
            double total = 0;
            for (double w : opponent) {
                total += w;
            }
            double[] perCard = new double[52];
            for (int h = 0; h < size(); h++) {
                perCard[cards[h][0]] += opponent[h];
                perCard[cards[h][1]] += opponent[h];
            }
            double[] out = new double[size()];
            for (int h = 0; h < size(); h++) {
                out[h] = total - perCard[cards[h][0]] - perCard[cards[h][1]] + opponent[h];
            }
            return out;
        }

        /** For each hand, opponent weight it beats minus weight that beats it, card removal included. */
        double[] showdown(double[] opponent) {
            int n = size();
            double[] out = new double[n];
            for (int pass = 0; pass < 2; pass++) {
                boolean descendingPass = pass == 1;
                int[] order = descendingPass ? descending : ascending;
                double total = 0;
                double[] perCard = new double[52];
                int start = 0;
                while (start < n) {
                    int end = start;
                    while (end < n && scores[order[end]] == scores[order[start]]) {
                        end++;
                    }
                    for (int k = start; k < end; k++) {
                        int i = order[k];
                        double weight = total - perCard[cards[i][0]] - perCard[cards[i][1]];
                        out[i] += descendingPass ? -weight : weight;
                    }
                    for (int k = start; k < end; k++) {
                        int i = order[k];
                        total += opponent[i];
                        perCard[cards[i][0]] += opponent[i];
                        perCard[cards[i][1]] += opponent[i];
                    }
                    start = end;
                }
            }
            return out;
        }
    }

    private static final int DECISION = 0;
    private static final int FOLD = 1;
    private static final int SHOWDOWN = 2;

    private static final class Node {
        int kind;
        int player;
        int[] actions;
        int[] children;
        int folder;
        /** Big blinds the folder loses, or each player's stake at a showdown. */
        double amount;
        double[] regrets;
        double[] sums;
    }

    /** A solved river: for every decision, each hand's average strategy. */
    public static final class Solution {
        private final Map<Long, Integer> index;
        private final List<Node> nodes;
        private final int[] handOfCombo;
        private final int hands;
        private final int[] board;

        private Solution(Map<Long, Integer> index, List<Node> nodes, int[] handOfCombo, int hands, int[] board) {
            this.index = index;
            this.nodes = nodes;
            this.handOfCombo = handOfCombo;
            this.hands = hands;
            this.board = board;
        }

        /**
         * The average strategy of hand {@code combo} at {@code state}, in the order of its legal
         * actions, or {@code null} if {@code state} is not a decision of this subgame or the hand was
         * left out (it uses a board card, or neither range holds it).
         */
        public double[] probabilities(AbstractHand state, int combo) {
            Integer at = index.get(state.nodeKey());
            int hand = handOfCombo[combo];
            if (at == null || hand < 0) {
                return null;
            }
            Node node = nodes.get(at);
            return average(node, hand);
        }

        private double[] average(Node node, int hand) {
            int n = node.actions.length;
            double[] out = new double[n];
            double total = 0;
            for (int a = 0; a < n; a++) {
                out[a] = Math.max(0, node.sums[a * hands + hand]);
                total += out[a];
            }
            for (int a = 0; a < n; a++) {
                out[a] = total > 0 ? out[a] / total : 1.0 / n;
            }
            return out;
        }

        /**
         * How much the average strategy could be beaten inside this subgame, per unit of joint range
         * weight, in big blinds.
         */
        public double exploitability(double[][] ranges) {
            Hands set = new Hands(board, c -> handOfCombo[c] >= 0);
            double[][] r = new double[2][set.size()];
            for (int p = 0; p < 2; p++) {
                for (int h = 0; h < set.size(); h++) {
                    r[p][h] = ranges[p][set.combo[h]];
                }
            }
            double total = 0;
            for (int me = 0; me < 2; me++) {
                double[] best = bestResponse(0, me, r[1 - me], set);
                for (int h = 0; h < set.size(); h++) {
                    total += best[h] * r[me][h];
                }
            }
            double[] compatible = set.compatible(r[1]);
            double mass = 0;
            for (int h = 0; h < set.size(); h++) {
                mass += compatible[h] * r[0][h];
            }
            return mass > 0 ? total / (2 * mass) : 0;
        }

        private double[] bestResponse(int at, int me, double[] opponent, Hands set) {
            Node node = nodes.get(at);
            int n = set.size();
            switch (node.kind) {
                case FOLD -> {
                    double sign = node.folder == me ? -node.amount : node.amount;
                    double[] out = set.compatible(opponent);
                    for (int h = 0; h < n; h++) {
                        out[h] = sign * out[h];
                    }
                    return out;
                }
                case SHOWDOWN -> {
                    double[] out = set.showdown(opponent);
                    for (int h = 0; h < n; h++) {
                        out[h] = node.amount * out[h];
                    }
                    return out;
                }
                default -> {
                    if (node.player == me) {
                        double[] best = new double[n];
                        Arrays.fill(best, Double.NEGATIVE_INFINITY);
                        for (int child : node.children) {
                            double[] v = bestResponse(child, me, opponent, set);
                            for (int h = 0; h < n; h++) {
                                best[h] = Math.max(best[h], v[h]);
                            }
                        }
                        return best;
                    }
                    double[] value = new double[n];
                    for (int a = 0; a < node.children.length; a++) {
                        double[] reach = new double[n];
                        for (int h = 0; h < n; h++) {
                            reach[h] = opponent[h] * average(node, h)[a];
                        }
                        double[] v = bestResponse(node.children[a], me, reach, set);
                        for (int h = 0; h < n; h++) {
                            value[h] += v[h];
                        }
                    }
                    return value;
                }
            }
        }
    }

    /**
     * Solves the river subgame starting at {@code root} (on the river, before anyone acts) for
     * {@code iterations} rounds of discounted CFR. {@code ranges[p][combo]} is how likely player
     * {@code p} is to hold each pair of hole cards, up to a constant.
     */
    public static Solution solve(AbstractHand root, int[] board, double[][] ranges, int iterations) {
        if (root.street() != 3) {
            throw new IllegalArgumentException("Re-solving starts on the river.");
        }
        double[] floor = new double[2];
        for (int p = 0; p < 2; p++) {
            double max = 0;
            for (double w : ranges[p]) {
                max = Math.max(max, w);
            }
            floor[p] = 1e-6 * max;
        }
        Hands set = new Hands(board, c -> ranges[0][c] > floor[0] || ranges[1][c] > floor[1]);
        int n = set.size();
        List<Node> nodes = new ArrayList<>();
        Map<Long, Integer> index = new HashMap<>();
        build(root, nodes, index, n);
        int[] handOfCombo = new int[Cards.COMBOS];
        Arrays.fill(handOfCombo, -1);
        for (int h = 0; h < n; h++) {
            handOfCombo[set.combo[h]] = h;
        }
        double[][] reach = new double[2][n];
        for (int p = 0; p < 2; p++) {
            for (int h = 0; h < n; h++) {
                reach[p][h] = ranges[p][set.combo[h]];
            }
        }
        Solver solver = new Solver(nodes, set);
        for (int t = 1; t <= iterations; t++) {
            for (int traverser = 0; traverser < 2; traverser++) {
                solver.walk(0, traverser, reach[traverser], reach[1 - traverser]);
            }
            solver.discount(t);
        }
        return new Solution(index, nodes, handOfCombo, n, board.clone());
    }

    private static int build(AbstractHand state, List<Node> nodes, Map<Long, Integer> index, int n) {
        int at = nodes.size();
        Node node = new Node();
        nodes.add(node);
        double bb = AbstractHand.BIG_BLIND;
        if (state.terminal()) {
            if (state.folder() >= 0) {
                node.kind = FOLD;
                node.folder = state.folder();
                node.amount = state.committed(state.folder()) / bb;
            } else {
                node.kind = SHOWDOWN;
                node.amount = Math.min(state.committed(0), state.committed(1)) / bb;
            }
            return at;
        }
        node.kind = DECISION;
        node.player = state.toAct();
        node.actions = state.legalActions();
        node.regrets = new double[node.actions.length * n];
        node.sums = new double[node.actions.length * n];
        index.put(state.nodeKey(), at);
        node.children = new int[node.actions.length];
        for (int a = 0; a < node.actions.length; a++) {
            node.children[a] = build(state.apply(node.actions[a]), nodes, index, n);
        }
        return at;
    }

    private static final class Solver {
        private final List<Node> nodes;
        private final Hands set;
        private final int n;

        Solver(List<Node> nodes, Hands set) {
            this.nodes = nodes;
            this.set = set;
            this.n = set.size();
        }

        void discount(double t) {
            double positive = Math.pow(t, 1.5) / (Math.pow(t, 1.5) + 1.0);
            double average = Math.pow(t / (t + 1.0), 2);
            for (Node node : nodes) {
                if (node.kind != DECISION) {
                    continue;
                }
                for (int i = 0; i < node.regrets.length; i++) {
                    node.regrets[i] *= node.regrets[i] > 0 ? positive : 0.5;
                }
                for (int i = 0; i < node.sums.length; i++) {
                    node.sums[i] *= average;
                }
            }
        }

        double[] walk(int at, int traverser, double[] own, double[] opponent) {
            boolean any = false;
            for (double w : opponent) {
                if (w != 0) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return new double[n];
            }
            Node node = nodes.get(at);
            if (node.kind == FOLD) {
                double sign = node.folder == traverser ? -node.amount : node.amount;
                double[] out = set.compatible(opponent);
                for (int h = 0; h < n; h++) {
                    out[h] = sign * out[h];
                }
                return out;
            }
            if (node.kind == SHOWDOWN) {
                double[] out = set.showdown(opponent);
                for (int h = 0; h < n; h++) {
                    out[h] = node.amount * out[h];
                }
                return out;
            }
            int actions = node.actions.length;
            double[] strategy = new double[actions * n];
            for (int h = 0; h < n; h++) {
                double total = 0;
                for (int a = 0; a < actions; a++) {
                    total += Math.max(0, node.regrets[a * n + h]);
                }
                for (int a = 0; a < actions; a++) {
                    strategy[a * n + h] = total > 0 ? Math.max(0, node.regrets[a * n + h]) / total : 1.0 / actions;
                }
            }
            if (node.player == traverser) {
                double[][] values = new double[actions][];
                for (int a = 0; a < actions; a++) {
                    double[] reach = new double[n];
                    for (int h = 0; h < n; h++) {
                        reach[h] = own[h] * strategy[a * n + h];
                    }
                    values[a] = walk(node.children[a], traverser, reach, opponent);
                }
                double[] value = new double[n];
                for (int a = 0; a < actions; a++) {
                    for (int h = 0; h < n; h++) {
                        value[h] += strategy[a * n + h] * values[a][h];
                    }
                }
                for (int a = 0; a < actions; a++) {
                    for (int h = 0; h < n; h++) {
                        node.regrets[a * n + h] += values[a][h] - value[h];
                        node.sums[a * n + h] += own[h] * strategy[a * n + h];
                    }
                }
                return value;
            }
            double[] value = new double[n];
            for (int a = 0; a < actions; a++) {
                double[] reach = new double[n];
                for (int h = 0; h < n; h++) {
                    reach[h] = opponent[h] * strategy[a * n + h];
                }
                double[] v = walk(node.children[a], traverser, own, reach);
                for (int h = 0; h < n; h++) {
                    value[h] += v[h];
                }
            }
            return value;
        }
    }
}
