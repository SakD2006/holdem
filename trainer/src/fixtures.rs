//! Test fixtures for the Java port in `poker-ai`: examples worked out here that the Java code must
//! reproduce exactly, so the trainer and the bot that plays its strategy never disagree about a
//! bucket, an information set or a river solution.
//!
//! The file is plain text, one example per line, fields separated by spaces:
//!
//! ```text
//! deal  c0 c1 b0 b1 b2 b3 b4  preflop-class  flop-key  turn-key  river-strength-bits
//! state a1,a2,...  street to-act pot to-call  legal(l1,l2,...)  raise-to(t1,t2,...)  key0 key37
//! solve b0 b1 b2 b3 b4  iterations  exploitability-bits
//! probs node(a1,a2,...) combo p0-bits,p1-bits,...
//! ```
//!
//! Cards are 0..52 (rank × 4 + suit). Floating-point values are written as their exact bits in
//! hexadecimal. A `state` line gives the actions taken from the start of a hand (`-` for none),
//! then what the abstract game says at that point. `raise-to` lists, for each legal action, the
//! street total it makes (0 for fold and call). `key0` and `key37` are the information-set keys
//! of a hand in bucket 0 and bucket 37.

use std::fmt::Write as _;
use std::sync::Arc;

use crate::game::{Action, Game, Turn};
use crate::holdem::abstraction::{all_combos, deal, preflop_class, river_strength, CategoryBuckets};
use crate::holdem::canon;
use crate::holdem::eval::Card;
use crate::holdem::game::{Holdem, HoldemState, CALL, FOLD};
use crate::holdem::resolve;
use crate::rng::Rng;

fn list<T: std::fmt::Display>(items: impl IntoIterator<Item = T>) -> String {
    let parts: Vec<String> = items.into_iter().map(|x| x.to_string()).collect();
    if parts.is_empty() {
        "-".to_string()
    } else {
        parts.join(",")
    }
}

/// All fixtures, as the text of the file.
pub fn fixtures(seed: u64) -> String {
    let mut out = String::new();
    let mut rng = Rng::new(seed);

    for _ in 0..300 {
        let c = deal(&mut rng, 7);
        let hole = [c[0], c[1]];
        let board: [Card; 5] = [c[2], c[3], c[4], c[5], c[6]];
        writeln!(
            out,
            "deal {} {} {} {} {} {} {} {} {} {} {:08x}",
            c[0],
            c[1],
            c[2],
            c[3],
            c[4],
            c[5],
            c[6],
            preflop_class(hole),
            canon::key(hole, &board[..3]),
            canon::key(hole, &board[..4]),
            river_strength(hole, &board).to_bits()
        )
        .unwrap();
    }

    let game = Holdem::new(Arc::new(CategoryBuckets));
    let start = game.deal([[48, 49], [50, 51]], &[0, 5, 10, 15, 20]);
    for _ in 0..400 {
        let mut state = start;
        let mut taken: Vec<Action> = Vec::new();
        while let Turn::Player(player) = game.turn(&state) {
            write_state(&mut out, &game, &state, player, &taken);
            let legal = game.legal_actions(&state);
            let action = legal[(rng.next_u64() % legal.len() as u64) as usize];
            taken.push(action);
            state = game.apply(&state, action);
        }
    }

    // A river solved from uniform ranges, as the Java solver must reproduce it.
    let board: [Card; 5] = [44, 29, 14, 3, 37];
    let mut root = game.deal([[48, 49], [50, 51]], &board);
    let mut path: Vec<Action> = Vec::new();
    while root.street() < 3 {
        root = game.apply(&root, CALL);
        path.push(CALL);
    }
    let used = board.iter().fold(0u64, |m, c| m | 1 << c);
    let range: Vec<f64> = all_combos()
        .into_iter()
        .map(|h| if used & (1 << h[0] | 1 << h[1]) == 0 { 1.0 } else { 0.0 })
        .collect();
    let iterations = 30;
    let solution = resolve::solve(&game, &root, &board, [&range, &range], iterations);
    writeln!(
        out,
        "solve {} {} {} {} {} {} {:016x}",
        board[0],
        board[1],
        board[2],
        board[3],
        board[4],
        iterations,
        solution.exploitability([&range, &range], &board).to_bits()
    )
    .unwrap();
    let legal = game.legal_actions(&root);
    let mut nodes = vec![(root, path.clone())];
    for action in legal.iter() {
        let mut p = path.clone();
        p.push(*action);
        nodes.push((game.apply(&root, *action), p));
    }
    for (state, p) in nodes {
        if !matches!(game.turn(&state), Turn::Player(_)) {
            continue;
        }
        for combo in [0usize, 77, 300, 512, 900, 1200, 1325] {
            if let Some(probs) = solution.probs(&game, &state, combo) {
                writeln!(
                    out,
                    "probs {} {} {}",
                    list(&p),
                    combo,
                    list(probs.iter().map(|x| format!("{:016x}", x.to_bits())))
                )
                .unwrap();
            }
        }
    }
    out
}

fn write_state(out: &mut String, game: &Holdem, state: &HoldemState, player: usize, taken: &[Action]) {
    let legal = game.legal_actions(state);
    let raise_to: Vec<u32> = legal
        .iter()
        .map(|a| if *a == FOLD || *a == CALL { 0 } else { state.raise_to(*a).unwrap() })
        .collect();
    writeln!(
        out,
        "state {} {} {} {} {} {} {} {} {}",
        list(taken),
        state.street(),
        player,
        state.pot(),
        state.to_call(player),
        list(legal.iter()),
        list(raise_to),
        game.info_key_with_bucket(state, 0),
        game.info_key_with_bucket(state, 37)
    )
    .unwrap();
}
