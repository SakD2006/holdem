//! River buckets by opponent cluster hand strength (OCHS; Johanson et al., 2013).
//!
//! The 169 starting hands are grouped into a few clusters of similar strength. A river hand is
//! then described by its chance of beating a hand from each cluster, not just a random hand: top
//! pair with a weak kicker and a small two pair beat random hands about equally often, but fare
//! very differently against the strong hands an opponent still holds after three rounds of
//! betting. River hands are put in buckets by k-means on these vectors. There are far too many
//! river deals to list, so bucket centres are learned from a sample and a hand's bucket is its
//! nearest centre, found during play.

use std::thread;

use super::abstraction::{all_combos, deal, preflop_class, strength_cdf, PREFLOP_BUCKETS};
use super::eval::{score, Card};
use super::kmeans;
use crate::rng::Rng;

/// Clusters of starting hands.
pub const GROUPS: usize = 8;

pub type Features = [f32; GROUPS];

pub struct Ochs {
    /// Each starting hand's cluster, 0 the weakest.
    pub groups: Vec<u8>,
    /// Bucket centres, ordered from weakest to strongest.
    pub centres: Vec<Features>,
}

/// Hands that can still be held on a board: card mask, cluster, and score with the board.
struct Scored {
    index: usize,
    mask: u64,
    group: u8,
    score: u32,
}

impl Ochs {
    pub fn build(buckets: usize, samples: usize, seed: u64, log: &(impl Fn(&str) + Sync)) -> Ochs {
        log(&format!("grouping the 169 starting hands into {GROUPS} clusters"));
        let groups = starting_hand_groups(seed, log);
        log(&format!("river strength against each cluster for {samples} sample deals"));
        let threads = thread::available_parallelism().map_or(4, |n| n.get());
        let groups_ref = &groups;
        let points: Vec<Features> = thread::scope(|scope| {
            let workers: Vec<_> = (0..threads)
                .map(|t| {
                    scope.spawn(move || {
                        let mut rng = Rng::stream(seed ^ 0x0C45, t as u64);
                        let share = samples / threads + usize::from(t < samples % threads);
                        (0..share)
                            .map(|_| {
                                let c = deal(&mut rng, 7);
                                features_for(groups_ref, &[[c[0], c[1]]], &[c[2], c[3], c[4], c[5], c[6]])[0]
                            })
                            .collect::<Vec<_>>()
                    })
                })
                .collect();
            workers.into_iter().flat_map(|w| w.join().unwrap()).collect()
        });
        log(&format!("k-means into {buckets} river buckets"));
        let (labels, _) = kmeans::cluster(&points, buckets, 30, seed ^ 0x0C46, log);
        let mut sums = vec![([0f64; GROUPS], 0u64); buckets];
        for (label, p) in labels.iter().zip(&points) {
            let entry = &mut sums[*label as usize];
            for (s, x) in entry.0.iter_mut().zip(p) {
                *s += *x as f64;
            }
            entry.1 += 1;
        }
        let mut centres: Vec<Features> =
            sums.iter().map(|(s, n)| std::array::from_fn(|g| (s[g] / (*n).max(1) as f64) as f32)).collect();
        let shares = group_shares(&groups);
        let overall = |c: &Features| c.iter().zip(&shares).map(|(x, w)| *x as f64 * w).sum::<f64>();
        centres.sort_by(|a, b| overall(a).total_cmp(&overall(b)));
        Ochs { groups, centres }
    }

    /// The nearest centre.
    pub fn bucket(&self, features: &Features) -> u16 {
        let mut best = (0usize, f32::INFINITY);
        for (i, c) in self.centres.iter().enumerate() {
            let d: f32 = c.iter().zip(features).map(|(a, b)| (a - b) * (a - b)).sum();
            if d < best.1 {
                best = (i, d);
            }
        }
        best.0 as u16
    }

    pub fn features(&self, holes: &[[Card; 2]], board: &[Card; 5]) -> Vec<Features> {
        features_for(&self.groups, holes, board)
    }

    /// Features of every pair of hole cards on a board, by `combo_index`; `None` for pairs that
    /// use a board card.
    pub fn features_all(&self, board: &[Card; 5]) -> Vec<Option<Features>> {
        let hands = scored_hands(&self.groups, board);
        let mut out = vec![None; 1326];
        for h in &hands {
            out[h.index] = Some(against_groups(h.mask, h.score, &hands));
        }
        out
    }
}

/// Every two-card hand the cards off the board can make, scored once.
fn scored_hands(groups: &[u8], board: &[Card; 5]) -> Vec<Scored> {
    let used = board.iter().fold(0u64, |m, c| m | 1 << c);
    let mut seven = [0, 0, board[0], board[1], board[2], board[3], board[4]];
    let mut hands = Vec::with_capacity(1081);
    for (index, hole) in all_combos().into_iter().enumerate() {
        let mask = 1u64 << hole[0] | 1 << hole[1];
        if used & mask == 0 {
            seven[0] = hole[0];
            seven[1] = hole[1];
            hands.push(Scored { index, mask, group: groups[preflop_class(hole)], score: score(&seven) });
        }
    }
    hands
}

/// Chance of beating a hand from each cluster, ties counting half. A cluster with no hand left
/// (every one blocked by the cards in play) takes the chance against all hands.
fn against_groups(mask: u64, mine: u32, hands: &[Scored]) -> Features {
    let mut points = [0u32; GROUPS];
    let mut counts = [0u32; GROUPS];
    for other in hands {
        if other.mask & mask != 0 {
            continue;
        }
        let g = other.group as usize;
        points[g] += match mine.cmp(&other.score) {
            std::cmp::Ordering::Greater => 2,
            std::cmp::Ordering::Equal => 1,
            std::cmp::Ordering::Less => 0,
        };
        counts[g] += 1;
    }
    let overall = points.iter().sum::<u32>() as f32 / (2 * counts.iter().sum::<u32>()).max(1) as f32;
    std::array::from_fn(|g| if counts[g] > 0 { points[g] as f32 / (2 * counts[g]) as f32 } else { overall })
}

fn features_for(groups: &[u8], holes: &[[Card; 2]], board: &[Card; 5]) -> Vec<Features> {
    let hands = scored_hands(groups, board);
    holes
        .iter()
        .map(|hole| {
            let mask = 1u64 << hole[0] | 1 << hole[1];
            let mine = hands.iter().find(|h| h.mask == mask).expect("own hand is listed").score;
            against_groups(mask, mine, &hands)
        })
        .collect()
}

/// Share of all 1,326 hands in each cluster.
fn group_shares(groups: &[u8]) -> [f64; GROUPS] {
    let mut shares = [0f64; GROUPS];
    for hole in all_combos() {
        shares[groups[preflop_class(hole)] as usize] += 1.0 / 1326.0;
    }
    shares
}

/// Starting hands grouped by k-means on their histogram of river strength, each counted once
/// per card combination (a pair 6 times, suited 4, offsuit 12), numbered weakest first.
fn starting_hand_groups(seed: u64, log: &(impl Fn(&str) + Sync)) -> Vec<u8> {
    let mut representative = vec![None; PREFLOP_BUCKETS];
    let mut weight = vec![0usize; PREFLOP_BUCKETS];
    for hole in all_combos() {
        let class = preflop_class(hole);
        representative[class].get_or_insert(hole);
        weight[class] += 1;
    }
    let features: Vec<_> = representative
        .iter()
        .enumerate()
        .map(|(class, hole)| {
            let mut rng = Rng::stream(seed ^ 0x0C47, class as u64);
            strength_cdf(hole.expect("every class has a hand"), &[], 2000, 64, &mut rng)
        })
        .collect();
    let points: Vec<_> =
        features.iter().zip(&weight).flat_map(|(f, w)| std::iter::repeat(*f).take(*w)).collect();
    let (labels, _) = kmeans::cluster(&points, GROUPS, 50, seed ^ 0x0C48, log);
    // Each class's label (all its copies share one), then clusters ordered by mean strength.
    let mut class_label = vec![0u16; PREFLOP_BUCKETS];
    let mut at = 0;
    for (class, w) in weight.iter().enumerate() {
        class_label[class] = labels[at];
        at += w;
    }
    let mean = |f: &[f32; super::abstraction::BINS]| {
        let mut previous = 0.0;
        f.iter()
            .enumerate()
            .map(|(i, c)| {
                let m = (c - previous) as f64 * (i as f64 + 0.5);
                previous = *c;
                m
            })
            .sum::<f64>()
    };
    let mut strength = [(0.0f64, 0usize); GROUPS];
    for (class, f) in features.iter().enumerate() {
        let g = class_label[class] as usize;
        strength[g].0 += mean(f) * weight[class] as f64;
        strength[g].1 += weight[class];
    }
    let mut order: Vec<usize> = (0..GROUPS).collect();
    order.sort_by(|a, b| {
        let m = |g: usize| strength[g].0 / strength[g].1.max(1) as f64;
        m(*a).total_cmp(&m(*b))
    });
    let mut rank = [0u8; GROUPS];
    for (position, g) in order.into_iter().enumerate() {
        rank[g] = position as u8;
    }
    class_label.into_iter().map(|l| rank[l as usize]).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::holdem::eval::parse;

    fn groups_by_strength() -> Vec<u8> {
        // A cheap stand-in for the k-means grouping: pairs strongest, then suited, then the rest.
        (0..PREFLOP_BUCKETS)
            .map(|c| {
                if c < 13 {
                    7
                } else if c < 91 {
                    4
                } else {
                    1
                }
            })
            .collect()
    }

    #[test]
    fn features_of_every_hand_match_the_single_ones() {
        let ochs = Ochs { groups: groups_by_strength(), centres: vec![[0.5; GROUPS]] };
        let b = parse("Ah 7d 7c 2s Td");
        let board = [b[0], b[1], b[2], b[3], b[4]];
        let all = ochs.features_all(&board);
        for hole in [parse("As Ad"), parse("7h 2h"), parse("Kc Qc")] {
            let one = ochs.features(&[[hole[0], hole[1]]], &board)[0];
            assert_eq!(all[crate::holdem::abstraction::combo_index(hole[0], hole[1])], Some(one));
        }
    }

    #[test]
    fn the_nuts_beats_every_cluster_and_air_beats_none() {
        let ochs = Ochs { groups: groups_by_strength(), centres: vec![[0.5; GROUPS]] };
        let b = parse("Qs Js Ts 2h 3d");
        let board = [b[0], b[1], b[2], b[3], b[4]];
        let nuts = parse("As Ks");
        let f = ochs.features(&[[nuts[0], nuts[1]]], &board)[0];
        assert!(f.iter().all(|x| *x == 1.0), "{f:?}");
        let air = parse("4c 5c");
        let g = ochs.features(&[[air[0], air[1]]], &board)[0];
        assert!(g.iter().all(|x| *x < 0.2), "{g:?}");
    }

    #[test]
    fn nearest_centre_is_the_bucket() {
        let ochs =
            Ochs { groups: groups_by_strength(), centres: vec![[0.1; GROUPS], [0.5; GROUPS], [0.9; GROUPS]] };
        assert_eq!(ochs.bucket(&[0.45; GROUPS]), 1);
        assert_eq!(ochs.bucket(&[0.95; GROUPS]), 2);
    }
}
