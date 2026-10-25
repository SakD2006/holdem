//! Heads-up No-Limit Texas Hold'em as a trainer game, with abstracted cards and bets.
//!
//! Both players start every hand with 100 big blinds (10,000 chips; blinds 50 and 100). Player 0
//! is the button: small blind, first to act before the flop and last after it.
//!
//! Bets: the first bet or raise of a street can be half the pot, the pot or twice the pot; the
//! next two raises are pot-sized; all-in is always allowed; after three raises a player may only
//! fold, call or move all-in. "Pot" means the pot after calling.
//!
//! An information set is the whole betting history plus the bucket of the player's cards on the
//! current street. Earlier buckets are forgotten ("imperfect recall"), as in Pluribus: it keeps
//! the strategy small and loses little.

use std::sync::Arc;

use super::abstraction::Bucketer;
use super::eval::{score, Card};
use crate::game::{Action, Actions, Game, InfoKey, Turn};
use crate::rng::Rng;

pub const STACK: u32 = 10_000;
pub const SMALL_BLIND: u32 = 50;
pub const BIG_BLIND: u32 = 100;

pub const FOLD: Action = 0;
pub const CALL: Action = 1;
pub const ALL_IN: Action = 2;
/// `RAISE + i` raises by `SIZES[level][i]` of the pot.
pub const RAISE: Action = 3;

/// Raise sizes, as fractions of the pot, by how many raises the street has seen.
const SIZES: [&[f64]; 3] = [&[0.5, 1.0, 2.0], &[1.0], &[1.0]];
const SHOWDOWN: u8 = 4;
const STREET_MARK: u8 = 255;

pub struct Holdem {
    buckets: Arc<dyn Bucketer>,
}

impl Holdem {
    pub fn new(buckets: Arc<dyn Bucketer>) -> Self {
        Holdem { buckets }
    }

    pub fn bucketer(&self) -> &dyn Bucketer {
        self.buckets.as_ref()
    }

    /// The start of a hand with these exact cards (player 0 is the button).
    pub fn deal(&self, holes: [[Card; 2]; 2], board: &[Card; 5]) -> HoldemState {
        let mut state = self.root();
        state.buckets = self.buckets.deal_buckets(holes, board);
        for (player, hole) in holes.into_iter().enumerate() {
            state.scores[player] =
                score(&[hole[0], hole[1], board[0], board[1], board[2], board[3], board[4]]);
        }
        state.dealt = true;
        state
    }

    /// The information set of whoever holds a hand in `bucket` at this point of the betting.
    pub fn info_key_with_bucket(&self, s: &HoldemState, bucket: u16) -> InfoKey {
        let mut z = s.path_hash ^ ((bucket as u64) << 48) ^ ((s.street as u64) << 60);
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }
}

/// Longest betting record a hand can have: at most six actions a street, plus street marks.
const MAX_PATH: usize = 32;

#[derive(Clone, Copy, Debug)]
pub struct HoldemState {
    dealt: bool,
    /// Each player's bucket on each street.
    buckets: [[u16; 4]; 2],
    /// Each player's best hand at showdown.
    scores: [u32; 2],
    street: u8,
    /// Chips put in this hand, and on this street.
    committed: [u32; 2],
    street_bet: [u32; 2],
    to_act: usize,
    raises: u8,
    /// The size of the last raise, which the next one must at least match.
    last_raise: u32,
    acted: [bool; 2],
    folded: Option<usize>,
    /// Betting so far, with a mark between streets.
    path: [u8; MAX_PATH],
    path_len: u8,
    path_hash: u64,
}

impl HoldemState {
    /// 0 preflop, 1 flop, 2 turn, 3 river, 4 showdown.
    pub fn street(&self) -> usize {
        self.street as usize
    }

    /// What `player` has bet on the current street.
    pub fn street_bet(&self, player: usize) -> u32 {
        self.street_bet[player]
    }

    /// Chips `player` has put in this hand.
    pub fn committed(&self, player: usize) -> u32 {
        self.committed[player]
    }

    /// Who folded, if the hand ended that way.
    pub fn folder(&self) -> Option<usize> {
        self.folded
    }

    fn behind(&self, player: usize) -> u32 {
        STACK - self.committed[player]
    }

    pub fn to_call(&self, player: usize) -> u32 {
        self.street_bet[1 - player] - self.street_bet[player]
    }

    pub fn pot(&self) -> u32 {
        self.committed[0] + self.committed[1]
    }

    fn record(&mut self, code: u8) {
        self.path[self.path_len as usize] = code;
        self.path_len += 1;
        self.path_hash = (self.path_hash ^ (code as u64 + 1)).wrapping_mul(0x0000_0100_0000_01B3);
    }

    /// What a raise action makes the player's total bet on this street, or `None` if that size
    /// is not allowed here.
    pub fn raise_to(&self, action: Action) -> Option<u32> {
        let me = self.to_act;
        let all_in = self.street_bet[me] + self.behind(me);
        if action == ALL_IN {
            return Some(all_in);
        }
        let sizes = SIZES.get(self.raises as usize)?;
        let fraction = *sizes.get(action - RAISE)?;
        let increase = ((fraction * (self.pot() + self.to_call(me)) as f64).round() as u32)
            .max(self.last_raise)
            .max(BIG_BLIND);
        let to = self.street_bet[1 - me] + increase;
        if to < all_in {
            Some(to)
        } else {
            None
        }
    }

    fn next_street(&mut self) {
        let someone_all_in = self.behind(0) == 0 || self.behind(1) == 0;
        self.street = if someone_all_in { SHOWDOWN } else { self.street + 1 };
        self.street_bet = [0, 0];
        self.acted = [false, false];
        self.raises = 0;
        self.last_raise = BIG_BLIND;
        self.to_act = 1;
        if self.street != SHOWDOWN {
            self.record(STREET_MARK);
        }
    }
}

impl Game for Holdem {
    type State = HoldemState;

    fn name(&self) -> &'static str {
        "holdem"
    }

    fn root(&self) -> HoldemState {
        HoldemState {
            dealt: false,
            buckets: [[0; 4]; 2],
            scores: [0; 2],
            street: 0,
            committed: [SMALL_BLIND, BIG_BLIND],
            street_bet: [SMALL_BLIND, BIG_BLIND],
            to_act: 0,
            raises: 0,
            last_raise: BIG_BLIND,
            acted: [false, false],
            folded: None,
            path: [0; MAX_PATH],
            path_len: 0,
            path_hash: 0xCBF2_9CE4_8422_2325,
        }
    }

    fn turn(&self, s: &HoldemState) -> Turn {
        if !s.dealt {
            Turn::Chance
        } else if s.folded.is_some() || s.street == SHOWDOWN {
            Turn::Terminal
        } else {
            Turn::Player(s.to_act)
        }
    }

    fn chance_outcomes(&self, _s: &HoldemState) -> Vec<(Action, f64)> {
        panic!("Hold'em has too many deals to list; solvers must sample them")
    }

    /// The deal is one chance event: a random seed from which all nine cards are drawn.
    fn sample_chance(&self, _s: &HoldemState, rng: &mut Rng) -> Action {
        rng.next_u64() as Action
    }

    fn legal_actions(&self, s: &HoldemState) -> Actions {
        let me = s.to_act;
        let mut actions = Actions::new();
        if s.to_call(me) > 0 {
            actions.push(FOLD);
        }
        actions.push(CALL);
        let can_raise = s.behind(me) > s.to_call(me) && s.behind(1 - me) > 0;
        if can_raise {
            let mut previous = 0;
            if let Some(sizes) = SIZES.get(s.raises as usize) {
                for i in 0..sizes.len() {
                    if let Some(to) = s.raise_to(RAISE + i) {
                        if to != previous {
                            actions.push(RAISE + i);
                            previous = to;
                        }
                    }
                }
            }
            actions.push(ALL_IN);
        }
        actions
    }

    fn apply(&self, s: &HoldemState, action: Action) -> HoldemState {
        if !s.dealt {
            let mut rng = Rng::new(action as u64);
            let cards = super::abstraction::deal(&mut rng, 9);
            let board: [Card; 5] = [cards[4], cards[5], cards[6], cards[7], cards[8]];
            return self.deal([[cards[0], cards[1]], [cards[2], cards[3]]], &board);
        }
        let mut next = *s;
        let me = s.to_act;
        match action {
            FOLD => {
                next.folded = Some(me);
                next.record(FOLD as u8);
            }
            CALL => {
                let amount = s.to_call(me).min(s.behind(me));
                next.committed[me] += amount;
                next.street_bet[me] += amount;
                next.acted[me] = true;
                next.record(CALL as u8);
                let matched = next.street_bet[0] == next.street_bet[1] || next.behind(me) == 0;
                if next.acted[1 - me] && matched {
                    next.next_street();
                } else {
                    next.to_act = 1 - me;
                }
            }
            _ => {
                let to = s.raise_to(action).expect("a legal raise");
                let increase = to - s.street_bet[1 - me];
                next.last_raise = next.last_raise.max(increase);
                next.committed[me] += to - s.street_bet[me];
                next.street_bet[me] = to;
                next.raises += 1;
                next.acted = [false, false];
                next.acted[me] = true;
                next.to_act = 1 - me;
                next.record(action as u8);
            }
        }
        next
    }

    /// In big blinds.
    fn payoff(&self, s: &HoldemState, player: usize) -> f64 {
        let other = 1 - player;
        let chips = match s.folded {
            Some(folder) if folder == player => -(s.committed[player] as f64),
            Some(_) => s.committed[other] as f64,
            None => match s.scores[player].cmp(&s.scores[other]) {
                std::cmp::Ordering::Greater => s.committed[other] as f64,
                std::cmp::Ordering::Less => -(s.committed[player] as f64),
                std::cmp::Ordering::Equal => 0.0,
            },
        };
        chips / BIG_BLIND as f64
    }

    fn info_key(&self, s: &HoldemState, player: usize) -> InfoKey {
        self.info_key_with_bucket(s, s.buckets[player][s.street as usize])
    }

    fn describe_info(&self, s: &HoldemState, player: usize) -> String {
        let streets = ["preflop", "flop", "turn", "river"];
        let moves: String = s.path[..s.path_len as usize]
            .iter()
            .map(|code| match *code {
                STREET_MARK => "/".to_string(),
                c if c as Action == FOLD => "f".to_string(),
                c if c as Action == CALL => "c".to_string(),
                c if c as Action == ALL_IN => "a".to_string(),
                c => format!("r{}", c as Action - RAISE),
            })
            .collect();
        format!("{} {:>3} {}", streets[s.street as usize], s.buckets[player][s.street as usize], moves)
    }

    fn action_name(&self, action: Action) -> &'static str {
        match action {
            FOLD => "fold",
            CALL => "call",
            ALL_IN => "allin",
            a if a == RAISE => "raise0",
            a if a == RAISE + 1 => "raise1",
            _ => "raise2",
        }
    }
}
