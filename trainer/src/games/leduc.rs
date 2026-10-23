//! Leduc Hold'em: six cards (J, Q, K in two suits), one private card each, one shared card after
//! the first betting round. Antes of 1; bets of 2 in the first round and 4 in the second; at most
//! two raises a round; a pair with the shared card beats any unpaired hand. The rules follow
//! OpenSpiel's `leduc_poker` (including: a player may fold only when facing a bet), so published
//! numbers for it can be used to check this implementation.
//!
//! Suits never matter here, so an information set records card ranks only.

use crate::game::{Action, Game, InfoKey, Turn};

pub const FOLD: Action = 0;
pub const CALL: Action = 1;
pub const RAISE: Action = 2;

const RANKS: [&str; 3] = ["J", "Q", "K"];
const BET_SIZE: [u32; 2] = [2, 4];
const MAX_RAISES: u8 = 2;

pub struct Leduc;

#[derive(Clone, Debug)]
pub struct LeducState {
    /// Card ids 0..6; rank is id / 2.
    cards: [u8; 2],
    dealt: usize,
    public: Option<u8>,
    round: usize,
    contributed: [u32; 2],
    /// What each player must have put in to stay in the hand this round.
    stakes: u32,
    raises: u8,
    player: usize,
    round_actions: usize,
    history: Vec<Action>,
    folded: Option<usize>,
    finished: bool,
}

fn rank(card: u8) -> u8 {
    card / 2
}

impl LeducState {
    fn strength(&self, player: usize) -> u8 {
        let own = rank(self.cards[player]);
        let public = rank(self.public.expect("showdown before the shared card"));
        if own == public {
            10 + own
        } else {
            own
        }
    }

    fn end_round(&mut self) {
        if self.round == 0 {
            self.round = 1;
            self.raises = 0;
            self.round_actions = 0;
            self.player = 0;
        } else {
            self.finished = true;
        }
    }
}

impl Game for Leduc {
    type State = LeducState;

    fn name(&self) -> &'static str {
        "leduc"
    }

    fn root(&self) -> LeducState {
        LeducState {
            cards: [0, 0],
            dealt: 0,
            public: None,
            round: 0,
            contributed: [1, 1],
            stakes: 1,
            raises: 0,
            player: 0,
            round_actions: 0,
            history: Vec::new(),
            folded: None,
            finished: false,
        }
    }

    fn turn(&self, s: &LeducState) -> Turn {
        if s.folded.is_some() || s.finished {
            Turn::Terminal
        } else if s.dealt < 2 || (s.round == 1 && s.public.is_none()) {
            Turn::Chance
        } else {
            Turn::Player(s.player)
        }
    }

    fn chance_outcomes(&self, s: &LeducState) -> Vec<(Action, f64)> {
        let used = |c: u8| s.cards[..s.dealt].contains(&c) || s.public == Some(c);
        let left: Vec<Action> = (0..6u8).filter(|c| !used(*c)).map(|c| c as Action).collect();
        let p = 1.0 / left.len() as f64;
        left.into_iter().map(|c| (c, p)).collect()
    }

    fn legal_actions(&self, s: &LeducState) -> Vec<Action> {
        let mut actions = Vec::with_capacity(3);
        if s.contributed[s.player] < s.stakes {
            actions.push(FOLD);
        }
        actions.push(CALL);
        if s.raises < MAX_RAISES {
            actions.push(RAISE);
        }
        actions
    }

    fn apply(&self, s: &LeducState, action: Action) -> LeducState {
        let mut next = s.clone();
        if s.dealt < 2 {
            next.cards[s.dealt] = action as u8;
            next.dealt += 1;
            return next;
        }
        if s.round == 1 && s.public.is_none() {
            next.public = Some(action as u8);
            return next;
        }
        let me = s.player;
        next.history.push(action);
        next.round_actions += 1;
        match action {
            FOLD => next.folded = Some(me),
            CALL => {
                next.contributed[me] = s.stakes;
                // A call closes the round once both players have acted: check-check,
                // or calling a raise.
                if next.round_actions >= 2 {
                    next.end_round();
                } else {
                    next.player = 1 - me;
                }
            }
            RAISE => {
                next.stakes = s.stakes + BET_SIZE[s.round];
                next.contributed[me] = next.stakes;
                next.raises += 1;
                next.player = 1 - me;
            }
            _ => panic!("no such Leduc action: {action}"),
        }
        next
    }

    fn payoff(&self, s: &LeducState, player: usize) -> f64 {
        let other = 1 - player;
        if let Some(folder) = s.folded {
            return if folder == player {
                -(s.contributed[player] as f64)
            } else {
                s.contributed[other] as f64
            };
        }
        match s.strength(player).cmp(&s.strength(other)) {
            std::cmp::Ordering::Greater => s.contributed[other] as f64,
            std::cmp::Ordering::Less => -(s.contributed[player] as f64),
            std::cmp::Ordering::Equal => 0.0,
        }
    }

    fn info_key(&self, s: &LeducState, player: usize) -> InfoKey {
        let own = rank(s.cards[player]) as u64;
        let public = s.public.map_or(3, |c| rank(c) as u64);
        let history = s.history.iter().fold(1u64, |code, a| code * 4 + *a as u64 + 1);
        (history << 4) | (own << 2) | public
    }

    fn describe_info(&self, s: &LeducState, player: usize) -> String {
        let moves: String = s
            .history
            .iter()
            .map(|a| match *a {
                FOLD => 'f',
                CALL => 'c',
                _ => 'r',
            })
            .collect();
        let public = s.public.map_or("-", |c| RANKS[rank(c) as usize]);
        format!("{} {}:{}", RANKS[rank(s.cards[player]) as usize], public, moves)
    }

    fn action_name(&self, action: Action) -> &'static str {
        match action {
            FOLD => "fold",
            CALL => "call",
            _ => "raise",
        }
    }
}
