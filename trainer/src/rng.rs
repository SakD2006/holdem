//! A small, fast, seedable random number generator (SplitMix64). Every sampled run can be
//! replayed exactly from its seed, on any machine.

pub struct Rng(u64);

/// SplitMix64's output step: a bijection that scrambles every bit of its input.
fn finish(mut z: u64) -> u64 {
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

impl Rng {
    pub fn new(seed: u64) -> Self {
        Rng(seed)
    }

    /// An independent generator for one of many parallel jobs (a thread, a deal, a table
    /// entry). The seed and the index are hashed together first: seeding with `seed ^ index`
    /// or `seed + index` makes streams that are copies of each other shifted by a few steps,
    /// because the generator itself only adds a constant each step.
    pub fn stream(seed: u64, index: u64) -> Self {
        Rng(finish(seed ^ finish(index ^ 0x632B_E59B_D9B4_E019)))
    }

    pub fn next_u64(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        finish(self.0)
    }

    /// Uniform in [0, 1).
    pub fn next_f64(&mut self) -> f64 {
        (self.next_u64() >> 11) as f64 * (1.0 / (1u64 << 53) as f64)
    }

    /// An index chosen with the given probabilities, which must add up to (about) 1.
    pub fn pick(&mut self, probs: &[f64]) -> usize {
        let mut x = self.next_f64();
        for (i, p) in probs.iter().enumerate() {
            if x < *p {
                return i;
            }
            x -= p;
        }
        probs.len() - 1
    }
}

#[cfg(test)]
mod tests {
    use super::Rng;

    #[test]
    fn same_seed_same_numbers() {
        let mut a = Rng::new(7);
        let mut b = Rng::new(7);
        for _ in 0..100 {
            assert_eq!(a.next_u64(), b.next_u64());
        }
    }

    #[test]
    fn streams_for_neighbouring_indices_do_not_overlap() {
        // Seeding with seed ^ (i * step) made thread i's numbers thread 0's, a few steps later.
        let firsts: Vec<u64> = {
            let mut a = Rng::stream(9, 0);
            (0..64).map(|_| a.next_u64()).collect()
        };
        for index in 1..32 {
            let mut b = Rng::stream(9, index);
            let theirs: Vec<u64> = (0..64).map(|_| b.next_u64()).collect();
            assert!(theirs.iter().all(|x| !firsts.contains(x)), "stream {index} repeats stream 0");
        }
    }

    #[test]
    fn pick_follows_the_probabilities() {
        let mut rng = Rng::new(1);
        let mut counts = [0usize; 3];
        for _ in 0..100_000 {
            counts[rng.pick(&[0.2, 0.5, 0.3])] += 1;
        }
        assert!((counts[0] as f64 / 100_000.0 - 0.2).abs() < 0.01);
        assert!((counts[1] as f64 / 100_000.0 - 0.5).abs() < 0.01);
        assert!((counts[2] as f64 / 100_000.0 - 0.3).abs() < 0.01);
    }
}
