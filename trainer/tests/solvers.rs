//! The games and solvers checked against known answers.

use holdem_trainer::game::{Game, Turn};
use holdem_trainer::games::{Kuhn, Leduc};
use holdem_trainer::solver::best_response::{expected_values, exploitability};
use holdem_trainer::solver::cfr::{Cfr, Variant};
use holdem_trainer::solver::mccfr::ExternalSampling;
use holdem_trainer::solver::{Solver, Uniform};

fn run(solver: &mut impl Solver, iterations: u64) {
    while solver.iterations() < iterations {
        solver.iterate();
    }
}

/// Every terminal state of a game, found by walking the whole tree.
fn terminals<G: Game>(game: &G, state: &G::State, out: &mut Vec<G::State>) {
    match game.turn(state) {
        Turn::Terminal => out.push(state.clone()),
        Turn::Chance => {
            for (o, _) in game.chance_outcomes(state) {
                terminals(game, &game.apply(state, o), out);
            }
        }
        Turn::Player(_) => {
            for a in game.legal_actions(state) {
                terminals(game, &game.apply(state, a), out);
            }
        }
    }
}

// Published values (OpenSpiel): uniform random play has NashConv 0.916667 in Kuhn and 4.747222 in
// Leduc; exploitability is half of NashConv in a two-player game.

#[test]
fn kuhn_uniform_play_has_the_published_exploitability() {
    assert!((exploitability(&Kuhn, &Uniform) - 11.0 / 24.0).abs() < 1e-9);
}

#[test]
fn leduc_uniform_play_has_the_published_exploitability() {
    assert!((exploitability(&Leduc, &Uniform) - 4.747222222 / 2.0).abs() < 1e-6);
}

#[test]
fn leduc_has_288_information_sets_by_rank() {
    let mut solver = Cfr::new(Leduc, Variant::Vanilla, false);
    solver.iterate();
    assert_eq!(solver.table().nodes.len(), 288);
}

#[test]
fn every_leduc_ending_is_zero_sum_and_within_the_stakes() {
    let mut ends = Vec::new();
    terminals(&Leduc, &Leduc.root(), &mut ends);
    assert!(!ends.is_empty());
    for end in &ends {
        let (a, b) = (Leduc.payoff(end, 0), Leduc.payoff(end, 1));
        assert_eq!(a + b, 0.0);
        // The most a player can put in is 1 + 2 * 2 + 2 * 4 = 13 chips.
        assert!(a.abs() <= 13.0);
    }
}

#[test]
fn cfr_plus_solves_kuhn_to_its_known_value() {
    let mut solver = Cfr::new(Kuhn, Variant::Plus, false);
    run(&mut solver, 2000);
    assert!(exploitability(&Kuhn, solver.table()) < 1e-3);
    assert!((expected_values(&Kuhn, solver.table())[0] + 1.0 / 18.0).abs() < 1e-3);
}

#[test]
fn kuhn_second_player_calls_a_bet_with_a_queen_one_third_of_the_time() {
    // The second player's equilibrium strategy in Kuhn poker is unique.
    let mut solver = Cfr::new(Kuhn, Variant::Plus, true);
    run(&mut solver, 5000);
    let table = solver.table();
    let (key, _) = table.labels.iter().find(|(_, label)| label.starts_with("Q:b\t")).expect("Q facing a bet");
    let call = table.nodes[key].average_strategy()[1];
    assert!((call - 1.0 / 3.0).abs() < 0.01, "calls {call}");
}

#[test]
fn discounted_cfr_solves_leduc_quickly() {
    let mut solver = Cfr::new(Leduc, Variant::DCFR, false);
    run(&mut solver, 1000);
    assert!(exploitability(&Leduc, solver.table()) < 1e-3);
}

#[test]
fn the_newer_variants_beat_vanilla_cfr_on_leduc() {
    let gap = |variant| {
        let mut solver = Cfr::new(Leduc, variant, false);
        run(&mut solver, 500);
        exploitability(&Leduc, solver.table())
    };
    let vanilla = gap(Variant::Vanilla);
    assert!(gap(Variant::Plus) < vanilla / 5.0);
    assert!(gap(Variant::DCFR) < vanilla / 5.0);
}

#[test]
fn leduc_value_matches_the_known_equilibrium_value() {
    // The first player loses about 0.0856 chips a hand at equilibrium.
    let mut solver = Cfr::new(Leduc, Variant::DCFR, false);
    run(&mut solver, 1000);
    assert!((expected_values(&Leduc, solver.table())[0] + 0.0856).abs() < 2e-3);
}

#[test]
fn external_sampling_mccfr_converges_on_kuhn() {
    let mut solver = ExternalSampling::new(Kuhn, 3, false, false);
    run(&mut solver, 200_000);
    assert!(exploitability(&Kuhn, solver.table()) < 0.01);
}

#[test]
fn linear_mccfr_converges_on_leduc() {
    let mut solver = ExternalSampling::new(Leduc, 3, true, false);
    run(&mut solver, 300_000);
    assert!(exploitability(&Leduc, solver.table()) < 0.05);
}

#[test]
fn a_seed_replays_a_sampled_run_exactly() {
    let train = || {
        let mut solver = ExternalSampling::new(Leduc, 42, true, false);
        run(&mut solver, 5000);
        exploitability(&Leduc, solver.table())
    };
    assert_eq!(train(), train());
}

#[test]
fn parallel_mccfr_converges_on_leduc_with_many_threads() {
    use holdem_trainer::solver::parallel::ParallelMccfr;
    let mut solver = ParallelMccfr::new(Leduc, 16, 4, 20_000, 7);
    solver.run_until(400_000);
    let gap = exploitability(&Leduc, solver.table());
    assert!(gap < 0.05, "exploitability {gap}");
    assert_eq!(solver.table().len(), 288);
}

#[test]
fn parallel_mccfr_on_one_thread_replays_exactly() {
    use holdem_trainer::solver::parallel::ParallelMccfr;
    let train = || {
        let mut solver = ParallelMccfr::new(Leduc, 12, 1, 1000, 3);
        solver.run_until(5000);
        exploitability(&Leduc, solver.table())
    };
    assert_eq!(train(), train());
}
