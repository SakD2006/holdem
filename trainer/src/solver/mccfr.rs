//! External-sampling Monte Carlo CFR (Lanctot et al., 2009): each iteration samples one deal and
//! one action at every opponent decision, and tries every action of the player being updated.
//! An iteration costs a tiny fraction of a full walk, which is what makes Hold'em trainable;
//! Pluribus trained its blueprint this way.
//!
//! With `linear` set, iteration t counts t times as much (Linear MCCFR, as in Pluribus).

use crate::game::{Game, Turn};
use crate::rng::Rng;
use crate::solver::{Solver, Table};

pub struct ExternalSampling<G: Game> {
    game: G,
    table: Table,
    rng: Rng,
    linear: bool,
    iteration: u64,
}

impl<G: Game> ExternalSampling<G> {
    pub fn new(game: G, seed: u64, linear: bool, keep_labels: bool) -> Self {
        ExternalSampling { game, table: Table::new(keep_labels), rng: Rng::new(seed), linear, iteration: 0 }
    }

    fn weight(&self) -> f64 {
        if self.linear {
            self.iteration as f64
        } else {
            1.0
        }
    }

    /// Returns a sampled estimate of the traverser's value at `state`.
    fn walk(&mut self, state: &G::State, traverser: usize) -> f64 {
        match self.game.turn(state) {
            Turn::Terminal => self.game.payoff(state, traverser),
            Turn::Chance => {
                let outcomes = self.game.chance_outcomes(state);
                let probs: Vec<f64> = outcomes.iter().map(|(_, p)| *p).collect();
                let (outcome, _) = outcomes[self.rng.pick(&probs)];
                let child = self.game.apply(state, outcome);
                self.walk(&child, traverser)
            }
            Turn::Player(player) => {
                let actions = self.game.legal_actions(state);
                let key = self.game.info_key(state, player);
                let weight = self.weight();
                let strategy =
                    self.table.node(&self.game, state, player, key, actions.len()).current_strategy();
                if player == traverser {
                    let mut values = vec![0.0; actions.len()];
                    let mut value = 0.0;
                    for (i, action) in actions.iter().enumerate() {
                        let child = self.game.apply(state, *action);
                        values[i] = self.walk(&child, traverser);
                        value += strategy[i] * values[i];
                    }
                    let node = self.table.nodes.get_mut(&key).expect("node made above");
                    for (regret, v) in node.regrets.iter_mut().zip(&values) {
                        *regret += weight * (v - value);
                    }
                    value
                } else {
                    // The opponent's average strategy is built from the strategies it is seen to
                    // play here; sampling already weights each visit by how likely it was.
                    let node = self.table.nodes.get_mut(&key).expect("node made above");
                    for (sum, p) in node.strategy_sum.iter_mut().zip(&strategy) {
                        *sum += weight * p;
                    }
                    let choice = self.rng.pick(&strategy);
                    let child = self.game.apply(state, actions[choice]);
                    self.walk(&child, traverser)
                }
            }
        }
    }
}

impl<G: Game> Solver for ExternalSampling<G> {
    fn iterate(&mut self) {
        self.iteration += 1;
        let root = self.game.root();
        for traverser in 0..2 {
            self.walk(&root, traverser);
        }
    }

    fn iterations(&self) -> u64 {
        self.iteration
    }

    fn table(&self) -> &Table {
        &self.table
    }
}
