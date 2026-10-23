//! What a solver needs to know about a game: two players, zero-sum, with chance and hidden
//! information. Kuhn poker, Leduc poker and abstracted heads-up Hold'em implement it.

use crate::rng::Rng;

/// An action, as the game numbers it. Solvers keep their numbers per information set in the
/// order `legal_actions` returns them.
pub type Action = usize;

/// The legal actions at a decision: a short list kept on the stack, because solvers ask for it
/// at every node of every iteration.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Actions {
    len: u8,
    items: [Action; Actions::MAX],
}

impl Actions {
    pub const MAX: usize = 8;

    pub fn new() -> Self {
        Self::default()
    }

    pub fn push(&mut self, action: Action) {
        self.items[self.len as usize] = action;
        self.len += 1;
    }
}

impl From<&[Action]> for Actions {
    fn from(actions: &[Action]) -> Self {
        let mut list = Actions::new();
        for &a in actions {
            list.push(a);
        }
        list
    }
}

impl std::ops::Deref for Actions {
    type Target = [Action];
    fn deref(&self) -> &[Action] {
        &self.items[..self.len as usize]
    }
}

impl std::ops::DerefMut for Actions {
    fn deref_mut(&mut self) -> &mut [Action] {
        &mut self.items[..self.len as usize]
    }
}

impl IntoIterator for Actions {
    type Item = Action;
    type IntoIter = std::iter::Take<std::array::IntoIter<Action, { Actions::MAX }>>;
    fn into_iter(self) -> Self::IntoIter {
        self.items.into_iter().take(self.len as usize)
    }
}

/// Identifies an information set: everything one player knows at a decision. Two states with
/// the same key must offer the same legal actions in the same order.
pub type InfoKey = u64;

/// Who moves next.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Turn {
    Chance,
    Player(usize),
    Terminal,
}

pub trait Game {
    type State: Clone;

    fn name(&self) -> &'static str;

    fn root(&self) -> Self::State;

    fn turn(&self, state: &Self::State) -> Turn;

    /// At a chance node: every outcome with its probability. The probabilities add up to 1.
    fn chance_outcomes(&self, state: &Self::State) -> Vec<(Action, f64)>;

    /// At a chance node: one outcome drawn with its probability. Games too large to list their
    /// outcomes (Hold'em deals) override this; sampling solvers only ever call this.
    fn sample_chance(&self, state: &Self::State, rng: &mut Rng) -> Action {
        let outcomes = self.chance_outcomes(state);
        let probs: Vec<f64> = outcomes.iter().map(|(_, p)| *p).collect();
        outcomes[rng.pick(&probs)].0
    }

    /// At a player's node: what that player may do.
    fn legal_actions(&self, state: &Self::State) -> Actions;

    fn apply(&self, state: &Self::State, action: Action) -> Self::State;

    /// At a terminal node: chips won (positive) or lost by `player`. The two players' payoffs
    /// always add up to zero.
    fn payoff(&self, state: &Self::State, player: usize) -> f64;

    /// The information set of `player` at this state.
    fn info_key(&self, state: &Self::State, player: usize) -> InfoKey;

    /// The same information set in words, for strategy files and debugging.
    fn describe_info(&self, state: &Self::State, player: usize) -> String;

    fn action_name(&self, action: Action) -> &'static str;
}
