//! Self-play trainer for the Hold'em bots.
//!
//! The plan, in order: prove the solvers on games small enough to solve exactly (Kuhn and Leduc
//! poker, here now), then train an abstracted heads-up Hold'em blueprint with Monte Carlo CFR,
//! export it, and let `poker-ai` play it in Java. Nothing here talks to the game server.

pub mod fixtures;
pub mod game;
pub mod games;
pub mod holdem;
pub mod rng;
pub mod solver;
