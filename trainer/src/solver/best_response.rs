//! Exact best response and exploitability, for games small enough to walk completely.
//!
//! Exploitability is how much a perfect opponent, who knows our whole strategy, would win from
//! us on average per hand: half of (player 0's best-response value + player 1's). It is zero
//! exactly at a Nash equilibrium, so it measures how far a strategy is from one.

use std::collections::HashMap;

use crate::game::{Action, Game, InfoKey, Turn};
use crate::solver::Policy;

/// Expected payoffs of both players when both follow `policy`.
pub fn expected_values<G: Game>(game: &G, policy: &impl Policy) -> [f64; 2] {
    fn walk<G: Game>(game: &G, policy: &impl Policy, state: &G::State) -> f64 {
        match game.turn(state) {
            Turn::Terminal => game.payoff(state, 0),
            Turn::Chance => game
                .chance_outcomes(state)
                .into_iter()
                .map(|(o, p)| p * walk(game, policy, &game.apply(state, o)))
                .sum(),
            Turn::Player(player) => {
                let actions = game.legal_actions(state);
                let probs = policy.probs(game.info_key(state, player), actions.len());
                actions
                    .iter()
                    .zip(probs)
                    .map(|(a, p)| if p > 0.0 { p * walk(game, policy, &game.apply(state, *a)) } else { 0.0 })
                    .sum()
            }
        }
    }
    let first = walk(game, policy, &game.root());
    [first, -first]
}

/// What `responder` wins on average by playing the best possible reply to `policy`.
pub fn best_response_value<G: Game>(game: &G, policy: &impl Policy, responder: usize) -> f64 {
    let mut br = BestResponse { game, policy, responder, histories: HashMap::new(), choice: HashMap::new() };
    let root = game.root();
    br.collect(&root, 1.0);
    br.value(&root)
}

/// Exploitability of `policy`, in chips per hand.
pub fn exploitability<G: Game>(game: &G, policy: &impl Policy) -> f64 {
    (best_response_value(game, policy, 0) + best_response_value(game, policy, 1)) / 2.0
}

struct BestResponse<'a, G: Game, P: Policy> {
    game: &'a G,
    policy: &'a P,
    responder: usize,
    /// For each of the responder's information sets: every state in it, with the probability
    /// that chance and the opponent play to that state.
    histories: HashMap<InfoKey, Vec<(G::State, f64)>>,
    /// The best action at each information set, once worked out.
    choice: HashMap<InfoKey, Action>,
}

impl<G: Game, P: Policy> BestResponse<'_, G, P> {
    fn collect(&mut self, state: &G::State, reach: f64) {
        match self.game.turn(state) {
            Turn::Terminal => {}
            Turn::Chance => {
                for (outcome, p) in self.game.chance_outcomes(state) {
                    self.collect(&self.game.apply(state, outcome), reach * p);
                }
            }
            Turn::Player(player) => {
                let actions = self.game.legal_actions(state);
                if player == self.responder {
                    let key = self.game.info_key(state, player);
                    self.histories.entry(key).or_default().push((state.clone(), reach));
                    for action in actions {
                        self.collect(&self.game.apply(state, action), reach);
                    }
                } else {
                    let probs = self.policy.probs(self.game.info_key(state, player), actions.len());
                    for (action, p) in actions.into_iter().zip(probs) {
                        self.collect(&self.game.apply(state, action), reach * p);
                    }
                }
            }
        }
    }

    /// The responder's value at `state` when it plays its best response from here on.
    fn value(&mut self, state: &G::State) -> f64 {
        match self.game.turn(state) {
            Turn::Terminal => self.game.payoff(state, self.responder),
            Turn::Chance => {
                let mut total = 0.0;
                for (outcome, p) in self.game.chance_outcomes(state) {
                    total += p * self.value(&self.game.apply(state, outcome));
                }
                total
            }
            Turn::Player(player) if player == self.responder => {
                let action = self.best_action(self.game.info_key(state, player));
                self.value(&self.game.apply(state, action))
            }
            Turn::Player(player) => {
                let actions = self.game.legal_actions(state);
                let probs = self.policy.probs(self.game.info_key(state, player), actions.len());
                let mut total = 0.0;
                for (action, p) in actions.into_iter().zip(probs) {
                    if p > 0.0 {
                        total += p * self.value(&self.game.apply(state, action));
                    }
                }
                total
            }
        }
    }

    /// The action that does best across every state the responder cannot tell apart here,
    /// each weighted by how likely it is.
    fn best_action(&mut self, key: InfoKey) -> Action {
        if let Some(action) = self.choice.get(&key) {
            return *action;
        }
        let histories = self.histories.get(&key).cloned().expect("information set seen while collecting");
        let actions = self.game.legal_actions(&histories[0].0);
        let mut best = (f64::NEG_INFINITY, actions[0]);
        for action in actions {
            let mut total = 0.0;
            for (state, reach) in &histories {
                if *reach > 0.0 {
                    total += reach * self.value(&self.game.apply(state, action));
                }
            }
            if total > best.0 {
                best = (total, action);
            }
        }
        self.choice.insert(key, best.1);
        best.1
    }
}
