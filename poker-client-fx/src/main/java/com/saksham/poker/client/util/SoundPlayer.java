package com.saksham.poker.client.util;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The table's sounds. They are worked out as waveforms when first needed rather than read from
 * sound files, so the app carries no recordings. Playing happens on a thread of its own: the window
 * never waits for the sound card.
 */
public final class SoundPlayer {

    /** The sounds the table can make. */
    public enum Sound {
        /** Hole cards sliding out. */
        DEAL,
        /** A community card landing. */
        CARD,
        /** A knock on the table. */
        CHECK,
        /** Chips going in. */
        CHIPS,
        /** Cards thrown away. */
        FOLD,
        /** A chime: you are to act. */
        YOUR_TURN,
        /** A rising run of notes: you won the pot. */
        WIN
    }

    private static final Logger log = LoggerFactory.getLogger(SoundPlayer.class);

    static final float RATE = 44_100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    /** Off until the app says otherwise, so tests and picture-drawing tools stay silent. */
    private static volatile boolean enabled;
    /** Set if this computer turned out to have no way to play sound; nothing is tried after that. */
    private static volatile boolean broken;
    /** Used only on the sound thread. */
    private static final Map<Sound, Clip> CLIPS = new EnumMap<>(Sound.class);
    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sound");
        thread.setDaemon(true);
        return thread;
    });

    private SoundPlayer() {
    }

    /** Turns sound on or off, as the player chose in Settings. */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Plays a sound, if sound is on. Returns at once. */
    public static void play(Sound sound) {
        if (!enabled || broken) {
            return;
        }
        THREAD.execute(() -> playNow(sound));
    }

    private static void playNow(Sound sound) {
        try {
            Clip clip = CLIPS.get(sound);
            if (clip == null) {
                byte[] samples = samples(sound);
                clip = AudioSystem.getClip();
                clip.open(FORMAT, samples, 0, samples.length);
                CLIPS.put(sound, clip);
            }
            // The same sound again before it has finished starts over, rather than being skipped.
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            broken = true;
            log.warn("This computer cannot play sound, so the game will be silent: {}", e.getMessage());
        }
    }

    // =====================================================================================
    // Making the waveforms
    // =====================================================================================

    /** The sound as 16-bit samples, low byte first, one channel at {@value #RATE} per second. */
    static byte[] samples(Sound sound) {
        float[] wave = switch (sound) {
            case DEAL -> deal();
            case CARD -> card();
            case CHECK -> check();
            case CHIPS -> chips();
            case FOLD -> fold();
            case YOUR_TURN -> yourTurn();
            case WIN -> win();
        };
        byte[] bytes = new byte[wave.length * 2];
        for (int i = 0; i < wave.length; i++) {
            int value = Math.round(Math.max(-1f, Math.min(1f, wave[i])) * 32_767);
            bytes[2 * i] = (byte) value;
            bytes[2 * i + 1] = (byte) (value >> 8);
        }
        return bytes;
    }

    private static float[] deal() {
        float[] wave = silence(330);
        for (int i = 0; i < 3; i++) {
            swish(wave, i * 95, 70, 0.22, 0.35, 11 + i);
        }
        return wave;
    }

    private static float[] card() {
        float[] wave = silence(140);
        swish(wave, 0, 80, 0.24, 0.3, 21);
        note(wave, 55, 420, 40, 0.10, 18);
        return wave;
    }

    private static float[] check() {
        float[] wave = silence(260);
        note(wave, 0, 170, 70, 0.55, 22);
        note(wave, 115, 150, 80, 0.45, 22);
        return wave;
    }

    private static float[] chips() {
        float[] wave = silence(260);
        double[] pitches = {2_650, 3_300, 2_900, 3_600};
        for (int i = 0; i < pitches.length; i++) {
            note(wave, i * 42, pitches[i], 60, 0.20, 16);
            swish(wave, i * 42, 14, 0.10, 0.9, 31 + i);
        }
        return wave;
    }

    private static float[] fold() {
        float[] wave = silence(220);
        swish(wave, 0, 200, 0.20, 0.12, 41);
        return wave;
    }

    private static float[] yourTurn() {
        float[] wave = silence(560);
        bell(wave, 0, 659.25, 300, 0.30);
        bell(wave, 150, 880.00, 400, 0.30);
        return wave;
    }

    private static float[] win() {
        float[] wave = silence(900);
        double[] run = {523.25, 659.25, 783.99, 1_046.50};
        for (int i = 0; i < run.length; i++) {
            bell(wave, i * 110, run[i], i == run.length - 1 ? 560 : 300, 0.26);
        }
        return wave;
    }

    private static float[] silence(int ms) {
        return new float[frames(ms)];
    }

    private static int frames(int ms) {
        return (int) (RATE * ms / 1_000);
    }

    /**
     * Adds a plain tone that starts sharply and dies away.
     *
     * @param decay how fast it dies: bigger is shorter
     */
    private static void note(float[] wave, int atMs, double hz, int ms, double volume, double decay) {
        int start = frames(atMs);
        int length = frames(ms);
        for (int i = 0; i < length && start + i < wave.length; i++) {
            double t = i / (double) RATE;
            double envelope = attack(i) * Math.exp(-decay * i / (double) length) * release(i, length);
            wave[start + i] += (float) (volume * envelope * Math.sin(2 * Math.PI * hz * t));
        }
    }

    /** Adds a tone with a softer overtone above it, which rings like a small bell. */
    private static void bell(float[] wave, int atMs, double hz, int ms, double volume) {
        note(wave, atMs, hz, ms, volume, 5);
        note(wave, atMs, hz * 2, ms, volume * 0.25, 8);
        note(wave, atMs, hz * 3, ms, volume * 0.08, 12);
    }

    /**
     * Adds a breath of hiss, the sound of a card or chips sliding.
     *
     * @param brightness 0 to 1: low is a dull rustle, high is a sharp tick
     * @param seed fixes the hiss, so a sound is the same every time it is made
     */
    private static void swish(float[] wave, int atMs, int ms, double volume, double brightness, long seed) {
        Random random = new Random(seed);
        int start = frames(atMs);
        int length = frames(ms);
        double smoothed = 0;
        for (int i = 0; i < length && start + i < wave.length; i++) {
            smoothed += brightness * (random.nextDouble() * 2 - 1 - smoothed);
            double position = i / (double) length;
            double envelope = Math.sin(Math.PI * position) * (1 - 0.5 * position);
            wave[start + i] += (float) (volume * envelope * smoothed / Math.sqrt(brightness));
        }
    }

    /** Eases a tone in over its first couple of milliseconds, so it does not start with a click. */
    private static double attack(int frame) {
        return Math.min(1, frame / (RATE * 0.002));
    }

    /** Eases a tone out over its last few milliseconds, so it does not end with a click. */
    private static double release(int frame, int length) {
        return Math.min(1, (length - frame) / (RATE * 0.004));
    }
}
