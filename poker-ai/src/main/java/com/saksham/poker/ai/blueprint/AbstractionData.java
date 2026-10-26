package com.saksham.poker.ai.blueprint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The trainer's card abstraction (`trainer abstraction`, version 1 or 2 files): which bucket a hand
 * falls in on each street. Preflop: the 169 starting hands. Flop and turn: a table indexed by
 * canonical deal. River: equal-sized buckets by {@link Cards#riverStrength}.
 *
 * <p>Read from bytes, so this module does no file handling; the caller loads the file. Immutable
 * and safe to share between threads once built.
 */
public final class AbstractionData implements CardBuckets {

    private final int[] sizes;
    private final long[] flopKeys;
    private final byte[] flopBuckets;
    private final long[] turnKeys;
    private final byte[] turnBuckets;
    private final float[] riverEdges;

    private AbstractionData(int[] sizes, long[] flopKeys, byte[] flopBuckets, long[] turnKeys, byte[] turnBuckets,
            float[] riverEdges) {
        this.sizes = sizes;
        this.flopKeys = flopKeys;
        this.flopBuckets = flopBuckets;
        this.turnKeys = turnKeys;
        this.turnBuckets = turnBuckets;
        this.riverEdges = riverEdges;
    }

    /**
     * Reads a file written by {@code trainer abstraction}.
     *
     * @throws IllegalArgumentException if the bytes are not such a file, or use river buckets this
     *     port does not support
     */
    public static AbstractionData read(ByteBuffer bytes) {
        ByteBuffer in = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[8];
        in.get(magic);
        if (!new String(magic, StandardCharsets.US_ASCII).equals("HOLDEMAB")) {
            throw new IllegalArgumentException("Not an abstraction file: it does not start with HOLDEMAB.");
        }
        int version = in.getInt();
        if (version < 1 || version > 2) {
            throw new IllegalArgumentException("Abstraction file version " + version + " is not supported.");
        }
        int[] sizes = new int[4];
        for (int i = 0; i < 4; i++) {
            sizes[i] = in.getInt();
        }
        long[][] keys = new long[2][];
        byte[][] buckets = new byte[2][];
        for (int t = 0; t < 2; t++) {
            int n = Math.toIntExact(in.getLong());
            keys[t] = new long[n];
            in.asLongBuffer().get(keys[t]);
            in.position(in.position() + 8 * n);
            buckets[t] = new byte[n];
            in.get(buckets[t]);
        }
        int kind = version >= 2 ? in.get() : 0;
        if (kind != 0) {
            throw new IllegalArgumentException(
                    "This abstraction's river uses opponent-cluster buckets, which the Java port does not read yet. "
                            + "Build one with --river strength.");
        }
        float[] edges = new float[in.getInt()];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = in.getFloat();
        }
        return new AbstractionData(sizes, keys[0], buckets[0], keys[1], buckets[1], edges);
    }

    /** Buckets per street: preflop, flop, turn, river. */
    public int[] sizes() {
        return sizes.clone();
    }

    @Override
    public int bucket(int street, int h0, int h1, int[] board) {
        return switch (street) {
            case 0 -> Cards.preflopClass(h0, h1);
            case 1 -> lookup(flopKeys, flopBuckets, Canon.key(h0, h1, board, 3));
            case 2 -> lookup(turnKeys, turnBuckets, Canon.key(h0, h1, board, 4));
            default -> riverBucket(Cards.riverStrength(h0, h1, Arrays.copyOf(board, 5)));
        };
    }

    @Override
    public int[] rangeBuckets(int street, int[] board) {
        int shown = street == 0 ? 0 : street + 2;
        long used = 0;
        for (int i = 0; i < shown; i++) {
            used |= 1L << board[i];
        }
        int[] out = new int[Cards.COMBOS];
        if (street == 3) {
            float[] strengths = Cards.allRiverStrengths(Arrays.copyOf(board, 5));
            for (int i = 0; i < Cards.COMBOS; i++) {
                out[i] = Float.isNaN(strengths[i]) ? 0 : riverBucket(strengths[i]);
            }
            return out;
        }
        for (int i = 0; i < Cards.COMBOS; i++) {
            int[] hole = Cards.combo(i);
            if ((used & (1L << hole[0] | 1L << hole[1])) == 0) {
                out[i] = bucket(street, hole[0], hole[1], board);
            }
        }
        return out;
    }

    private int riverBucket(float strength) {
        // The number of edges at or below the strength, as Rust's partition_point counts them.
        int low = 0;
        int high = riverEdges.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (riverEdges[mid] <= strength) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static int lookup(long[] keys, byte[] buckets, long key) {
        int i = Arrays.binarySearch(keys, key);
        if (i < 0) {
            throw new IllegalStateException("Deal " + key + " is missing from the abstraction table.");
        }
        return buckets[i] & 0xFF;
    }
}
