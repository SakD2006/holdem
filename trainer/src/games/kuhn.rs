//! Kuhn poker: three cards (J, Q, K), one each, one ante, one bet of one chip. Small enough to
//! check a solver by hand: the first player's value at equilibrium is -1/18.

use crate::game::{Action, Actions, Game, InfoKey, Turn};

pub const PASS: Action = 0;
pub const BET: Action = 1;

const CARDS: [&str; 3] = ["J", "Q", "K"];

pub struct Kuhn;

#[derive(Clone, Debug)]
pub struct KuhnState {
    cards: [u8; 2],
    dealt: usize,
    history: Vec<Action>,
}

impl KuhnState {
    fn is_terminal(&self) -> bool {
        matches!(
            self.history.as_slice(),
            [PASS, PASS] | [BET, PASS] | [BET, BET] | [PASS, BET, PASS] | [PASS, BET, BET]
        )
    }

    fn history_code(&self) -> u64 {
        self.history.iter().fold(1, |code, a| code * 3 + *a as u64)
    }
}

impl Game for Kuhn {
    type State = KuhnState;

    fn name(&self) -> &'static str {
        "kuhn"
    }

    fn root(&self) -> KuhnState {
        KuhnState { cards: [0, 0], dealt: 0, history: Vec::new() }
    }

    fn turn(&self, s: &KuhnState) -> Turn {
        if s.dealt < 2 {
            Turn::Chance
        } else if s.is_terminal() {
            Turn::Terminal
        } else {
            Turn::Player(s.history.len() % 2)
        }
    }

    fn chance_outcomes(&self, s: &KuhnState) -> Vec<(Action, f64)> {
        let left: Vec<Action> = (0..3).filter(|c| !s.cards[..s.dealt].contains(&(*c as u8))).collect();
        let p = 1.0 / left.len() as f64;
        left.into_iter().map(|c| (c, p)).collect()
    }

    fn legal_actions(&self, _s: &KuhnState) -> Actions {
        Actions::from(&[PASS, BET][..])
    }

    fn apply(&self, s: &KuhnState, action: Action) -> KuhnState {
        let mut next = s.clone();
        if s.dealt < 2 {
            next.cards[s.dealt] = action as u8;
            next.dealt += 1;
        } else {
            next.history.push(action);
        }
        next
    }

    fn payoff(&self, s: &KuhnState, player: usize) -> f64 {
        let high_card_wins = if s.cards[0] > s.cards[1] { 1.0 } else { -1.0 };
        let for_first = match s.history.as_slice() {
            [PASS, PASS] => high_card_wins,
            [BET, PASS] => 1.0,
            [PASS, BET, PASS] => -1.0,
            [BET, BET] | [PASS, BET, BET] => 2.0 * high_card_wins,
            _ => panic!("not a terminal state: {:?}", s.history),
        };
        if player == 0 {
            for_first
        } else {
            -for_first
        }
    }

    fn info_key(&self, s: &KuhnState, player: usize) -> InfoKey {
        s.cards[player] as u64 + 3 * s.history_code()
    }

    fn describe_info(&self, s: &KuhnState, player: usize) -> String {
        let moves: String = s.history.iter().map(|a| if *a == PASS { 'p' } else { 'b' }).collect();
        format!("{}:{}", CARDS[s.cards[player] as usize], moves)
    }

    fn action_name(&self, action: Action) -> &'static str {
        if action == PASS {
            "pass"
        } else {
            "bet"
        }
    }
}
