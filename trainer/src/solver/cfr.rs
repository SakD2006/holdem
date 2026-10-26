//! Counterfactual regret minimisation over the whole game tree, in its main variants. Each
//! iteration walks the tree once per player (alternating updates); the average strategy
//! converges to a Nash equilibrium.
//!
//! - Vanilla CFR (Zinkevich et al., 2007).
//! - CFR+ (Tammelin, 2014): regrets never go below zero; later iterations count more.
//! - Linear CFR and Discounted CFR (Brown & Sandholm, 2019): old regrets and strategies fade.
//!   Linear CFR is discounted CFR with alpha = beta = gamma = 1.
//!
//! Walking the whole tree is only possible for small games. For Hold'em see `mccfr`.

use crate::game::{Game, Turn};
use crate::solver::{Solver, Table};

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Variant {
    Vanilla,
    Plus,
    Discounted { alpha: f64, beta: f64, gamma: f64 },
}

impl Variant {
    pub const LINEAR: Variant = Variant::Discounted { alpha: 1.0, beta: 1.0, gamma: 1.0 };
    /// The parameters Brown and Sandholm found best across games.
    pub const DCFR: Variant = Variant::Discounted { alpha: 1.5, beta: 0.0, gamma: 2.0 };
}

pub struct Cfr<G: Game> {
    game: G,
    variant: Variant,
    table: Table,
    iteration: u64,
}

impl<G: Game> Cfr<G> {
    pub fn new(game: G, variant: Variant, keep_labels: bool) -> Self {
        Cfr { game, variant, table: Table::new(keep_labels), iteration: 0 }
    }

    /// Returns the traverser's expected value at `state`. `own_reach` is the traverser's
    /// probability of playing to here; `others_reach` is the opponent's times chance's.
    fn walk(&mut self, state: &G::State, traverser: usize, own_reach: f64, others_reach: f64) -> f64 {
        match self.game.turn(state) {
            Turn::Terminal => self.game.payoff(state, traverser),
            Turn::Chance => {
                let mut value = 0.0;
                for (outcome, p) in self.game.chance_outcomes(state) {
                    let child = self.game.apply(state, outcome);
                    value += p * self.walk(&child, traverser, own_reach, others_reach * p);
                }
                value
            }
            Turn::Player(player) => {
                let actions = self.game.legal_actions(state);
                let key = self.game.info_key(state, player);
                let strategy =
                    self.table.node(&self.game, state, player, key, actions.len()).current_strategy();
                if player == traverser {
                    let mut values = vec![0.0; actions.len()];
                    let mut value = 0.0;
                    for (i, action) in actions.iter().enumerate() {
                        let child = self.game.apply(state, *action);
                        values[i] = self.walk(&child, traverser, own_reach * strategy[i], others_reach);
                        value += strategy[i] * values[i];
                    }
                    let weight = match self.variant {
                        Variant::Plus => self.iteration as f64,
                        _ => 1.0,
                    };
                    let node = self.table.nodes.get_mut(&key).expect("node made above");
                    for i in 0..actions.len() {
                        node.pending[i] += others_reach * (values[i] - value);
                        node.strategy_sum[i] += weight * own_reach * strategy[i];
                    }
                    value
                } else {
                    let mut value = 0.0;
                    for (i, action) in actions.iter().enumerate() {
                        let child = self.game.apply(state, *action);
                        value +=
                            strategy[i] * self.walk(&child, traverser, own_reach, others_reach * strategy[i]);
                    }
                    value
                }
            }
        }
    }

    /// Adds the regret changes of the walk just finished. CFR+ then floors regrets at zero.
    fn apply_pending(&mut self) {
        let floor = self.variant == Variant::Plus;
        for node in self.table.nodes.values_mut() {
            for (regret, change) in node.regrets.iter_mut().zip(node.pending.iter_mut()) {
                *regret += *change;
                *change = 0.0;
                if floor && *regret < 0.0 {
                    *regret = 0.0;
                }
            }
        }
    }

    fn discount(&mut self) {
        if let Variant::Discounted { alpha, beta, gamma } = self.variant {
            let t = self.iteration as f64;
            let positive = t.powf(alpha) / (t.powf(alpha) + 1.0);
            let negative = t.powf(beta) / (t.powf(beta) + 1.0);
            let average = (t / (t + 1.0)).powf(gamma);
            for node in self.table.nodes.values_mut() {
                for r in node.regrets.iter_mut() {
                    *r *= if *r > 0.0 { positive } else { negative };
                }
                for s in node.strategy_sum.iter_mut() {
                    *s *= average;
                }
            }
        }
    }
}

impl<G: Game> Solver for Cfr<G> {
    fn iterate(&mut self) {
        self.iteration += 1;
        let root = self.game.root();
        for traverser in 0..2 {
            self.walk(&root, traverser, 1.0, 1.0);
            self.apply_pending();
        }
        self.discount();
    }

    fn iterations(&self) -> u64 {
        self.iteration
    }

    fn table(&self) -> &Table {
        &self.table
    }
}
