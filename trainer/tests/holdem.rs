//! Heads-up Hold'em as a trainer game: the rules hold over many random hands, and a short
//! training run already beats players who ignore their cards.

use std::sync::Arc;

use holdem_trainer::game::{Game, Turn};
use holdem_trainer::holdem::abstraction::CategoryBuckets;
use holdem_trainer::holdem::game::{Holdem, ALL_IN, BIG_BLIND, CALL, FOLD, STACK};
use holdem_trainer::holdem::play::{duel, CallingStation, RandomAgent, TableAgent};
use holdem_trainer::rng::Rng;
use holdem_trainer::solver::mccfr::ExternalSampling;
use holdem_trainer::solver::Solver;

fn game() -> Holdem {
    Holdem::new(Arc::new(CategoryBuckets))
}

#[test]
fn random_hands_follow_the_rules() {
    let game = game();
    let mut rng = Rng::new(9);
    for _ in 0..20_000 {
        let mut state = game.apply(&game.root(), rng.next_u64() as usize);
        let mut decisions = 0;
        loop {
            match game.turn(&state) {
                Turn::Terminal => break,
                Turn::Chance => panic!("only one deal per hand"),
                Turn::Player(_) => {
                    let legal = game.legal_actions(&state);
                    assert!(legal.contains(&CALL));
                    let mut unique = legal.to_vec();
                    unique.sort_unstable();
                    unique.dedup();
                    assert_eq!(unique.len(), legal.len(), "no action twice: {legal:?}");
                    let action = legal[(rng.next_u64() % legal.len() as u64) as usize];
                    state = game.apply(&state, action);
                    decisions += 1;
                    assert!(decisions < 60, "hand never ended");
                }
            }
        }
        let (a, b) = (game.payoff(&state, 0), game.payoff(&state, 1));
        assert_eq!(a + b, 0.0, "chips are conserved");
        assert!(a.abs() <= (STACK / BIG_BLIND) as f64, "nobody wins more than a stack");
    }
}

#[test]
fn folding_is_offered_only_against_a_bet() {
    let game = game();
    let state = game.apply(&game.root(), 1);
    // The small blind faces the big blind: may fold.
    assert!(game.legal_actions(&state).contains(&FOLD));
    // Small blind calls; the big blind can check, so it may not fold.
    let state = game.apply(&state, CALL);
    assert!(!game.legal_actions(&state).contains(&FOLD));
}

#[test]
fn all_in_and_call_goes_to_showdown_for_whole_stacks() {
    let game = game();
    let state = game.apply(&game.root(), 7);
    let state = game.apply(&state, ALL_IN);
    let state = game.apply(&state, CALL);
    assert_eq!(game.turn(&state), Turn::Terminal);
    let won = game.payoff(&state, 0);
    assert!(won == 0.0 || won.abs() == 100.0);
}

#[test]
fn a_short_training_run_beats_players_who_ignore_their_cards() {
    let mut solver = ExternalSampling::new(game(), 5, true, false);
    while solver.iterations() < 200_000 {
        solver.iterate();
    }
    let trained = TableAgent(solver.table());
    let game = game();
    let against_calls = duel(&game, &trained, &CallingStation, 20_000, 11);
    let against_random = duel(&game, &trained, &RandomAgent, 20_000, 12);
    assert!(against_calls.bb_per_100 - against_calls.margin > 0.0, "{against_calls:?}");
    assert!(against_random.bb_per_100 - against_random.margin > 0.0, "{against_random:?}");
}

#[test]
fn local_best_response_crushes_strategies_that_ignore_their_cards() {
    use holdem_trainer::holdem::lbr::{local_best_response, LbrOptions};
    use holdem_trainer::solver::{Table, Uniform};
    let game = game();
    let mut options = LbrOptions::new(1500, 3, 4);
    options.streets = [true; 4];
    // An empty table never trained anywhere, so it checks and calls every time.
    let against_calls = local_best_response(&game, &Table::new(false), &options);
    let against_random = local_best_response(&game, &Uniform, &options);
    assert!(against_calls.bb_per_100 - against_calls.margin > 50.0, "{against_calls:?}");
    assert!(against_random.bb_per_100 - against_random.margin > 50.0, "{against_random:?}");
}

#[test]
fn training_makes_a_strategy_harder_to_exploit() {
    use holdem_trainer::holdem::lbr::{local_best_response, LbrOptions};
    use holdem_trainer::solver::parallel::ParallelMccfr;
    use holdem_trainer::solver::Uniform;
    let mut solver = ParallelMccfr::new(game(), 20, 4, 50_000, 5);
    solver.run_until(400_000);
    let game = game();
    let options = LbrOptions::new(6000, 4, 12);
    let trained = local_best_response(&game, solver.table(), &options);
    let random = local_best_response(&game, &Uniform, &options);
    assert!(
        trained.bb_per_100 + trained.margin < random.bb_per_100 - random.margin,
        "{trained:?} vs {random:?}"
    );
}

#[test]
fn lbr_that_never_deviates_breaks_even() {
    use holdem_trainer::holdem::lbr::{local_best_response, LbrOptions};
    use holdem_trainer::solver::Uniform;
    // Playing the strategy on every street is the strategy against itself: no edge.
    let mut options = LbrOptions::new(30_000, 5, 12);
    options.streets = [false; 4];
    let result = local_best_response(&game(), &Uniform, &options);
    assert!(result.bb_per_100.abs() < result.margin, "{result:?}");
}
