//! Hands that differ only by suit names play the same: A♠K♠ on Q♠J♠2♥ is A♥K♥ on Q♥J♥2♠. A
//! canonical key gives every such group one number, which shrinks the flop from 25.9 million
//! (hole cards, board) deals to 1.29 million, and the turn from 305 million to 14 million.

use super::eval::Card;

/// All 24 ways to rename the four suits.
const PERMUTATIONS: [[u8; 4]; 24] = permutations();

const fn permutations() -> [[u8; 4]; 24] {
    let mut out = [[0u8; 4]; 24];
    let mut n = 0;
    let mut a = 0;
    while a < 4 {
        let mut b = 0;
        while b < 4 {
            let mut c = 0;
            while c < 4 {
                let d = 6 - a - b - c;
                if a != b && a != c && b != c && d < 4 && d != a && d != b && d != c {
                    out[n] = [a as u8, b as u8, c as u8, d as u8];
                    n += 1;
                }
                c += 1;
            }
            b += 1;
        }
        a += 1;
    }
    out
}

/// Packs sorted hole cards then sorted board cards, six bits each.
fn pack(hole: [Card; 2], board: &mut [Card]) -> u64 {
    let (lo, hi) = if hole[0] < hole[1] { (hole[0], hole[1]) } else { (hole[1], hole[0]) };
    board.sort_unstable();
    let mut key = (lo as u64) << 6 | hi as u64;
    for &card in board.iter() {
        key = key << 6 | card as u64;
    }
    key
}

/// The canonical key of hole cards on a board of 0 to 5 cards: the smallest packed form over
/// every renaming of the suits. Keys of different board sizes never meet, because each street
/// has its own table.
pub fn key(hole: [Card; 2], board: &[Card]) -> u64 {
    let mut best = u64::MAX;
    let mut renamed = [0u8; 5];
    for perm in &PERMUTATIONS {
        let rename = |c: Card| (c & !3) | perm[(c & 3) as usize];
        for (slot, &card) in renamed.iter_mut().zip(board) {
            *slot = rename(card);
        }
        let packed = pack([rename(hole[0]), rename(hole[1])], &mut renamed[..board.len()]);
        best = best.min(packed);
    }
    best
}

/// The cards a key stands for (one member of its group).
pub fn unpack(key: u64, board_size: usize) -> ([Card; 2], Vec<Card>) {
    let card = |i: usize| ((key >> (6 * i)) & 63) as Card;
    let board: Vec<Card> = (0..board_size).rev().map(card).collect();
    ([card(board_size + 1), card(board_size)], board)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::holdem::eval::parse;

    fn hole(text: &str) -> [Card; 2] {
        let cards = parse(text);
        [cards[0], cards[1]]
    }

    #[test]
    fn suit_renaming_gives_the_same_key() {
        assert_eq!(key(hole("As Ks"), &parse("Qs Js 2h")), key(hole("Ah Kh"), &parse("Qh Jh 2c")));
        assert_eq!(key(hole("As Kd"), &parse("2c 7h 9s")), key(hole("Kc Ah"), &parse("9h 2d 7s")));
    }

    #[test]
    fn different_situations_keep_different_keys() {
        assert_ne!(key(hole("As Ks"), &parse("Qs Js 2h")), key(hole("As Ks"), &parse("Qh Jh 2s")));
        assert_ne!(key(hole("As Kd"), &[]), key(hole("As Ks"), &[]));
    }

    #[test]
    fn there_are_169_starting_hands() {
        let mut keys = std::collections::HashSet::new();
        for a in 0..52u8 {
            for b in (a + 1)..52 {
                keys.insert(key([a, b], &[]));
            }
        }
        assert_eq!(keys.len(), 169);
    }

    #[test]
    fn unpack_returns_cards_with_the_same_key() {
        let k = key(hole("7d 2c"), &parse("Ah 7s 2h Kd"));
        let (h, b) = unpack(k, 4);
        assert_eq!(key(h, &b), k);
    }
}
