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
| `src/holdem/abstraction.rs` | Card buckets: 169 starting hands; flop and turn by E[HS²] or by k-means on river-strength histograms; river strength |
| `src/holdem/kmeans.rs` | k-means on all cores, starting from k-means++ |
| `src/holdem/game.rs` | Heads-up No-Limit Hold'em, 100 big blinds, abstracted bets |
| `src/holdem/play.rs` | Duplicate matches between agents, in bb/100 with a 95% interval |
| `src/holdem/lbr.rs` | Local best response: how much an exploiter with exact cards wins |
| `src/solver/parallel.rs` | MCCFR on every core with one lock-free shared table; saving and loading strategies |
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

**Hold'em speed.** 200,000 iterations a second on the M3 Pro's 12 cores (23,000 on one), up
from 4,100 at first: legal actions and game states no longer touch the heap, both players'
walks share one deal, the river buckets of both players share one set of 1,081 hand scores,
and all threads update one table without locks (64-bit floats: 32-bit ones stopped absorbing
updates at the busiest nodes). 50 million iterations take about 5 minutes.

**Hold'em strength.** Measured with local best response (LBR) in milli-big-blinds per hand,
lower is better. LBR plays the strategy before the flop and its best response after it (its
check-down rule plays the preflop badly, which would hide later leaks).

A seeding bug made the first measurements less certain than they claimed: threads were seeded
with `seed ^ (thread · step)`, and since the generator itself only adds `step` each draw,
several threads drew the same numbers a few draws apart. Their hands overlapped, so margins
were too small. Every parallel job now gets `Rng::stream(seed, index)`, which hashes the two
together; an LBR that never deviates now breaks even. Earlier tables (50 buckets; 200
buckets, E[HS²] against histograms) are not repeated here: their margins were understated.

200 histogram buckets a street, 100,000 deals (200,000 hands), ±75 or less:

| Iterations | Preflop only | Flop only | Turn only | River only | Postflop (standard) |
|---|---|---|---|---|---|
| 2 million | −577 | −109 | 91 | 276 | 479 |
| 20 million | −384 | −10 | 220 | 586 | 635 |
| 60 million | −255 | −9 | 190 | 736 | 793 |

"River only" means LBR plays its best response on the river and the strategy everywhere else,
so it measures what the river alone gives away. The flop is sound, the turn leaks a little,
and the river leaks a lot, more the longer the strategy trains: the training sharpens play in
the river buckets, and an opponent who knows exact cards punishes what those buckets hide. A
river bucket is strength against a random hand, which says little about strength against the
hands an opponent still holds after three rounds of betting.

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
cargo run --release -- holdem --abstraction runs/abstraction-50.bin --iterations 20000000 --save runs/bp.bin
cargo run --release -- lbr --abstraction runs/abstraction-50.bin --blueprint runs/bp.bin --deals 50000
cargo run --release -- duel --abstraction runs/abstraction-50.bin --a runs/bp.bin --b runs/other.bin
cargo run --release -- holdem --abstraction categories --iterations 200000        # no file needed
```

Algorithms: `cfr`, `cfr+`, `lcfr`, `dcfr` (whole tree); `mccfr`, `lmccfr` (sampled).
`--strategy FILE` writes the learned strategy, one information set per line, for example
`Q -:cr    fold,call,raise    0.0000,0.9312,0.0688`.

## Next

1. The river: either buckets that know more (strength against several kinds of opponent hand,
   "OCHS"), or solving the river during play with exact cards (real-time search, as in
   Libratus and Pluribus), which removes river card abstraction altogether.
2. Export the blueprint for `poker-ai` and play it in the arena against the current bots.
3. Opponent modelling on top of search (restricted Nash response).
