//! Local best response (Lisý and Bowling, 2017): a quick, standard estimate of how much a
//! strategy can be beaten.
//!
//! The LBR player knows its own cards and the strategy it faces, but not the strategy's cards.
//! It keeps a probability for each of the 1,326 hands the opponent could hold, and after every
//! opponent action multiplies each by how likely the strategy was to take that action with that
//! hand. At its own turn it picks the action with the highest value, assuming that after it the
//! hand is checked or called to the end:
//!
//! - fold: 0
//! - check or call `c` into pot `P`: `W·(P + c) − c`, where `W` is its chance of winning against
//!   the opponent's range
//! - bet or raise, putting in `d` more: `F·P + (1 − F)·(W′·(P + 2d − c) − d)`, where `F` is how
//!   often the strategy folds to it and `W′` is the winning chance against the hands that call.
//!
//! What LBR wins on average is a lower bound on the strategy's exploitability: a full best
//! response would win at least as much. By default LBR plays the strategy itself before the
//! flop and its best response after it. Here LBR uses the same bet sizes as the strategy, so it
//! measures how well the strategy plays its own game.

use std::thread;

use super::abstraction::{all_combos, deal};
use super::eval::{score, Card};
use super::game::{Holdem, HoldemState, FOLD};
use super::play::{strategy_or_call, MatchResult};
use crate::game::{Action, Game, Turn};
use crate::rng::Rng;
use crate::solver::Policy;

/// How many random run-outs estimate the winning chance before the river: board cards still to
/// come, by street (preflop, flop). The turn is enumerated exactly.
const RUNOUTS: [usize; 2] = [40, 60];

pub struct LbrOptions {
    /// Deals; each is played twice, LBR taking each seat once.
    pub deals: u64,
    pub seed: u64,
    pub threads: usize,
    /// The streets (preflop, flop, turn, river) on which LBR plays its best response. On the
    /// others it plays the strategy itself, so a run with one street switched on measures what
    /// can be won on that street alone.
    pub streets: [bool; 4],
}

impl LbrOptions {
    /// LBR on the flop, turn and river; it plays the strategy before the flop. Its rule (value
    /// an action as if the hand were then checked down) plays the preflop badly, and would hide
    /// leaks on later streets; this is the usual way to run it.
    pub fn new(deals: u64, seed: u64, threads: usize) -> Self {
        LbrOptions { deals, seed, threads, streets: [false, true, true, true] }
    }
}

/// LBR's winnings against `policy`, in big blinds per 100 hands.
pub fn local_best_response<P: Policy>(game: &Holdem, policy: &P, options: &LbrOptions) -> MatchResult {
    let threads = options.threads.max(1) as u64;
    let combos = all_combos();
    let parts: Vec<(f64, f64, u64)> = thread::scope(|scope| {
        let workers: Vec<_> = (0..threads)
            .map(|t| {
                let combos = &combos;
                scope.spawn(move || {
                    let share = options.deals / threads + u64::from(t < options.deals % threads);
                    let mut rng = Rng::stream(options.seed, t);
                    let (mut sum, mut sum_sq) = (0.0, 0.0);
                    for _ in 0..share {
                        let cards = deal(&mut rng, 9);
                        let holes = [[cards[0], cards[1]], [cards[2], cards[3]]];
                        let board = [cards[4], cards[5], cards[6], cards[7], cards[8]];
                        let mut per_deal = 0.0;
                        for lbr_seat in 0..2 {
                            per_deal += play(
                                game,
                                policy,
                                combos,
                                holes,
                                &board,
                                lbr_seat,
                                options.streets,
                                &mut rng,
                            );
                        }
                        per_deal /= 2.0;
                        sum += per_deal;
                        sum_sq += per_deal * per_deal;
                    }
                    (sum, sum_sq, share)
                })
            })
            .collect();
        workers.into_iter().map(|w| w.join().unwrap()).collect()
    });
    let (sum, sum_sq, n) = parts.iter().fold((0.0, 0.0, 0u64), |a, p| (a.0 + p.0, a.1 + p.1, a.2 + p.2));
    let n_f = n as f64;
    let mean = sum / n_f;
    let variance = (sum_sq / n_f - mean * mean).max(0.0) * n_f / (n_f - 1.0).max(1.0);
    MatchResult { hands: 2 * n, bb_per_100: 100.0 * mean, margin: 100.0 * 1.96 * (variance / n_f).sqrt() }
}

/// One hand with LBR in `lbr_seat`; returns what LBR won, in big blinds.
#[allow(clippy::too_many_arguments)]
fn play<P: Policy>(
    game: &Holdem,
    policy: &P,
    combos: &[[Card; 2]],
    holes: [[Card; 2]; 2],
    board: &[Card; 5],
    lbr_seat: usize,
    streets: [bool; 4],
    rng: &mut Rng,
) -> f64 {
    let me = holes[lbr_seat];
    let shown = |street: usize| match street {
        0 => 0,
        1 => 3,
        2 => 4,
        _ => 5,
    };
    // The opponent's possible hands: none that share a card with LBR's.
    let mut range: Vec<f64> =
        combos.iter().map(|h| if h.contains(&me[0]) || h.contains(&me[1]) { 0.0 } else { 1.0 }).collect();
    let mut state = game.deal(holes, board);
    let mut street = usize::MAX;
    let mut buckets: Vec<u16> = Vec::new();
    loop {
        let player = match game.turn(&state) {
            Turn::Terminal => return game.payoff(&state, lbr_seat),
            Turn::Chance => unreachable!("dealt at the start"),
            Turn::Player(p) => p,
        };
        if state.street() != street {
            street = state.street();
            let visible = &board[..shown(street)];
            for (weight, hand) in range.iter_mut().zip(combos) {
                if visible.contains(&hand[0]) || visible.contains(&hand[1]) {
                    *weight = 0.0;
                }
            }
            buckets = game.bucketer().range_buckets(visible);
        }
        let legal = game.legal_actions(&state);
        if player != lbr_seat {
            let key = game.info_key(&state, player);
            let probs = strategy_or_call(policy, key, &legal);
            let choice = rng.pick(&probs);
            update_range(game, policy, &state, &legal, choice, &buckets, &mut range);
            state = game.apply(&state, legal[choice]);
        } else if streets[street] {
            let action =
                best_action(game, policy, &state, &legal, me, &board[..shown(street)], &buckets, &range, rng);
            state = game.apply(&state, action);
        } else {
            let probs = strategy_or_call(policy, game.info_key(&state, player), &legal);
            state = game.apply(&state, legal[rng.pick(&probs)]);
        }
    }
}

/// Bayes' rule over the opponent's hands after it took `legal[choice]`. The strategy's
/// probabilities depend only on the bucket, so they are looked up once per bucket.
fn update_range<P: Policy>(
    game: &Holdem,
    policy: &P,
    state: &HoldemState,
    legal: &[Action],
    choice: usize,
    buckets: &[u16],
    range: &mut [f64],
) {
    let mut by_bucket: Vec<f64> = Vec::new();
    let mut known: Vec<bool> = Vec::new();
    for (weight, &bucket) in range.iter_mut().zip(buckets) {
        if *weight == 0.0 {
            continue;
        }
        let b = bucket as usize;
        if b >= known.len() {
            known.resize(b + 1, false);
            by_bucket.resize(b + 1, 0.0);
        }
        if !known[b] {
            let key = game.info_key_with_bucket(state, bucket);
            by_bucket[b] = strategy_or_call(policy, key, legal)[choice];
            known[b] = true;
        }
        *weight *= by_bucket[b];
    }
}

/// How often the strategy folds to `bet` at each bucket.
fn fold_chance_by_bucket<P: Policy>(
    game: &Holdem,
    policy: &P,
    after_bet: &HoldemState,
    buckets: &[u16],
) -> Vec<f64> {
    let legal = game.legal_actions(after_bet);
    let Some(fold_at) = legal.iter().position(|a| *a == FOLD) else {
        return vec![0.0; 256];
    };
    let top = buckets.iter().copied().max().unwrap_or(0) as usize;
    (0..=top as u16)
        .map(|bucket| strategy_or_call(policy, game.info_key_with_bucket(after_bet, bucket), &legal)[fold_at])
        .collect()
}

#[allow(clippy::too_many_arguments)]
fn best_action<P: Policy>(
    game: &Holdem,
    policy: &P,
    state: &HoldemState,
    legal: &[Action],
    me: [Card; 2],
    visible: &[Card],
    buckets: &[u16],
    range: &[f64],
    rng: &mut Rng,
) -> Action {
    let wins = win_chances(me, visible, range, rng);
    let me_seat = match game.turn(state) {
        Turn::Player(p) => p,
        _ => unreachable!(),
    };
    let pot = state.pot() as f64;
    let call = state.to_call(me_seat) as f64;
    let total: f64 = range.iter().sum();
    if total <= 0.0 {
        // The strategy reached here with zero probability; just call.
        return super::game::CALL;
    }
    let win = range.iter().zip(&wins).map(|(w, e)| w * e).sum::<f64>() / total;

    let mut best = (f64::NEG_INFINITY, super::game::CALL);
    for &action in legal {
        let value = match action {
            FOLD => 0.0,
            super::game::CALL => win * (pot + call) - call,
            _ => {
                let after = game.apply(state, action);
                let put_in = (after.street_bet(me_seat) - state.street_bet(me_seat)) as f64;
                let folds = fold_chance_by_bucket(game, policy, &after, buckets);
                let (mut fold_weight, mut call_weight, mut call_wins) = (0.0, 0.0, 0.0);
                for ((w, &b), e) in range.iter().zip(buckets).zip(&wins) {
                    if *w > 0.0 {
                        let f = folds.get(b as usize).copied().unwrap_or(0.0);
                        fold_weight += w * f;
                        call_weight += w * (1.0 - f);
                        call_wins += w * (1.0 - f) * e;
                    }
                }
                let fold_chance = fold_weight / total;
                let win_when_called = if call_weight > 0.0 { call_wins / call_weight } else { 0.0 };
                fold_chance * pot
                    + (1.0 - fold_chance) * (win_when_called * (pot + 2.0 * put_in - call) - put_in)
            }
        };
        if value > best.0 + 1e-9 {
            best = (value, action);
        }
    }
    best.1
}

/// For every opponent hand (by `combo_index`), the chance LBR's hand beats it at showdown, ties
/// counting half, over the board cards still to come. Hands with zero weight are skipped.
fn win_chances(me: [Card; 2], visible: &[Card], range: &[f64], rng: &mut Rng) -> Vec<f64> {
    let combos = all_combos();
    let mut used = 1u64 << me[0] | 1 << me[1];
    for &c in visible {
        used |= 1 << c;
    }
    let runouts: Vec<Vec<Card>> = match visible.len() {
        5 => vec![vec![]],
        4 => (0..52u8).filter(|c| used & (1 << c) == 0).map(|c| vec![c]).collect(),
        n => {
            let needed = 5 - n;
            let samples = RUNOUTS[if n == 0 { 0 } else { 1 }];
            (0..samples)
                .map(|_| {
                    let mut taken = used;
                    (0..needed)
                        .map(|_| loop {
                            let c = (rng.next_u64() % 52) as Card;
                            if taken & (1 << c) == 0 {
                                taken |= 1 << c;
                                break c;
                            }
                        })
                        .collect()
                })
                .collect()
        }
    };
    let mut points = vec![0.0f64; 1326];
    let mut counted = vec![0u32; 1326];
    let mut seven = [0u8; 7];
    for runout in &runouts {
        let mut full = visible.to_vec();
        full.extend_from_slice(runout);
        let board_mask = full.iter().fold(0u64, |m, c| m | 1 << c);
        seven[2..].copy_from_slice(&full);
        seven[0] = me[0];
        seven[1] = me[1];
        let mine = score(&seven);
        for (index, hand) in combos.iter().enumerate() {
            if range[index] == 0.0 || board_mask & (1 << hand[0] | 1 << hand[1]) != 0 {
                continue;
            }
            seven[0] = hand[0];
            seven[1] = hand[1];
            let theirs = score(&seven);
            points[index] += match mine.cmp(&theirs) {
                std::cmp::Ordering::Greater => 1.0,
                std::cmp::Ordering::Equal => 0.5,
                std::cmp::Ordering::Less => 0.0,
            };
            counted[index] += 1;
        }
    }
    points.iter().zip(&counted).map(|(p, n)| if *n > 0 { p / *n as f64 } else { 0.0 }).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::holdem::abstraction::combo_index;
    use crate::holdem::eval::parse;

    #[test]
    fn aces_beat_a_uniform_range_most_of_the_time() {
        let me = parse("As Ah");
        let range: Vec<f64> = all_combos()
            .iter()
            .map(|h| if h.contains(&me[0]) || h.contains(&me[1]) { 0.0 } else { 1.0 })
            .collect();
        let wins = win_chances([me[0], me[1]], &[], &range, &mut Rng::new(1));
        let total: f64 = range.iter().sum();
        let average = range.iter().zip(&wins).map(|(w, e)| w * e).sum::<f64>() / total;
        assert!((average - 0.85).abs() < 0.03, "aces win {average}");
        assert_eq!(wins[combo_index(me[0], me[1])], 0.0);
    }
}
