//! Games small enough to solve exactly, used to prove the solvers right before they meet Hold'em.

pub mod kuhn;
pub mod leduc;

pub use kuhn::Kuhn;
pub use leduc::Leduc;
