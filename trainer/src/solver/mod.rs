//! Regret tables and the solvers that fill them.
//!
//! Every solver here keeps, for each information set, the regret of each action and the running
//! sum of the strategies it played. The *current* strategy (regret matching) is what self-play
//! uses; the *average* strategy is what converges to an equilibrium and is what gets exported.

pub mod best_response;
pub mod cfr;
pub mod mccfr;
pub mod parallel;

use std::collections::HashMap;
use std::io::{self, Write};

use crate::game::{Game, InfoKey};

#[derive(Clone, Debug)]
pub struct Node {
    pub regrets: Vec<f64>,
    pub strategy_sum: Vec<f64>,
    /// Regret changes found during the current walk, applied when it ends, so that every visit
    /// to this information set in one walk plays the same strategy.
    pub pending: Vec<f64>,
}

impl Node {
    pub fn new(actions: usize) -> Self {
        Node { regrets: vec![0.0; actions], strategy_sum: vec![0.0; actions], pending: vec![0.0; actions] }
    }

    /// Regret matching: play each action in proportion to its positive regret, or uniformly if
    /// no action has any.
    pub fn current_strategy(&self) -> Vec<f64> {
        normalise_positive(&self.regrets)
    }

    pub fn average_strategy(&self) -> Vec<f64> {
        normalise_positive(&self.strategy_sum)
    }
}

fn normalise_positive(values: &[f64]) -> Vec<f64> {
    let total: f64 = values.iter().map(|v| v.max(0.0)).sum();
    if total > 0.0 {
        values.iter().map(|v| v.max(0.0) / total).collect()
    } else {
        vec![1.0 / values.len() as f64; values.len()]
    }
}

/// A way of playing: action probabilities for an information set, in legal-action order.
pub trait Policy: Sync {
    fn probs(&self, key: InfoKey, actions: usize) -> Vec<f64>;

    /// Whether the policy has learned anything at this information set.
    fn contains(&self, _key: InfoKey) -> bool {
        true
    }
}

/// Every action equally often. Useful as a baseline: its exploitability is published for the
/// standard games.
pub struct Uniform;

impl Policy for Uniform {
    fn probs(&self, _key: InfoKey, actions: usize) -> Vec<f64> {
        vec![1.0 / actions as f64; actions]
    }
}

/// The table a solver learns.
#[derive(Default)]
pub struct Table {
    pub nodes: HashMap<InfoKey, Node>,
    /// Readable names of the information sets, kept only for exporting a strategy.
    pub labels: HashMap<InfoKey, String>,
    pub keep_labels: bool,
}

impl Table {
    pub fn new(keep_labels: bool) -> Self {
        Table { keep_labels, ..Default::default() }
    }

    /// The node for an information set, made on first visit.
    pub fn node<G: Game>(
        &mut self,
        game: &G,
        state: &G::State,
        player: usize,
        key: InfoKey,
        actions: usize,
    ) -> &mut Node {
        if self.keep_labels && !self.labels.contains_key(&key) {
            let names: Vec<&str> =
                game.legal_actions(state).into_iter().map(|a| game.action_name(a)).collect();
            self.labels.insert(key, format!("{}\t{}", game.describe_info(state, player), names.join(",")));
        }
        self.nodes.entry(key).or_insert_with(|| Node::new(actions))
    }

    /// The learned (average) strategy as text: one information set per line, sorted, with its
    /// legal actions and their probabilities.
    pub fn write_strategy(&self, out: &mut impl Write) -> io::Result<()> {
        let mut lines: Vec<(String, Vec<f64>)> = self
            .nodes
            .iter()
            .map(|(key, node)| {
                let label = self.labels.get(key).cloned().unwrap_or_else(|| format!("#{key}"));
                (label, node.average_strategy())
            })
            .collect();
        lines.sort_by(|a, b| a.0.cmp(&b.0));
        for (label, probs) in lines {
            let probs: Vec<String> = probs.iter().map(|p| format!("{p:.4}")).collect();
            writeln!(out, "{label}\t{}", probs.join(","))?;
        }
        Ok(())
    }
}

impl Policy for Table {
    fn probs(&self, key: InfoKey, actions: usize) -> Vec<f64> {
        match self.nodes.get(&key) {
            Some(node) => node.average_strategy(),
            None => vec![1.0 / actions as f64; actions],
        }
    }

    fn contains(&self, key: InfoKey) -> bool {
        self.nodes.contains_key(&key)
    }
}

/// Anything that improves a table one iteration at a time.
pub trait Solver {
    fn iterate(&mut self);
    fn iterations(&self) -> u64;
    fn table(&self) -> &Table;
}

#[cfg(test)]
mod tests {
    use super::Node;

    #[test]
    fn regret_matching_uses_positive_regret_only() {
        let node = Node { regrets: vec![3.0, -5.0, 1.0], strategy_sum: vec![0.0; 3], pending: vec![0.0; 3] };
        assert_eq!(node.current_strategy(), vec![0.75, 0.0, 0.25]);
    }

    #[test]
    fn no_positive_regret_means_uniform() {
        let node = Node { regrets: vec![-1.0, -2.0], strategy_sum: vec![0.0; 2], pending: vec![0.0; 2] };
        assert_eq!(node.current_strategy(), vec![0.5, 0.5]);
    }
}
