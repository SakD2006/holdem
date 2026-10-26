//! External-sampling MCCFR on every core, sharing one table without locks.
//!
//! Each thread samples its own deals and walks the tree; all of them read and add to the same
//! regrets and strategy sums. Two threads occasionally update the same number at the same moment
//! and one update is lost. Sampling already adds far more noise than that, and Pluribus trained
//! the same way.
//!
//! Linear weighting (later iterations count more) is done as Pluribus did: training runs in
//! epochs, and after epoch e every regret and strategy sum is multiplied by e / (e + 1). Numbers
//! stay small enough for 32-bit floats however long training runs.

use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering::Relaxed};
use std::thread;

use crate::game::{Game, InfoKey, Turn};
use crate::rng::Rng;
use crate::solver::Policy;

/// The most actions an information set may have in a shared table.
pub const MAX_ACTIONS: usize = 6;

/// One information set: its key and, for each action, regret and strategy sum (f64 bits).
/// 64-bit floats matter: at the busiest nodes the sums reach tens of millions, where a 32-bit
/// float can no longer absorb an update of about one and training quietly stalls.
#[repr(align(128))]
struct Slot {
    key: AtomicU64,
    regrets: [AtomicU64; MAX_ACTIONS],
    sums: [AtomicU64; MAX_ACTIONS],
}

/// A fixed-size hash table of information sets that many threads can use at once.
pub struct SharedTable {
    slots: Vec<Slot>,
    mask: usize,
    used: AtomicUsize,
}

fn load(x: &AtomicU64) -> f64 {
    f64::from_bits(x.load(Relaxed))
}

fn add(x: &AtomicU64, delta: f64) {
    x.store((load(x) + delta).to_bits(), Relaxed);
}

/// Spreads small or patterned keys over the table.
fn mix(key: u64) -> u64 {
    let mut z = key.wrapping_add(0x9E37_79B9_7F4A_7C15);
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

impl SharedTable {
    /// A table for up to `2^bits` information sets (128 bytes each).
    pub fn new(bits: u32) -> Self {
        let size = 1usize << bits;
        let slots = (0..size)
            .map(|_| Slot { key: AtomicU64::new(0), regrets: Default::default(), sums: Default::default() })
            .collect();
        SharedTable { slots, mask: size - 1, used: AtomicUsize::new(0) }
    }

    pub fn len(&self) -> usize {
        self.used.load(Relaxed)
    }

    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    pub fn capacity(&self) -> usize {
        self.slots.len()
    }

    /// Key 0 marks an empty slot, so a real key of 0 is stored as 1.
    fn stored(key: InfoKey) -> u64 {
        if key == 0 {
            1
        } else {
            key
        }
    }

    fn find(&self, key: InfoKey) -> Option<&Slot> {
        let key = Self::stored(key);
        let mut i = mix(key) as usize & self.mask;
        loop {
            let slot = &self.slots[i];
            match slot.key.load(Relaxed) {
                0 => return None,
                k if k == key => return Some(slot),
                _ => i = (i + 1) & self.mask,
            }
        }
    }

    /// The slot for `key`, claimed if it is new.
    fn slot(&self, key: InfoKey) -> &Slot {
        let key = Self::stored(key);
        let mut i = mix(key) as usize & self.mask;
        loop {
            let slot = &self.slots[i];
            let k = slot.key.load(Relaxed);
            if k == key {
                return slot;
            }
            if k == 0 {
                match slot.key.compare_exchange(0, key, Relaxed, Relaxed) {
                    Ok(_) => {
                        let used = self.used.fetch_add(1, Relaxed) + 1;
                        assert!(
                            used * 10 < self.slots.len() * 9,
                            "information set table is full; raise --table-bits"
                        );
                        return slot;
                    }
                    Err(other) if other == key => return slot,
                    Err(_) => {}
                }
            }
            i = (i + 1) & self.mask;
        }
    }

    pub fn contains(&self, key: InfoKey) -> bool {
        self.find(key).is_some()
    }

    /// Multiplies every regret and strategy sum by `factor`.
    fn discount(&self, factor: f64, threads: usize) {
        let chunk = self.slots.len().div_ceil(threads);
        thread::scope(|scope| {
            for part in self.slots.chunks(chunk) {
                scope.spawn(move || {
                    for slot in part {
                        if slot.key.load(Relaxed) == 0 {
                            continue;
                        }
                        for x in slot.regrets.iter().chain(&slot.sums) {
                            x.store((load(x) * factor).to_bits(), Relaxed);
                        }
                    }
                });
            }
        });
    }
}

impl SharedTable {
    /// Writes the average strategy: every information set's key and strategy sums.
    pub fn save_strategy(&self, path: &std::path::Path) -> std::io::Result<usize> {
        use std::io::Write;
        if let Some(folder) = path.parent() {
            std::fs::create_dir_all(folder)?;
        }
        let mut out = std::io::BufWriter::new(std::fs::File::create(path)?);
        out.write_all(Blueprint::MAGIC)?;
        out.write_all(&(self.len() as u64).to_le_bytes())?;
        let mut written = 0;
        for slot in &self.slots {
            let key = slot.key.load(Relaxed);
            if key == 0 {
                continue;
            }
            out.write_all(&key.to_le_bytes())?;
            // Only the proportions matter when playing, so 32 bits each is plenty on disk.
            for sum in &slot.sums {
                out.write_all(&(load(sum) as f32).to_le_bytes())?;
            }
            written += 1;
        }
        out.flush()?;
        Ok(written)
    }
}

/// A trained strategy read back from a file: for each information set, its strategy sums.
pub struct Blueprint {
    sums: std::collections::HashMap<u64, [f32; MAX_ACTIONS]>,
}

impl Blueprint {
    const MAGIC: &'static [u8; 8] = b"HOLDEMBP";

    pub fn load(path: &std::path::Path) -> std::io::Result<Blueprint> {
        use std::io::Read;
        let mut input = std::io::BufReader::new(std::fs::File::open(path)?);
        let mut magic = [0u8; 8];
        input.read_exact(&mut magic)?;
        if &magic != Self::MAGIC {
            return Err(std::io::Error::new(std::io::ErrorKind::InvalidData, "not a blueprint file"));
        }
        let mut bytes8 = [0u8; 8];
        input.read_exact(&mut bytes8)?;
        let count = u64::from_le_bytes(bytes8) as usize;
        let mut sums = std::collections::HashMap::with_capacity(count);
        let mut bytes4 = [0u8; 4];
        // The count is only a hint: slots claimed during writing may add a few.
        while input.read_exact(&mut bytes8).is_ok() {
            let mut entry = [0f32; MAX_ACTIONS];
            for x in entry.iter_mut() {
                input.read_exact(&mut bytes4)?;
                *x = f32::from_le_bytes(bytes4);
            }
            sums.insert(u64::from_le_bytes(bytes8), entry);
        }
        Ok(Blueprint { sums })
    }

    pub fn len(&self) -> usize {
        self.sums.len()
    }

    pub fn is_empty(&self) -> bool {
        self.sums.is_empty()
    }
}

impl Policy for Blueprint {
    fn probs(&self, key: InfoKey, actions: usize) -> Vec<f64> {
        let Some(entry) = self.sums.get(&SharedTable::stored(key)) else {
            return vec![1.0 / actions as f64; actions];
        };
        let total: f64 = entry[..actions].iter().map(|s| s.max(0.0) as f64).sum();
        if total > 0.0 {
            entry[..actions].iter().map(|s| s.max(0.0) as f64 / total).collect()
        } else {
            vec![1.0 / actions as f64; actions]
        }
    }

    fn contains(&self, key: InfoKey) -> bool {
        self.sums.contains_key(&SharedTable::stored(key))
    }
}

/// Regret matching over the first `n` actions of a slot.
fn current_strategy(slot: &Slot, n: usize) -> [f64; MAX_ACTIONS] {
    let mut out = [0f64; MAX_ACTIONS];
    let mut total = 0.0;
    for (x, regret) in out.iter_mut().zip(&slot.regrets).take(n) {
        *x = load(regret).max(0.0);
        total += *x;
    }
    for x in out.iter_mut().take(n) {
        *x = if total > 0.0 { *x / total } else { 1.0 / n as f64 };
    }
    out
}

impl Policy for SharedTable {
    fn probs(&self, key: InfoKey, actions: usize) -> Vec<f64> {
        let Some(slot) = self.find(key) else {
            return vec![1.0 / actions as f64; actions];
        };
        let sums: Vec<f64> = (0..actions).map(|i| load(&slot.sums[i]).max(0.0)).collect();
        let total: f64 = sums.iter().sum();
        if total > 0.0 {
            sums.iter().map(|s| s / total).collect()
        } else {
            vec![1.0 / actions as f64; actions]
        }
    }

    fn contains(&self, key: InfoKey) -> bool {
        self.find(key).is_some()
    }
}

pub struct ParallelMccfr<G: Game + Sync> {
    game: G,
    table: SharedTable,
    threads: usize,
    seed: u64,
    iterations: u64,
    epoch: u64,
    epochs_done: u64,
}

impl<G: Game + Sync> ParallelMccfr<G>
where
    G::State: Send,
{
    /// `epoch` iterations pass between discounts.
    pub fn new(game: G, table_bits: u32, threads: usize, epoch: u64, seed: u64) -> Self {
        ParallelMccfr {
            game,
            table: SharedTable::new(table_bits),
            threads: threads.max(1),
            seed,
            iterations: 0,
            epoch: epoch.max(1),
            epochs_done: 0,
        }
    }

    pub fn iterations(&self) -> u64 {
        self.iterations
    }

    pub fn table(&self) -> &SharedTable {
        &self.table
    }

    /// Trains until `target` iterations in all (one iteration updates both players).
    pub fn run_until(&mut self, target: u64) {
        while self.iterations < target {
            let epoch_end = (self.iterations / self.epoch + 1) * self.epoch;
            let stop = epoch_end.min(target);
            self.run_batch(stop - self.iterations);
            self.iterations = stop;
            if stop == epoch_end {
                self.epochs_done += 1;
                let e = self.epochs_done as f64;
                self.table.discount(e / (e + 1.0), self.threads);
            }
        }
    }

    fn run_batch(&self, count: u64) {
        let threads = self.threads as u64;
        let batch_seed = self.seed ^ self.iterations.wrapping_mul(0xD1B5_4A32_D192_ED03);
        thread::scope(|scope| {
            for t in 0..threads {
                let share = count / threads + u64::from(t < count % threads);
                let game = &self.game;
                let table = &self.table;
                scope.spawn(move || {
                    let mut rng = Rng::stream(batch_seed, t);
                    let root = game.root();
                    for _ in 0..share {
                        // Both players' walks share one deal: dealing (bucketing nine cards)
                        // is a large part of an iteration's cost.
                        let start = match game.turn(&root) {
                            Turn::Chance => game.apply(&root, game.sample_chance(&root, &mut rng)),
                            _ => root.clone(),
                        };
                        for traverser in 0..2 {
                            walk(game, table, &start, traverser, &mut rng);
                        }
                    }
                });
            }
        });
    }
}

/// One sampled walk updating `traverser`'s regrets; returns its sampled value.
fn walk<G: Game>(game: &G, table: &SharedTable, state: &G::State, traverser: usize, rng: &mut Rng) -> f64 {
    match game.turn(state) {
        Turn::Terminal => game.payoff(state, traverser),
        Turn::Chance => {
            let outcome = game.sample_chance(state, rng);
            walk(game, table, &game.apply(state, outcome), traverser, rng)
        }
        Turn::Player(player) => {
            let actions = game.legal_actions(state);
            let n = actions.len();
            debug_assert!(n <= MAX_ACTIONS);
            let slot = table.slot(game.info_key(state, player));
            let strategy = current_strategy(slot, n);
            if player == traverser {
                let mut values = [0f64; MAX_ACTIONS];
                let mut value = 0.0;
                for (i, action) in actions.iter().enumerate() {
                    values[i] = walk(game, table, &game.apply(state, *action), traverser, rng);
                    value += strategy[i] * values[i];
                }
                for (regret, v) in slot.regrets.iter().zip(&values).take(n) {
                    add(regret, v - value);
                }
                value
            } else {
                for (sum, p) in slot.sums.iter().zip(&strategy).take(n) {
                    add(sum, *p);
                }
                let choice = rng.pick(&strategy[..n]);
                walk(game, table, &game.apply(state, actions[choice]), traverser, rng)
            }
        }
    }
}
