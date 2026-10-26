//! k-means clustering on all cores, for grouping hands with similar equity histograms.
//!
//! Points are fixed-size arrays. Starting centres are chosen by k-means++ on a sample (each new
//! centre far from the ones already chosen); then Lloyd's iterations alternate between giving
//! every point its nearest centre and moving each centre to the mean of its points.

use std::thread;

use crate::rng::Rng;

fn distance<const D: usize>(a: &[f32; D], b: &[f32; D]) -> f32 {
    a.iter().zip(b).map(|(x, y)| (x - y) * (x - y)).sum()
}

fn nearest<const D: usize>(point: &[f32; D], centres: &[[f32; D]]) -> (usize, f32) {
    let mut best = (0, f32::INFINITY);
    for (i, c) in centres.iter().enumerate() {
        let d = distance(point, c);
        if d < best.1 {
            best = (i, d);
        }
    }
    best
}

/// One thread's share of a pass: centre sums, counts, total error, points that changed cluster.
type PassPart<const D: usize> = (Vec<[f64; D]>, Vec<u64>, f64, usize);

fn threads() -> usize {
    thread::available_parallelism().map_or(4, |n| n.get())
}

/// Groups `points` into `k` clusters; returns each point's cluster and the mean squared
/// distance of the points to their centres.
pub fn cluster<const D: usize>(
    points: &[[f32; D]],
    k: usize,
    iterations: usize,
    seed: u64,
    log: &(impl Fn(&str) + Sync),
) -> (Vec<u16>, f64) {
    assert!(k >= 1 && points.len() >= k, "need at least k points");
    let mut rng = Rng::new(seed);
    let mut centres = plus_plus(points, k, &mut rng);
    let mut labels = vec![0u16; points.len()];
    let mut error = 0.0;
    let chunk = points.len().div_ceil(threads()).max(1);
    for iteration in 0..iterations {
        // Assign every point and total up the new centres, one part of the points per thread.
        let parts: Vec<PassPart<D>> = thread::scope(|scope| {
            let workers: Vec<_> = points
                .chunks(chunk)
                .zip(labels.chunks_mut(chunk))
                .map(|(part, part_labels)| {
                    let centres = &centres;
                    scope.spawn(move || {
                        let mut sums = vec![[0f64; D]; k];
                        let mut counts = vec![0u64; k];
                        let (mut err, mut moved) = (0.0, 0usize);
                        for (p, label) in part.iter().zip(part_labels.iter_mut()) {
                            let (c, d) = nearest(p, centres);
                            if *label as usize != c {
                                moved += 1;
                                *label = c as u16;
                            }
                            err += d as f64;
                            counts[c] += 1;
                            for (s, x) in sums[c].iter_mut().zip(p) {
                                *s += *x as f64;
                            }
                        }
                        (sums, counts, err, moved)
                    })
                })
                .collect();
            workers.into_iter().map(|w| w.join().unwrap()).collect()
        });
        let mut sums = vec![[0f64; D]; k];
        let mut counts = vec![0u64; k];
        let mut moved = 0;
        error = 0.0;
        for (part_sums, part_counts, err, part_moved) in parts {
            for c in 0..k {
                counts[c] += part_counts[c];
                for (s, x) in sums[c].iter_mut().zip(&part_sums[c]) {
                    *s += x;
                }
            }
            error += err;
            moved += part_moved;
        }
        error /= points.len() as f64;
        for c in 0..k {
            if counts[c] == 0 {
                // An empty cluster restarts at a random point.
                centres[c] = points[(rng.next_u64() % points.len() as u64) as usize];
            } else {
                for (centre, s) in centres[c].iter_mut().zip(&sums[c]) {
                    *centre = (*s / counts[c] as f64) as f32;
                }
            }
        }
        log(&format!("k-means pass {}: mean error {error:.5}, {moved} points moved", iteration + 1));
        if iteration > 0 && moved * 10_000 < points.len() {
            break;
        }
    }
    (labels, error)
}

/// k-means++ starting centres, chosen from a sample of the points.
fn plus_plus<const D: usize>(points: &[[f32; D]], k: usize, rng: &mut Rng) -> Vec<[f32; D]> {
    let sample: Vec<&[f32; D]> = (0..points.len().min(200_000))
        .map(|_| &points[(rng.next_u64() % points.len() as u64) as usize])
        .collect();
    let mut centres = vec![*sample[0]];
    let mut closest: Vec<f32> = sample.iter().map(|p| distance(p, &centres[0])).collect();
    while centres.len() < k {
        let total: f64 = closest.iter().map(|d| *d as f64).sum();
        let next = if total > 0.0 {
            let mut x = rng.next_f64() * total;
            let mut pick = sample.len() - 1;
            for (i, d) in closest.iter().enumerate() {
                x -= *d as f64;
                if x <= 0.0 {
                    pick = i;
                    break;
                }
            }
            pick
        } else {
            (rng.next_u64() % sample.len() as u64) as usize
        };
        let centre = *sample[next];
        for (c, p) in closest.iter_mut().zip(&sample) {
            *c = c.min(distance(p, &centre));
        }
        centres.push(centre);
    }
    centres
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_well_separated_groups() {
        let mut rng = Rng::new(2);
        let mut points = Vec::new();
        for group in 0..4 {
            for _ in 0..500 {
                let jitter = (rng.next_f64() as f32 - 0.5) * 0.1;
                points.push([group as f32 + jitter, (group * group) as f32 - jitter]);
            }
        }
        let (labels, error) = cluster(&points, 4, 20, 1, &|_| {});
        assert!(error < 0.01, "error {error}");
        for group in 0..4 {
            let first = labels[group * 500];
            assert!(labels[group * 500..(group + 1) * 500].iter().all(|l| *l == first));
        }
        let mut distinct: Vec<u16> = (0..4).map(|g| labels[g * 500]).collect();
        distinct.sort_unstable();
        distinct.dedup();
        assert_eq!(distinct.len(), 4);
    }
}
