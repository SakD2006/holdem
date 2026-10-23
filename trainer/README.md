# Trainer

Self-play training for the Hold'em bots, in Rust, with Python scripts for experiments. It is
separate from the Maven build: nothing here talks to the game server, and the Java code does not
depend on it. What it produces (strategy files) will be read by `poker-ai`.

## Why Rust and Python

Training runs the same small loop billions of times; Rust does that about a hundred times faster
than Python. Python drives the experiments and draws the charts. The Rust crate has no
dependencies and the scripts use only Python's standard library, so both build offline and every
sampled run replays exactly from its seed.

## What is here now

| Part | What it does |
|---|---|
| `src/game.rs` | What a solver needs from a game: two players, zero-sum, chance, hidden cards |
| `src/games/` | Kuhn poker and Leduc Hold'em, the standard small games for checking solvers |
| `src/solver/cfr.rs` | CFR over the whole tree: vanilla, CFR+, Linear CFR, Discounted CFR |
| `src/solver/mccfr.rs` | External-sampling Monte Carlo CFR, plain and linear (the Pluribus method) |
| `src/solver/best_response.rs` | Exact best response, exploitability and expected value |
| `src/main.rs` | The `trainer` command |
| `py/compare.py` | Runs several solvers on one game, writes a CSV and an SVG chart |

**How we know it is right.** The tests (`tests/solvers.rs`) check the games and solvers against
published answers: uniform play's exploitability in Kuhn (0.458333) and Leduc (2.373611, half
of OpenSpiel's NashConv of 4.747222); Kuhn's equilibrium value (-1/18) and the second player's
unique strategy (call a bet with a queen one time in three); Leduc's equilibrium value (about
-0.0856 for the first player).

**First results** (Leduc, 5,000 iterations, an M3 Pro, about 9 seconds each):

| Solver | Exploitability (chips per hand) |
|---|---|
| CFR | 0.0036 |
| Linear CFR | 0.0020 |
| CFR+ | 0.000015 |
| Discounted CFR | 0.000010 |
| Linear MCCFR (2.5 million sampled iterations, 12 seconds) | 0.0076 |

On a game this small, walking the whole tree wins. Hold'em is far too large to walk, which is why
the blueprint will use sampling.

## Commands

Run from `trainer/`:

```bash
cargo test --release                                          # all tests, a few seconds
cargo run --release -- info --game leduc                      # size and uniform exploitability
cargo run --release -- solve --game leduc --algo dcfr --iterations 10000 --strategy runs/leduc-dcfr.txt
```

From the repository root:

```bash
python3 trainer/py/compare.py --game leduc --iterations 5000  # every solver; CSV + chart in trainer/runs/
```

Algorithms: `cfr`, `cfr+`, `lcfr`, `dcfr` (whole tree); `mccfr`, `lmccfr` (sampled).
`--strategy FILE` writes the learned strategy, one information set per line, for example
`Q -:cr    fold,call,raise    0.0000,0.9312,0.0688`.

## Next

1. Heads-up No-Limit Hold'em as a `Game`, reusing the arena's rules: 100 big blinds, a fixed
   set of bet sizes.
2. Card abstraction: group hands by their equity distribution on each street (k-means).
3. Train a blueprint with linear MCCFR on all cores; measure it with local best response.
4. Export the blueprint for `poker-ai`, play it in the arena against the current bots.
5. Real-time search, then opponent modelling on top (restricted Nash response).
