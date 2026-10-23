//! What a solver needs to know about a game: two players, zero-sum, with chance and hidden
//! information. Kuhn and Leduc poker implement it now; abstracted Hold'em will later.

/// An action, as the game numbers it. Solvers keep their numbers per information set in the
/// order `legal_actions` returns them.
pub type Action = usize;

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

    /// At a player's node: what that player may do.
    fn legal_actions(&self, state: &Self::State) -> Vec<Action>;

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
