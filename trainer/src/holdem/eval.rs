//! Hand evaluation: a port of `poker-ai`'s `FastHand`, with the same card numbers and the same
//! scores, so the trainer and the Java bots always agree on who wins.
//!
//! A card is 0..52: four times its rank (0 for a two up to 12 for an ace) plus its suit (0..4).
//! A score is one number per five-to-seven-card hand; bigger is better. Its category (high card
//! 0 up to straight flush 8) is `score >> 20`.

pub type Card = u8;

pub const STRAIGHT_FLUSH: u32 = 8;
pub const FOUR: u32 = 7;
pub const FULL_HOUSE: u32 = 6;
pub const FLUSH: u32 = 5;
pub const STRAIGHT: u32 = 4;
pub const THREE: u32 = 3;
pub const TWO_PAIR: u32 = 2;
pub const PAIR: u32 = 1;

pub fn rank(card: Card) -> u32 {
    (card >> 2) as u32
}

pub fn suit(card: Card) -> usize {
    (card & 3) as usize
}

pub fn category(score: u32) -> u32 {
    score >> 20
}

/// Scores the best five-card hand among 5 to 7 cards.
pub fn score(cards: &[Card]) -> u32 {
    let mut ranks = 0u32;
    let mut by_suit = [0u32; 4];
    // Four bits per rank: how many cards of that rank.
    let mut counts = 0u64;
    for &card in cards {
        let r = rank(card);
        let bit = 1 << r;
        ranks |= bit;
        by_suit[suit(card)] |= bit;
        counts += 1 << (r * 4);
    }

    let flush = by_suit.iter().copied().find(|s| s.count_ones() >= 5).unwrap_or(0);
    if flush != 0 {
        if let Some(high) = straight_high(flush) {
            return STRAIGHT_FLUSH << 20 | high;
        }
    }

    let (mut quads, mut trips, mut second_trips, mut pair, mut second_pair) = (None, None, None, None, None);
    for r in (0..13).rev() {
        match (counts >> (r * 4)) & 15 {
            4 => quads = Some(r),
            3 if trips.is_none() => trips = Some(r),
            3 if second_trips.is_none() => second_trips = Some(r),
            2 if pair.is_none() => pair = Some(r),
            2 if second_pair.is_none() => second_pair = Some(r),
            _ => {}
        }
    }

    if let Some(q) = quads {
        return FOUR << 20 | q << 4 | top(ranks & !(1 << q), 1);
    }
    if let Some(t) = trips {
        if pair.is_some() || second_trips.is_some() {
            let below = pair.unwrap_or(0).max(second_trips.unwrap_or(0));
            return FULL_HOUSE << 20 | t << 4 | below;
        }
    }
    if flush != 0 {
        return FLUSH << 20 | top(flush, 5);
    }
    if let Some(high) = straight_high(ranks) {
        return STRAIGHT << 20 | high;
    }
    if let Some(t) = trips {
        return THREE << 20 | t << 8 | top(ranks & !(1 << t), 2);
    }
    if let (Some(p), Some(q)) = (pair, second_pair) {
        return TWO_PAIR << 20 | p << 8 | q << 4 | top(ranks & !(1 << p) & !(1 << q), 1);
    }
    if let Some(p) = pair {
        return PAIR << 20 | p << 12 | top(ranks & !(1 << p), 3);
    }
    top(ranks, 5)
}

/// The top rank of the best straight in a set of ranks. The ace also plays low.
fn straight_high(ranks: u32) -> Option<u32> {
    (4..13).rev().find(|high| (ranks >> (high - 4)) & 0x1F == 0x1F).or(if ranks & 0x100F == 0x100F {
        Some(3)
    } else {
        None
    })
}

/// The highest `n` ranks of a set, packed four bits each, highest first; padded when there are
/// fewer, so lengths compare fairly.
fn top(ranks: u32, mut n: u32) -> u32 {
    let mut packed = 0;
    for r in (0..13).rev() {
        if n == 0 {
            break;
        }
        if ranks & (1 << r) != 0 {
            packed = packed << 4 | r;
            n -= 1;
        }
    }
    packed << (4 * n)
}

/// Parses "As Kd 7c" style text, for tests and tools.
pub fn parse(text: &str) -> Vec<Card> {
    text.split_whitespace()
        .map(|c| {
            let mut chars = c.chars();
            let r = "23456789TJQKA".find(chars.next().expect("rank")).expect("rank character") as u8;
            let s = "cdhs".find(chars.next().expect("suit")).expect("suit character") as u8;
            r * 4 + s
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn counts_over_all_hands(size: usize) -> [u64; 9] {
        let mut counts = [0u64; 9];
        let mut hand = vec![0u8; size];
        fn walk(hand: &mut Vec<u8>, depth: usize, start: u8, counts: &mut [u64; 9]) {
            if depth == hand.len() {
                counts[category(score(hand)) as usize] += 1;
                return;
            }
            for c in start..52 {
                hand[depth] = c;
                walk(hand, depth + 1, c + 1, counts);
            }
        }
        walk(&mut hand, 0, 0, &mut counts);
        counts
    }

    #[test]
    fn every_five_card_hand_falls_in_the_right_category() {
        // The standard counts over all 2,598,960 five-card hands.
        assert_eq!(
            counts_over_all_hands(5),
            [1_302_540, 1_098_240, 123_552, 54_912, 10_200, 5_108, 3_744, 624, 40]
        );
    }

    #[test]
    fn every_seven_card_hand_falls_in_the_right_category() {
        // The standard counts over all 133,784,560 seven-card hands.
        assert_eq!(
            counts_over_all_hands(7),
            [23_294_460, 58_627_800, 31_433_400, 6_461_620, 6_180_020, 4_047_644, 3_473_184, 224_848, 41_584]
        );
    }

    #[test]
    fn orders_hands_like_the_rules() {
        let s = |t: &str| score(&parse(t));
        assert!(s("As Ks Qs Js Ts 2c 3d") > s("9s 9h 9d 9c Ad 2c 3d")); // royal beats quads
        assert!(s("Ah 2d 3c 4s 5h Kd Qc") < s("2h 3d 4c 5s 6h Kd Qc")); // wheel is the lowest straight
        assert!(s("Ah Ad Kc Ks 2h 2d 3c") == s("Ah Ad Kc Ks 3h 3d 2c")); // third pair does not play
        assert_eq!(category(s("Kh Kd Kc 2s 2h 2d Ac")), FULL_HOUSE); // two sets make a full house
        assert!(s("Kh Kd Kc 2s 2h 2d Ac") == s("Kh Kd Kc 2s 2h Qd Jc")); // kings full of twos either way
        assert!(s("Ah Kh 9h 5h 2h 3c 4d") > s("Ah Qh Jh Th 8h 3c 4d")); // flushes compare top down
    }
}
