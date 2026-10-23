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
| `src/holdem/eval.rs` | Hand evaluator, a port of `poker-ai`'s `FastHand` (same card numbers and scores) |
| `src/holdem/canon.rs` | Canonical keys: deals that differ only by suit names share one |
| `src/holdem/abstraction.rs` | Card buckets: 169 starting hands; E[HS²] buckets on flop and turn; river strength |
| `src/holdem/game.rs` | Heads-up No-Limit Hold'em, 100 big blinds, abstracted bets |
| `src/holdem/play.rs` | Duplicate matches between agents, in bb/100 with a 95% interval |
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

**Hold'em, first run** (50 buckets a street, linear MCCFR, one core, 2 million iterations in
8 minutes, 787,000 information sets): +362 ±21 bb/100 against a player who always calls and
+274 ±27 against a random player, over 40,000 duplicate hands each. That shows it learned;
it does not measure strength, which needs a best-response estimate (next).

The checks: the evaluator puts all 2,598,960 five-card and 133,784,560 seven-card hands in the
right categories; the canonical deals number 1,286,792 flops and 13,960,050 turns, as published
(Waugh, 2013); 20,000 random hands keep every rule and conserve chips.

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

Hold'em (from `trainer/`):

```bash
cargo run --release -- abstraction --out runs/abstraction-50.bin --buckets 50    # about 3 minutes, 137 MB
cargo run --release -- holdem --abstraction runs/abstraction-50.bin --iterations 2000000
cargo run --release -- holdem --abstraction categories --iterations 200000        # no file needed
```

Algorithms: `cfr`, `cfr+`, `lcfr`, `dcfr` (whole tree); `mccfr`, `lmccfr` (sampled).
`--strategy FILE` writes the learned strategy, one information set per line, for example
`Q -:cr    fold,call,raise    0.0000,0.9312,0.0688`.

## Next

1. Speed: training on all cores with a shared lock-free table, and fewer allocations per
   iteration (now about 4,000 iterations a second on one core).
2. Strength: local best response (LBR), the standard estimate of how much a perfect-ish
   opponent wins against the strategy.
3. Better buckets: k-means on equity histograms.
4. Export the blueprint for `poker-ai` and play it in the arena against the current bots.
5. Real-time search, then opponent modelling on top (restricted Nash response).
