package com.saksham.poker.ai.blueprint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A trained strategy (`trainer holdem --save`): for each information set, the summed strategy of
 * training, whose proportions are the average strategy the blueprint plays.
 *
 * <p>Held as one open-addressing table of keys and one array of sums, rather than a map of objects:
 * a strategy has millions of information sets. Immutable and safe to share once built.
 */
public final class BlueprintTable {

    /** Strategy sums kept per information set. */
    static final int WIDTH = 6;

    private final long[] keys;
    private final float[] sums;
    private final int mask;
    private final int size;

    private BlueprintTable(long[] keys, float[] sums, int size) {
        this.keys = keys;
        this.sums = sums;
        this.mask = keys.length - 1;
        this.size = size;
    }

    /**
     * Reads a file written by {@code trainer holdem --save}.
     *
     * @throws IllegalArgumentException if the bytes are not such a file
     */
    public static BlueprintTable read(ByteBuffer bytes) {
        ByteBuffer in = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[8];
        in.get(magic);
        if (!new String(magic, StandardCharsets.US_ASCII).equals("HOLDEMBP")) {
            throw new IllegalArgumentException("Not a blueprint file: it does not start with HOLDEMBP.");
        }
        in.getLong(); // the writer's count, a hint only: the entries that follow are what count
        int entries = in.remaining() / (8 + 4 * WIDTH);
        // A power of two at most three-quarters full: about 130 MB for three million entries.
        int needed = Math.max(16, (int) Math.ceil(entries / 0.75));
        int capacity = Integer.highestOneBit(needed - 1) << 1;
        long[] keys = new long[capacity];
        float[] sums = new float[capacity * WIDTH];
        int size = 0;
        for (int e = 0; e < entries; e++) {
            long key = in.getLong();
            int slot = slotFor(keys, capacity - 1, key);
            if (keys[slot] == 0) {
                keys[slot] = key;
                size++;
            }
            for (int a = 0; a < WIDTH; a++) {
                sums[slot * WIDTH + a] = in.getFloat();
            }
        }
        return new BlueprintTable(keys, sums, size);
    }

    /** The slot holding {@code key}, or the empty slot where it would go. */
    private static int slotFor(long[] keys, int mask, long key) {
        int i = (int) mix(key) & mask;
        while (keys[i] != 0 && keys[i] != key) {
            i = (i + 1) & mask;
        }
        return i;
    }

    private static long mix(long key) {
        long z = key + 0x9E37_79B9_7F4A_7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    /** The trainer stores key 0 as 1, since 0 marks an empty slot. */
    private static long stored(long key) {
        return key == 0 ? 1 : key;
    }

    /** Information sets in the table. */
    public int size() {
        return size;
    }

    /** Whether training ever reached this information set. */
    public boolean contains(long key) {
        return keys[slotFor(keys, mask, stored(key))] != 0;
    }

    /**
     * The average strategy at {@code key} over {@code actions} legal actions, or {@code null} if
     * the table has no entry for it.
     */
    public double[] probabilities(long key, int actions) {
        int slot = slotFor(keys, mask, stored(key));
        if (keys[slot] == 0) {
            return null;
        }
        double[] out = new double[actions];
        double total = 0;
        for (int a = 0; a < actions; a++) {
            out[a] = Math.max(0, sums[slot * WIDTH + a]);
            total += out[a];
        }
        for (int a = 0; a < actions; a++) {
            out[a] = total > 0 ? out[a] / total : 1.0 / actions;
        }
        return out;
    }

    /**
     * What the blueprint plays at {@code key}: its average strategy, or, where it never trained,
     * check or call every time (as the trainer's own evaluation does).
     */
    public double[] strategyOrCall(long key, int[] legal) {
        double[] probs = probabilities(key, legal.length);
        if (probs != null) {
            return probs;
        }
        double[] call = new double[legal.length];
        for (int a = 0; a < legal.length; a++) {
            call[a] = legal[a] == AbstractHand.CALL ? 1 : 0;
        }
        return call;
    }
}
