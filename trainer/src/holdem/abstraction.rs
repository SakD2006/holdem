//! Card abstraction: which bucket a hand falls in on each street. The solver sees the bucket,
//! not the cards, so hands in one bucket share a strategy.
//!
//! - Preflop: the 169 starting hands (pairs, suited, offsuit). Nothing is lost.
//! - Flop and turn: buckets of equal size by E[HS²], the average over random run-outs of the
//!   squared chance of beating a random hand on the river. Squaring rewards hands that might
//!   become very strong (draws) over hands that are merely middling (Johanson et al., 2013).
//!   Worked out once for every canonical deal and saved; looked up during training.
//! - River: buckets of equal size by exact hand strength against every possible opponent hand,
//!   worked out during training (about 1,000 evaluations).
//!
//! Version 1 is deliberately simple. Next: k-means on equity histograms (distribution-aware
//! buckets), which separates draws from made hands of the same average strength.

use std::fs::File;
use std::io::{self, BufReader, BufWriter, Read, Write};
use std::path::Path;
use std::thread;

use super::canon;
use super::eval::{score, Card};
use crate::rng::Rng;

pub const PREFLOP_BUCKETS: usize = 169;
const MAGIC: &[u8; 8] = b"HOLDEMAB";
const VERSION: u32 = 1;

#[derive(Clone, Debug)]
pub struct BuildOptions {
    pub flop_buckets: usize,
    pub turn_buckets: usize,
    pub river_buckets: usize,
    /// Random run-outs per flop deal, and per turn deal.
    pub flop_runouts: usize,
    pub turn_runouts: usize,
    /// Random opponent hands per run-out.
    pub opponents: usize,
    /// Random deals used to place the bucket boundaries.
    pub boundary_samples: usize,
    pub seed: u64,
}

impl Default for BuildOptions {
    fn default() -> Self {
        BuildOptions {
            flop_buckets: 50,
            turn_buckets: 50,
            river_buckets: 50,
            flop_runouts: 48,
            turn_runouts: 24,
            opponents: 96,
            boundary_samples: 1_000_000,
            seed: 1,
        }
    }
}

/// Buckets for every canonical deal of one street, sorted by key.
struct KeyTable {
    keys: Vec<u64>,
    buckets: Vec<u8>,
}

impl KeyTable {
    fn bucket(&self, key: u64) -> usize {
        let i = self.keys.binary_search(&key).expect("every canonical deal is in the table");
        self.buckets[i] as usize
    }
}

/// Anything that puts a hand in a bucket on each street. The full `Abstraction` is the real
/// one; `CategoryBuckets` is a cheap stand-in for tests and quick runs.
pub trait Bucketer: Send + Sync {
    /// The bucket of `hole` on each street of a five-card `board`: preflop, flop, turn, river.
    fn buckets_for(&self, hole: [Card; 2], board: &[Card; 5]) -> [u16; 4];
    /// How many buckets each street has.
    fn sizes(&self) -> [usize; 4];

    /// Both players' buckets for one deal. Bucketers that can share work between the two
    /// players override this.
    fn deal_buckets(&self, holes: [[Card; 2]; 2], board: &[Card; 5]) -> [[u16; 4]; 2] {
        [self.buckets_for(holes[0], board), self.buckets_for(holes[1], board)]
    }

    /// The bucket of every possible pair of hole cards (indexed by `combo_index`) on a board of
    /// 0, 3, 4 or 5 cards. Pairs that use a board card get bucket 0; callers skip them.
    fn range_buckets(&self, board: &[Card]) -> Vec<u16>;
}

/// The number of a pair of different cards, 0..1326.
pub fn combo_index(a: Card, b: Card) -> usize {
    let (low, high) = if a < b { (a as usize, b as usize) } else { (b as usize, a as usize) };
    high * (high - 1) / 2 + low
}

/// Every pair of hole cards, in `combo_index` order.
pub fn all_combos() -> Vec<[Card; 2]> {
    let mut out = vec![[0, 0]; 1326];
    for b in 1..52u8 {
        for a in 0..b {
            out[combo_index(a, b)] = [a, b];
        }
    }
    out
}

fn board_mask(board: &[Card]) -> u64 {
    board.iter().fold(0u64, |m, c| m | 1 << c)
}

/// Preflop: the 169 starting hands. After: the category of the best hand so far (high card,
/// pair, ... straight flush). Needs no tables, plays badly.
pub struct CategoryBuckets;

impl Bucketer for CategoryBuckets {
    fn buckets_for(&self, hole: [Card; 2], board: &[Card; 5]) -> [u16; 4] {
        let made = |n: usize| {
            let mut cards = vec![hole[0], hole[1]];
            cards.extend_from_slice(&board[..n]);
            (score(&cards) >> 20) as u16
        };
        [preflop_class(hole) as u16, made(3), made(4), made(5)]
    }

    fn sizes(&self) -> [usize; 4] {
        [PREFLOP_BUCKETS, 9, 9, 9]
    }

    fn range_buckets(&self, board: &[Card]) -> Vec<u16> {
        let used = board_mask(board);
        all_combos()
            .into_iter()
            .map(|hole| {
                if used & (1 << hole[0] | 1 << hole[1]) != 0 {
                    0
                } else if board.is_empty() {
                    preflop_class(hole) as u16
                } else {
                    let mut cards = vec![hole[0], hole[1]];
                    cards.extend_from_slice(board);
                    (score(&cards) >> 20) as u16
                }
            })
            .collect()
    }
}

pub struct Abstraction {
    pub buckets: [usize; 4],
    flop: KeyTable,
    turn: KeyTable,
    /// Hand strengths that separate river buckets.
    river_edges: Vec<f32>,
}

impl Bucketer for Abstraction {
    fn buckets_for(&self, hole: [Card; 2], board: &[Card; 5]) -> [u16; 4] {
        [
            preflop_class(hole) as u16,
            self.flop.bucket(canon::key(hole, &board[..3])) as u16,
            self.turn.bucket(canon::key(hole, &board[..4])) as u16,
            self.river_edges.partition_point(|edge| *edge <= river_strength(hole, board)) as u16,
        ]
    }

    fn sizes(&self) -> [usize; 4] {
        self.buckets
    }

    fn range_buckets(&self, board: &[Card]) -> Vec<u16> {
        let used = board_mask(board);
        let combos = all_combos();
        if board.len() == 5 {
            let board: &[Card; 5] = board.try_into().expect("five cards");
            let strengths = all_river_strengths(board);
            return strengths
                .iter()
                .map(|s| if s.is_nan() { 0 } else { self.river_edges.partition_point(|e| e <= s) as u16 })
                .collect();
        }
        combos
            .into_iter()
            .map(|hole| match board.len() {
                _ if used & (1 << hole[0] | 1 << hole[1]) != 0 => 0,
                0 => preflop_class(hole) as u16,
                3 => self.flop.bucket(canon::key(hole, board)) as u16,
                4 => self.turn.bucket(canon::key(hole, board)) as u16,
                n => panic!("no street has {n} board cards"),
            })
            .collect()
    }

    fn deal_buckets(&self, holes: [[Card; 2]; 2], board: &[Card; 5]) -> [[u16; 4]; 2] {
        let strengths = river_strengths(holes, board);
        let mut out = [[0u16; 4]; 2];
        for (player, hole) in holes.into_iter().enumerate() {
            out[player] = [
                preflop_class(hole) as u16,
                self.flop.bucket(canon::key(hole, &board[..3])) as u16,
                self.turn.bucket(canon::key(hole, &board[..4])) as u16,
                self.river_edges.partition_point(|edge| *edge <= strengths[player]) as u16,
            ];
        }
        out
    }
}

impl Abstraction {
    pub fn build(options: &BuildOptions, log: impl Fn(&str) + Sync) -> Abstraction {
        log("listing canonical flops");
        let flops = canonical_flops();
        log(&format!("{} canonical flops; listing turns", flops.len()));
        let turns = canonical_turns(&flops);
        log(&format!("{} canonical turns; strength of every flop", turns.len()));
        let flop_values = parallel_map(
            &flops,
            |key, rng| {
                let (hole, board) = canon::unpack(key, 3);
                ehs2(hole, &board, options.flop_runouts, options.opponents, rng)
            },
            options.seed,
        );
        log("strength of every turn");
        let turn_values = parallel_map(
            &turns,
            |key, rng| {
                let (hole, board) = canon::unpack(key, 4);
                ehs2(hole, &board, options.turn_runouts, options.opponents, rng)
            },
            options.seed ^ 0x7475_726E,
        );

        log("placing bucket boundaries");
        let mut rng = Rng::new(options.seed ^ 0xB0B0);
        let n = options.boundary_samples;
        let (mut flop_sample, mut turn_sample, mut river_sample) =
            (Vec::with_capacity(n), Vec::with_capacity(n), Vec::with_capacity(n / 5));
        for i in 0..n {
            let cards = deal(&mut rng, 7);
            let hole = [cards[0], cards[1]];
            flop_sample.push(flop_values[flops.binary_search(&canon::key(hole, &cards[2..5])).unwrap()]);
            turn_sample.push(turn_values[turns.binary_search(&canon::key(hole, &cards[2..6])).unwrap()]);
            if i % 5 == 0 {
                let board = [cards[2], cards[3], cards[4], cards[5], cards[6]];
                river_sample.push(river_strength(hole, &board));
            }
        }
        let flop_edges = edges(flop_sample, options.flop_buckets);
        let turn_edges = edges(turn_sample, options.turn_buckets);
        let river_edges = edges(river_sample, options.river_buckets);
        let place = |values: &[f32], edges: &[f32]| -> Vec<u8> {
            values.iter().map(|v| edges.partition_point(|e| e <= v) as u8).collect()
        };
        Abstraction {
            buckets: [PREFLOP_BUCKETS, options.flop_buckets, options.turn_buckets, options.river_buckets],
            flop: KeyTable { buckets: place(&flop_values, &flop_edges), keys: flops },
            turn: KeyTable { buckets: place(&turn_values, &turn_edges), keys: turns },
            river_edges,
        }
    }

    pub fn save(&self, path: &Path) -> io::Result<()> {
        if let Some(folder) = path.parent() {
            std::fs::create_dir_all(folder)?;
        }
        let mut out = BufWriter::new(File::create(path)?);
        out.write_all(MAGIC)?;
        out.write_all(&VERSION.to_le_bytes())?;
        for b in self.buckets {
            out.write_all(&(b as u32).to_le_bytes())?;
        }
        for table in [&self.flop, &self.turn] {
            out.write_all(&(table.keys.len() as u64).to_le_bytes())?;
            for k in &table.keys {
                out.write_all(&k.to_le_bytes())?;
            }
            out.write_all(&table.buckets)?;
        }
        out.write_all(&(self.river_edges.len() as u32).to_le_bytes())?;
        for e in &self.river_edges {
            out.write_all(&e.to_le_bytes())?;
        }
        out.flush()
    }

    pub fn load(path: &Path) -> io::Result<Abstraction> {
        let mut input = BufReader::new(File::open(path)?);
        let mut magic = [0u8; 8];
        input.read_exact(&mut magic)?;
        if &magic != MAGIC || read_u32(&mut input)? != VERSION {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "not an abstraction file of this version",
            ));
        }
        let mut buckets = [0usize; 4];
        for b in buckets.iter_mut() {
            *b = read_u32(&mut input)? as usize;
        }
        let mut tables = Vec::new();
        for _ in 0..2 {
            let len = read_u64(&mut input)? as usize;
            let mut keys = Vec::with_capacity(len);
            for _ in 0..len {
                keys.push(read_u64(&mut input)?);
            }
            let mut table_buckets = vec![0u8; len];
            input.read_exact(&mut table_buckets)?;
            tables.push(KeyTable { keys, buckets: table_buckets });
        }
        let edges = read_u32(&mut input)? as usize;
        let mut river_edges = Vec::with_capacity(edges);
        for _ in 0..edges {
            let mut bytes = [0u8; 4];
            input.read_exact(&mut bytes)?;
            river_edges.push(f32::from_le_bytes(bytes));
        }
        let turn = tables.pop().unwrap();
        let flop = tables.pop().unwrap();
        Ok(Abstraction { buckets, flop, turn, river_edges })
    }
}

fn read_u32(input: &mut impl Read) -> io::Result<u32> {
    let mut bytes = [0u8; 4];
    input.read_exact(&mut bytes)?;
    Ok(u32::from_le_bytes(bytes))
}

fn read_u64(input: &mut impl Read) -> io::Result<u64> {
    let mut bytes = [0u8; 8];
    input.read_exact(&mut bytes)?;
    Ok(u64::from_le_bytes(bytes))
}

/// The 169 starting hands: 13 pairs (0..13), then 78 suited and 78 offsuit hands.
pub fn preflop_class(hole: [Card; 2]) -> usize {
    let (a, b) = ((hole[0] >> 2) as usize, (hole[1] >> 2) as usize);
    let (high, low) = if a >= b { (a, b) } else { (b, a) };
    if high == low {
        return high;
    }
    let index = high * (high - 1) / 2 + low;
    if hole[0] & 3 == hole[1] & 3 {
        13 + index
    } else {
        13 + 78 + index
    }
}

/// Chance of beating a random opponent hand on a complete board, ties counting half, against
/// every possible opponent hand.
pub fn river_strength(hole: [Card; 2], board: &[Card; 5]) -> f32 {
    let mut used = 0u64;
    for &c in hole.iter().chain(board) {
        used |= 1 << c;
    }
    let mut seven = [hole[0], hole[1], board[0], board[1], board[2], board[3], board[4]];
    let mine = score(&seven);
    let (mut points, mut hands) = (0u32, 0u32);
    for a in 0..52u8 {
        if used & (1 << a) != 0 {
            continue;
        }
        for b in (a + 1)..52 {
            if used & (1 << b) != 0 {
                continue;
            }
            seven[0] = a;
            seven[1] = b;
            let theirs = score(&seven);
            points += showdown_points(mine, theirs);
            hands += 1;
        }
    }
    points as f32 / (2 * hands) as f32
}

/// 2 for a win, 1 for a tie, 0 for a loss.
fn showdown_points(mine: u32, theirs: u32) -> u32 {
    match mine.cmp(&theirs) {
        std::cmp::Ordering::Greater => 2,
        std::cmp::Ordering::Equal => 1,
        std::cmp::Ordering::Less => 0,
    }
}

/// `river_strength` for two players on one board, sharing the work: every two-card hand the
/// remaining 47 cards can make is scored once (1,081 evaluations instead of 1,980).
pub fn river_strengths(holes: [[Card; 2]; 2], board: &[Card; 5]) -> [f32; 2] {
    let mut on_board = 0u64;
    for &c in board {
        on_board |= 1 << c;
    }
    let mut seven = [0, 0, board[0], board[1], board[2], board[3], board[4]];
    let mut hands: Vec<(u64, u32)> = Vec::with_capacity(1081);
    for a in 0..52u8 {
        if on_board & (1 << a) != 0 {
            continue;
        }
        for b in (a + 1)..52 {
            if on_board & (1 << b) != 0 {
                continue;
            }
            seven[0] = a;
            seven[1] = b;
            hands.push((1 << a | 1 << b, score(&seven)));
        }
    }
    let mut out = [0f32; 2];
    for (player, hole) in holes.into_iter().enumerate() {
        let mine_mask = 1u64 << hole[0] | 1 << hole[1];
        let mine = hands.iter().find(|(mask, _)| *mask == mine_mask).expect("own hand is listed").1;
        let (mut points, mut count) = (0u32, 0u32);
        for &(mask, theirs) in &hands {
            if mask & mine_mask == 0 {
                points += showdown_points(mine, theirs);
                count += 1;
            }
        }
        out[player] = points as f32 / (2 * count) as f32;
    }
    out
}

/// `river_strength` of every pair of hole cards on one board, indexed by `combo_index`; NaN for
/// pairs that use a board card. Each hand is scored once and the hands compared pairwise.
pub fn all_river_strengths(board: &[Card; 5]) -> Vec<f32> {
    let used = board_mask(board);
    let mut seven = [0, 0, board[0], board[1], board[2], board[3], board[4]];
    let mut hands: Vec<(usize, u64, u32)> = Vec::with_capacity(1081);
    for (index, hole) in all_combos().into_iter().enumerate() {
        let mask = 1u64 << hole[0] | 1 << hole[1];
        if used & mask == 0 {
            seven[0] = hole[0];
            seven[1] = hole[1];
            hands.push((index, mask, score(&seven)));
        }
    }
    let mut out = vec![f32::NAN; 1326];
    for &(index, mask, mine) in &hands {
        let (mut points, mut count) = (0u32, 0u32);
        for &(_, other_mask, theirs) in &hands {
            if other_mask & mask == 0 {
                points += showdown_points(mine, theirs);
                count += 1;
            }
        }
        out[index] = points as f32 / (2 * count) as f32;
    }
    out
}

/// E[HS²] estimated by sampling run-outs and opponent hands.
fn ehs2(hole: [Card; 2], board: &[Card], runouts: usize, opponents: usize, rng: &mut Rng) -> f32 {
    let mut used = 0u64;
    for &c in hole.iter().chain(board) {
        used |= 1 << c;
    }
    let mut total = 0.0f64;
    let mut seven = [0u8; 7];
    for _ in 0..runouts {
        let mut used_here = used;
        let mut full = [0u8; 5];
        full[..board.len()].copy_from_slice(board);
        for slot in full.iter_mut().skip(board.len()) {
            *slot = draw(rng, &mut used_here);
        }
        seven[2..].copy_from_slice(&full);
        seven[0] = hole[0];
        seven[1] = hole[1];
        let mine = score(&seven);
        let mut points = 0u32;
        for _ in 0..opponents {
            let mut used_opp = used_here;
            seven[0] = draw(rng, &mut used_opp);
            seven[1] = draw(rng, &mut used_opp);
            let theirs = score(&seven);
            points += showdown_points(mine, theirs);
        }
        let hs = points as f64 / (2 * opponents) as f64;
        total += hs * hs;
    }
    (total / runouts as f64) as f32
}

/// A random card not yet used, which is then marked used.
fn draw(rng: &mut Rng, used: &mut u64) -> Card {
    loop {
        let c = (rng.next_u64() % 52) as Card;
        if *used & (1 << c) == 0 {
            *used |= 1 << c;
            return c;
        }
    }
}

/// `n` different random cards.
pub fn deal(rng: &mut Rng, n: usize) -> Vec<Card> {
    let mut used = 0u64;
    (0..n).map(|_| draw(rng, &mut used)).collect()
}

/// Values that split `values` into `buckets` groups of equal size.
fn edges(mut values: Vec<f32>, buckets: usize) -> Vec<f32> {
    values.sort_unstable_by(|a, b| a.total_cmp(b));
    (1..buckets).map(|i| values[i * values.len() / buckets]).collect()
}

fn canonical_flops() -> Vec<u64> {
    let mut keys = parallel_collect(52, |a| {
        let a = a as u8;
        let mut out = Vec::new();
        for b in (a + 1)..52 {
            let rest: Vec<u8> = (0..52).filter(|c| *c != a && *c != b).collect();
            for i in 0..rest.len() {
                for j in (i + 1)..rest.len() {
                    for k in (j + 1)..rest.len() {
                        out.push(canon::key([a, b], &[rest[i], rest[j], rest[k]]));
                    }
                }
            }
        }
        out.sort_unstable();
        out.dedup();
        out
    });
    keys.sort_unstable();
    keys.dedup();
    keys
}

fn canonical_turns(flops: &[u64]) -> Vec<u64> {
    let chunk = flops.len().div_ceil(threads() * 4);
    let mut keys = parallel_collect(flops.len().div_ceil(chunk), |i| {
        let mut out = Vec::new();
        for &flop in &flops[i * chunk..((i + 1) * chunk).min(flops.len())] {
            let (hole, board) = canon::unpack(flop, 3);
            for c in 0..52u8 {
                if c != hole[0] && c != hole[1] && !board.contains(&c) {
                    out.push(canon::key(hole, &[board[0], board[1], board[2], c]));
                }
            }
        }
        out.sort_unstable();
        out.dedup();
        out
    });
    keys.sort_unstable();
    keys.dedup();
    keys
}

fn threads() -> usize {
    thread::available_parallelism().map_or(4, |n| n.get())
}

/// Runs `job(i)` for i in 0..jobs on all cores and joins the results.
fn parallel_collect(jobs: usize, job: impl Fn(usize) -> Vec<u64> + Sync) -> Vec<u64> {
    let next = std::sync::atomic::AtomicUsize::new(0);
    let parts: Vec<Vec<u64>> = thread::scope(|scope| {
        let workers: Vec<_> = (0..threads())
            .map(|_| {
                scope.spawn(|| {
                    let mut mine = Vec::new();
                    loop {
                        let i = next.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                        if i >= jobs {
                            return mine;
                        }
                        mine.extend(job(i));
                    }
                })
            })
            .collect();
        workers.into_iter().map(|w| w.join().unwrap()).collect()
    });
    parts.concat()
}

/// `f(key)` for every key, on all cores. Each key's random numbers come from its own seed, so
/// the result does not depend on how the work was split.
fn parallel_map(keys: &[u64], f: impl Fn(u64, &mut Rng) -> f32 + Sync, seed: u64) -> Vec<f32> {
    let mut values = vec![0f32; keys.len()];
    let chunk = keys.len().div_ceil(threads() * 8).max(1);
    let slices: Vec<(usize, &mut [f32])> = values.chunks_mut(chunk).enumerate().collect();
    let slices = std::sync::Mutex::new(slices);
    thread::scope(|scope| {
        for _ in 0..threads() {
            scope.spawn(|| loop {
                let job = slices.lock().unwrap().pop();
                let Some((index, slice)) = job else { return };
                for (offset, value) in slice.iter_mut().enumerate() {
                    let key = keys[index * chunk + offset];
                    let mut rng = Rng::new(seed ^ key.wrapping_mul(0x9E37_79B9_7F4A_7C15));
                    *value = f(key, &mut rng);
                }
            });
        }
    });
    values
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::holdem::eval::parse;

    #[test]
    fn canonical_deal_counts_match_the_published_ones() {
        // Waugh (2013): 1,286,792 suit-isomorphic flops and 13,960,050 turns.
        let flops = canonical_flops();
        assert_eq!(flops.len(), 1_286_792);
        assert_eq!(canonical_turns(&flops).len(), 13_960_050);
    }

    #[test]
    fn preflop_classes_cover_169_hands() {
        let mut seen = std::collections::HashSet::new();
        for a in 0..52u8 {
            for b in (a + 1)..52 {
                let class = preflop_class([a, b]);
                assert!(class < PREFLOP_BUCKETS);
                seen.insert(class);
            }
        }
        assert_eq!(seen.len(), PREFLOP_BUCKETS);
    }

    #[test]
    fn the_nuts_on_the_river_beats_everything() {
        let hole = parse("As Ks");
        let board = parse("Qs Js Ts 2h 3d");
        assert_eq!(
            river_strength([hole[0], hole[1]], &[board[0], board[1], board[2], board[3], board[4]]),
            1.0
        );
    }

    #[test]
    fn shared_river_strengths_match_the_single_ones() {
        let mut rng = Rng::new(4);
        for _ in 0..200 {
            let c = deal(&mut rng, 9);
            let holes = [[c[0], c[1]], [c[2], c[3]]];
            let board = [c[4], c[5], c[6], c[7], c[8]];
            let both = river_strengths(holes, &board);
            assert_eq!(both[0], river_strength(holes[0], &board));
            assert_eq!(both[1], river_strength(holes[1], &board));
        }
    }

    #[test]
    fn all_river_strengths_match_the_single_ones() {
        let board_cards = parse("Ah 7d 7c 2s Td");
        let board = [board_cards[0], board_cards[1], board_cards[2], board_cards[3], board_cards[4]];
        let all = all_river_strengths(&board);
        for hole in [parse("As Ad"), parse("7h 2h"), parse("Kc Qc")] {
            assert_eq!(all[combo_index(hole[0], hole[1])], river_strength([hole[0], hole[1]], &board));
        }
        assert!(all[combo_index(board[0], 0)].is_nan() || board[0] == 0);
    }

    #[test]
    fn a_draw_scores_above_its_current_strength() {
        // Nine-high flush draw: weak now, often strong by the river.
        let hole = parse("9h 8h");
        let mut rng = Rng::new(1);
        let draw = ehs2([hole[0], hole[1]], &parse("Ah 4h Kc"), 200, 200, &mut rng);
        let air = ehs2([parse("9c")[0], parse("8d")[0]], &parse("Ah 4h Kc"), 200, 200, &mut rng);
        assert!(draw > air + 0.05, "draw {draw} air {air}");
    }
}
