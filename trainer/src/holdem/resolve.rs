//! River re-solving: when the river card is dealt, solve the rest of the hand for every pair of
//! hole cards both players could hold, instead of playing the blueprint's river buckets.
//!
//! The ranges are what the blueprint's play so far implies. The subgame is solved with
//! discounted CFR (Brown and Sandholm, 2019; alpha 1.5, beta 0, gamma 2) over all hands at once
//! ("vector" form): at each decision every hand has its own regrets, and a
//! showdown is valued for all hands in one sorted sweep, with card removal handled exactly (a
//! hand never meets an opponent hand that shares a card). Bet sizes are the blueprint's own.
//!
//! This is unsafe re-solving: it trusts the opponent's range as the blueprint implies it. The
//! range given for the opponent should therefore keep every hand slightly possible.

use std::collections::HashMap;

use super::abstraction::all_combos;
use super::eval::{score, Card};
use super::game::{Holdem, HoldemState, BIG_BLIND};
use crate::game::{Actions, Game, InfoKey, Turn};

enum Kind {
    Decision {
        player: usize,
        actions: Actions,
        children: Vec<usize>,
    },
    /// A fold: `folder` loses `lost` big blinds.
    Fold {
        folder: usize,
        lost: f64,
    },
    /// A showdown for `stake` big blinds each.
    Showdown {
        stake: f64,
    },
}

struct Node {
    kind: Kind,
    /// Per action, per hand (action-major).
    regrets: Vec<f64>,
    sums: Vec<f64>,
}

/// The hands of a river subgame: every pair of hole cards that misses the board.
struct Hands {
    combo: Vec<usize>,
    cards: Vec<[Card; 2]>,
    /// Hand positions from weakest to strongest, and the reverse.
    ascending: Vec<usize>,
    descending: Vec<usize>,
    scores: Vec<u32>,
}

impl Hands {
    /// The hands that miss the board and that `keep` accepts.
    fn new(board: &[Card; 5], keep: impl Fn(usize) -> bool) -> Hands {
        let used = board.iter().fold(0u64, |m, c| m | 1 << c);
        let (mut combo, mut cards, mut scores) = (Vec::new(), Vec::new(), Vec::new());
        for (index, hole) in all_combos().into_iter().enumerate() {
            if used & (1 << hole[0] | 1 << hole[1]) == 0 && keep(index) {
                combo.push(index);
                cards.push(hole);
                scores.push(score(&[hole[0], hole[1], board[0], board[1], board[2], board[3], board[4]]));
            }
        }
        let mut ascending: Vec<usize> = (0..combo.len()).collect();
        ascending.sort_by_key(|i| scores[*i]);
        let descending = ascending.iter().rev().copied().collect();
        Hands { combo, cards, ascending, descending, scores }
    }

    fn len(&self) -> usize {
        self.combo.len()
    }

    /// For each hand, the opponent weight on hands that share no card with it.
    fn compatible(&self, opponent: &[f64]) -> Vec<f64> {
        let total: f64 = opponent.iter().sum();
        let mut per_card = [0f64; 52];
        for (w, c) in opponent.iter().zip(&self.cards) {
            per_card[c[0] as usize] += w;
            per_card[c[1] as usize] += w;
        }
        // The identical hand was taken away once for each of its cards; give it back once.
        (0..self.len())
            .map(|i| {
                total - per_card[self.cards[i][0] as usize] - per_card[self.cards[i][1] as usize]
                    + opponent[i]
            })
            .collect()
    }

    /// For each hand, (opponent weight it beats) − (opponent weight that beats it), card
    /// removal included.
    fn showdown(&self, opponent: &[f64]) -> Vec<f64> {
        let n = self.len();
        let mut out = vec![0f64; n];
        for descending in [false, true] {
            let order = if descending { &self.descending } else { &self.ascending };
            let (mut total, mut per_card) = (0f64, [0f64; 52]);
            let mut start = 0;
            while start < n {
                let mut end = start;
                while end < n && self.scores[order[end]] == self.scores[order[start]] {
                    end += 1;
                }
                // Hands in this run meet everything strictly weaker (or stronger) seen so far.
                for &i in &order[start..end] {
                    let weight =
                        total - per_card[self.cards[i][0] as usize] - per_card[self.cards[i][1] as usize];
                    out[i] += if descending { -weight } else { weight };
                }
                for &i in &order[start..end] {
                    total += opponent[i];
                    per_card[self.cards[i][0] as usize] += opponent[i];
                    per_card[self.cards[i][1] as usize] += opponent[i];
                }
                start = end;
            }
        }
        out
    }
}

/// A solved river: for every decision of the subgame, each hand's average strategy.
pub struct RiverSolution {
    index: HashMap<InfoKey, usize>,
    nodes: Vec<Node>,
    hand_of_combo: Vec<Option<usize>>,
    hands: usize,
    pub iterations: usize,
}

/// Where a state sits in the subgame, the same for both players.
fn node_key(game: &Holdem, state: &HoldemState) -> InfoKey {
    game.info_key_with_bucket(state, 0)
}

impl RiverSolution {
    /// The average strategy of the hand `combo` (see `combo_index`) at `state`, in the order of
    /// the legal actions, or `None` if `state` is not a decision of this subgame or the hand
    /// was left out (it uses a board card, or neither range holds it).
    pub fn probs(&self, game: &Holdem, state: &HoldemState, combo: usize) -> Option<Vec<f64>> {
        let node = &self.nodes[*self.index.get(&node_key(game, state))?];
        let hand = self.hand_of_combo[combo]?;
        let Kind::Decision { actions, .. } = &node.kind else { return None };
        let n = actions.len();
        let sums: Vec<f64> = (0..n).map(|a| node.sums[a * self.hands + hand].max(0.0)).collect();
        let total: f64 = sums.iter().sum();
        Some(if total > 0.0 { sums.iter().map(|s| s / total).collect() } else { vec![1.0 / n as f64; n] })
    }

    /// How much the average strategy could be beaten inside this subgame: half of the sum of
    /// both players' best-response gains over the strategy's value, per unit of joint range
    /// weight, in big blinds. Zero at an equilibrium of the subgame.
    pub fn exploitability(&self, ranges: [&[f64]; 2], board: &[Card; 5]) -> f64 {
        let hands = Hands::new(board, |c| self.hand_of_combo[c].is_some());
        let r = |p: usize| -> Vec<f64> { hands.combo.iter().map(|c| ranges[p][*c]).collect() };
        let (r0, r1) = (r(0), r(1));
        let mut total = 0.0;
        for (me, mine, theirs) in [(0, &r0, &r1), (1, &r1, &r0)] {
            let best = self.best_response(0, me, theirs, &hands);
            total += best.iter().zip(mine.iter()).map(|(v, w)| v * w).sum::<f64>();
        }
        let mass: f64 = hands.compatible(&r1).iter().zip(&r0).map(|(c, w)| c * w).sum();
        if mass > 0.0 {
            total / (2.0 * mass)
        } else {
            0.0
        }
    }

    fn average(&self, node: &Node, actions: usize, hand: usize) -> Vec<f64> {
        let sums: Vec<f64> = (0..actions).map(|a| node.sums[a * self.hands + hand].max(0.0)).collect();
        let total: f64 = sums.iter().sum();
        if total > 0.0 {
            sums.iter().map(|s| s / total).collect()
        } else {
            vec![1.0 / actions as f64; actions]
        }
    }

    fn best_response(&self, at: usize, me: usize, opponent: &[f64], hands: &Hands) -> Vec<f64> {
        let n = hands.len();
        match &self.nodes[at].kind {
            Kind::Fold { folder, lost } => {
                let sign = if *folder == me { -lost } else { *lost };
                hands.compatible(opponent).into_iter().map(|w| sign * w).collect()
            }
            Kind::Showdown { stake } => hands.showdown(opponent).into_iter().map(|w| stake * w).collect(),
            Kind::Decision { player, actions, children } => {
                if *player == me {
                    let mut best = vec![f64::NEG_INFINITY; n];
                    for &child in children {
                        for (b, v) in best.iter_mut().zip(self.best_response(child, me, opponent, hands)) {
                            *b = b.max(v);
                        }
                    }
                    best
                } else {
                    let node = &self.nodes[at];
                    let mut value = vec![0f64; n];
                    for (a, &child) in children.iter().enumerate() {
                        let reach: Vec<f64> =
                            (0..n).map(|h| opponent[h] * self.average(node, actions.len(), h)[a]).collect();
                        for (v, x) in value.iter_mut().zip(self.best_response(child, me, &reach, hands)) {
                            *v += x;
                        }
                    }
                    value
                }
            }
        }
    }
}

/// Solves the river subgame that starts at `root` (a state on the river, before anyone acts)
/// for `iterations` rounds of discounted CFR. `ranges[p][combo]` is how likely player `p` is to hold each
/// pair of hole cards, up to a constant.
pub fn solve(
    game: &Holdem,
    root: &HoldemState,
    board: &[Card; 5],
    ranges: [&[f64]; 2],
    iterations: usize,
) -> RiverSolution {
    assert_eq!(root.street(), 3, "re-solving starts on the river");
    // Hands neither player can hold to any real degree change nothing; leave them out.
    let floor: [f64; 2] = std::array::from_fn(|p| 1e-6 * ranges[p].iter().copied().fold(0.0, f64::max));
    let hands = Hands::new(board, |c| ranges[0][c] > floor[0] || ranges[1][c] > floor[1]);
    let n = hands.len();
    let mut nodes = Vec::new();
    let mut index = HashMap::new();
    build(game, root, &mut nodes, &mut index, n);
    let mut hand_of_combo = vec![None; 1326];
    for (h, c) in hands.combo.iter().enumerate() {
        hand_of_combo[*c] = Some(h);
    }
    let reach: [Vec<f64>; 2] = std::array::from_fn(|p| hands.combo.iter().map(|c| ranges[p][*c]).collect());
    let mut solver = Solver { nodes, hands: &hands, n };
    for t in 1..=iterations {
        for traverser in 0..2 {
            solver.walk(0, traverser, &reach[traverser], &reach[1 - traverser]);
        }
        solver.discount(t as f64);
    }
    RiverSolution { index, nodes: solver.nodes, hand_of_combo, hands: n, iterations }
}

fn build(
    game: &Holdem,
    state: &HoldemState,
    nodes: &mut Vec<Node>,
    index: &mut HashMap<InfoKey, usize>,
    n: usize,
) -> usize {
    let at = nodes.len();
    let bb = BIG_BLIND as f64;
    let kind = match game.turn(state) {
        Turn::Terminal => match state.folder() {
            Some(folder) => Kind::Fold { folder, lost: state.committed(folder) as f64 / bb },
            None => Kind::Showdown { stake: state.committed(0).min(state.committed(1)) as f64 / bb },
        },
        Turn::Chance => unreachable!("no chance on the river"),
        Turn::Player(player) => {
            Kind::Decision { player, actions: game.legal_actions(state), children: Vec::new() }
        }
    };
    let width = match &kind {
        Kind::Decision { actions, .. } => actions.len() * n,
        _ => 0,
    };
    nodes.push(Node { kind, regrets: vec![0.0; width], sums: vec![0.0; width] });
    if let Kind::Decision { actions, .. } = &nodes[at].kind {
        index.insert(node_key(game, state), at);
        let actions = *actions;
        let children: Vec<usize> =
            actions.iter().map(|a| build(game, &game.apply(state, *a), nodes, index, n)).collect();
        if let Kind::Decision { children: slot, .. } = &mut nodes[at].kind {
            *slot = children;
        }
    }
    at
}

struct Solver<'a> {
    nodes: Vec<Node>,
    hands: &'a Hands,
    n: usize,
}

impl Solver<'_> {
    fn children_of(&self, at: usize) -> Vec<usize> {
        match &self.nodes[at].kind {
            Kind::Decision { children, .. } => children.clone(),
            _ => Vec::new(),
        }
    }

    /// The traverser's counterfactual value of each of its hands at node `at`.
    /// Discounted CFR after iteration `t`: positive regrets keep t^1.5/(t^1.5+1), negative ones
    /// half, and the strategy sums (t/(t+1))^2.
    fn discount(&mut self, t: f64) {
        let positive = t.powf(1.5) / (t.powf(1.5) + 1.0);
        let average = (t / (t + 1.0)).powi(2);
        for node in &mut self.nodes {
            for r in node.regrets.iter_mut() {
                *r *= if *r > 0.0 { positive } else { 0.5 };
            }
            for x in node.sums.iter_mut() {
                *x *= average;
            }
        }
    }

    fn walk(&mut self, at: usize, traverser: usize, own: &[f64], opponent: &[f64]) -> Vec<f64> {
        let n = self.n;
        if opponent.iter().all(|w| *w == 0.0) {
            return vec![0.0; n];
        }
        let (player, actions, children) = match &self.nodes[at].kind {
            Kind::Fold { folder, lost } => {
                let sign = if *folder == traverser { -lost } else { *lost };
                return self.hands.compatible(opponent).into_iter().map(|w| sign * w).collect();
            }
            Kind::Showdown { stake } => {
                return self.hands.showdown(opponent).into_iter().map(|w| stake * w).collect();
            }
            Kind::Decision { player, actions, .. } => (*player, actions.len(), self.children_of(at)),
        };
        // Regret matching, hand by hand.
        let mut strategy = vec![0f64; actions * n];
        {
            let regrets = &self.nodes[at].regrets;
            for h in 0..n {
                let total: f64 = (0..actions).map(|a| regrets[a * n + h].max(0.0)).sum();
                for a in 0..actions {
                    strategy[a * n + h] =
                        if total > 0.0 { regrets[a * n + h].max(0.0) / total } else { 1.0 / actions as f64 };
                }
            }
        }
        if player == traverser {
            let mut values = Vec::with_capacity(actions);
            for (a, &child) in children.iter().enumerate() {
                let reach: Vec<f64> = (0..n).map(|h| own[h] * strategy[a * n + h]).collect();
                values.push(self.walk(child, traverser, &reach, opponent));
            }
            let mut value = vec![0f64; n];
            for (a, v) in values.iter().enumerate() {
                for h in 0..n {
                    value[h] += strategy[a * n + h] * v[h];
                }
            }
            let node = &mut self.nodes[at];
            for (a, v) in values.iter().enumerate() {
                for h in 0..n {
                    node.regrets[a * n + h] += v[h] - value[h];
                    node.sums[a * n + h] += own[h] * strategy[a * n + h];
                }
            }
            value
        } else {
            let mut value = vec![0f64; n];
            for (a, &child) in children.iter().enumerate() {
                let reach: Vec<f64> = (0..n).map(|h| opponent[h] * strategy[a * n + h]).collect();
                for (v, x) in value.iter_mut().zip(self.walk(child, traverser, own, &reach)) {
                    *v += x;
                }
            }
            value
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::holdem::abstraction::CategoryBuckets;
    use crate::holdem::eval::parse;
    use crate::holdem::game::CALL;
    use std::sync::Arc;

    /// A hand checked through to the river, with the given board.
    fn river_root(game: &Holdem, board: &[Card; 5]) -> HoldemState {
        let holes: [[Card; 2]; 2] = [[48, 49], [50, 51]]; // the four aces, on no test board
        let mut s = game.deal(holes, board);
        while s.street() < 3 {
            s = game.apply(&s, CALL);
        }
        s
    }

    fn board(text: &str) -> [Card; 5] {
        let c = parse(text);
        [c[0], c[1], c[2], c[3], c[4]]
    }

    fn uniform(board: &[Card; 5]) -> Vec<f64> {
        let used = board.iter().fold(0u64, |m, c| m | 1 << c);
        all_combos()
            .into_iter()
            .map(|h| if used & (1 << h[0] | 1 << h[1]) == 0 { 1.0 } else { 0.0 })
            .collect()
    }

    #[test]
    fn showdown_weights_match_counting_by_hand() {
        let b = board("Ah 7d 7c 2s Td");
        let hands = Hands::new(&b, |_| true);
        let opponent: Vec<f64> = (0..hands.len()).map(|i| 1.0 + (i % 5) as f64).collect();
        let fast = hands.showdown(&opponent);
        for i in [0, 17, 300, hands.len() - 1] {
            let mut slow = 0.0;
            for (j, weight) in opponent.iter().enumerate() {
                let shares = hands.cards[i].iter().any(|c| hands.cards[j].contains(c));
                if !shares {
                    slow += weight * (hands.scores[i].cmp(&hands.scores[j]) as i32) as f64;
                }
            }
            assert!((fast[i] - slow).abs() < 1e-9, "hand {i}: {} against {slow}", fast[i]);
        }
    }

    #[test]
    fn solving_longer_leaves_less_to_exploit() {
        let game = Holdem::new(Arc::new(CategoryBuckets));
        let b = board("Kh 9d 5c 2s Jd");
        let root = river_root(&game, &b);
        let range = uniform(&b);
        let short = solve(&game, &root, &b, [&range, &range], 20).exploitability([&range, &range], &b);
        let long = solve(&game, &root, &b, [&range, &range], 400).exploitability([&range, &range], &b);
        assert!(long < short / 3.0, "20 iterations {short}, 400 iterations {long}");
        assert!(long < 0.02, "exploitability {long} big blinds");
    }

    #[test]
    fn the_nuts_never_folds_and_air_never_calls_a_big_bet() {
        let game = Holdem::new(Arc::new(CategoryBuckets));
        let b = board("Qs Js Ts 2h 3d");
        let root = river_root(&game, &b);
        let range = uniform(&b);
        let solution = solve(&game, &root, &b, [&range, &range], 400);
        // The big blind acts first on the river; it bets the pot (or more) and we look at the
        // button's answer.
        let legal = game.legal_actions(&root);
        let bet = legal.iter().copied().find(|a| *a >= super::super::game::RAISE).expect("a bet");
        let facing = game.apply(&root, bet);
        let answers = game.legal_actions(&facing);
        let fold_at = answers.iter().position(|a| *a == super::super::game::FOLD).unwrap();
        let nuts = parse("As Ks");
        let air = parse("4c 5c");
        let nuts_probs = solution
            .probs(&game, &facing, crate::holdem::abstraction::combo_index(nuts[0], nuts[1]))
            .unwrap();
        let air_probs =
            solution.probs(&game, &facing, crate::holdem::abstraction::combo_index(air[0], air[1])).unwrap();
        assert!(nuts_probs[fold_at] < 0.01, "{nuts_probs:?}");
        assert!(air_probs[fold_at] > 0.9, "{air_probs:?}");
    }
}
