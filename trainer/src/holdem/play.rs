//! Playing Hold'em hands between agents, to measure a trained strategy against others.
//!
//! An agent sees only its own information set (its bucket and the betting) and the legal
//! actions, exactly what a player in its seat would know.
//!
//! Matches use duplicate deals: every deal is played twice with the seats swapped, so neither
//! agent profits from better cards, only from better play. Results are in big blinds per 100
//! hands with a 95% interval.

use super::game::{Holdem, HoldemState, CALL};
use crate::game::{Action, Game, InfoKey, Turn};
use crate::rng::Rng;
use crate::solver::{Policy, Table};

pub trait Agent: Sync {
    fn act(&self, key: InfoKey, legal: &[Action], rng: &mut Rng) -> Action;
}

/// Checks or calls every time.
pub struct CallingStation;

impl Agent for CallingStation {
    fn act(&self, _key: InfoKey, _legal: &[Action], _rng: &mut Rng) -> Action {
        CALL
    }
}

/// Picks any legal action with equal chance.
pub struct RandomAgent;

impl Agent for RandomAgent {
    fn act(&self, _key: InfoKey, legal: &[Action], rng: &mut Rng) -> Action {
        legal[(rng.next_u64() % legal.len() as u64) as usize]
    }
}

/// Plays a trained table's average strategy; checks or calls where it never trained.
pub struct TableAgent<'a>(pub &'a Table);

impl Agent for TableAgent<'_> {
    fn act(&self, key: InfoKey, legal: &[Action], rng: &mut Rng) -> Action {
        if !self.0.nodes.contains_key(&key) {
            return CALL;
        }
        legal[rng.pick(&self.0.probs(key, legal.len()))]
    }
}

#[derive(Clone, Copy, Debug)]
pub struct MatchResult {
    /// Hands played (each deal counts twice).
    pub hands: u64,
    /// First agent's winnings in big blinds per 100 hands.
    pub bb_per_100: f64,
    /// Half-width of the 95% interval.
    pub margin: f64,
}

/// Plays one hand; returns what seat 0 won, in big blinds.
fn play_hand(game: &Holdem, seats: [&dyn Agent; 2], deal: u64, rng: &mut Rng) -> f64 {
    let mut state: HoldemState = game.apply(&game.root(), deal as Action);
    loop {
        match game.turn(&state) {
            Turn::Terminal => return game.payoff(&state, 0),
            Turn::Chance => unreachable!("the whole deal happens at the start"),
            Turn::Player(p) => {
                let legal = game.legal_actions(&state);
                let action = seats[p].act(game.info_key(&state, p), &legal, rng);
                state = game.apply(&state, action);
            }
        }
    }
}

/// `first` against `second` over `deals` duplicate deals.
pub fn duel(game: &Holdem, first: &dyn Agent, second: &dyn Agent, deals: u64, seed: u64) -> MatchResult {
    let mut rng = Rng::new(seed);
    let (mut sum, mut sum_sq) = (0.0, 0.0);
    for _ in 0..deals {
        let deal = rng.next_u64();
        let as_button = play_hand(game, [first, second], deal, &mut rng);
        let as_big_blind = -play_hand(game, [second, first], deal, &mut rng);
        let per_hand = (as_button + as_big_blind) / 2.0;
        sum += per_hand;
        sum_sq += per_hand * per_hand;
    }
    let n = deals as f64;
    let mean = sum / n;
    let variance = (sum_sq / n - mean * mean).max(0.0) * n / (n - 1.0).max(1.0);
    MatchResult { hands: 2 * deals, bb_per_100: 100.0 * mean, margin: 100.0 * 1.96 * (variance / n).sqrt() }
}
