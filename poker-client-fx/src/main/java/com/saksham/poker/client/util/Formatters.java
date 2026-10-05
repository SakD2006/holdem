package com.saksham.poker.client.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Turns numbers into the text shown on screen. */
public final class Formatters {

    private Formatters() {
    }

    /** Chips with thousands separators, such as {@code 12,500}. */
    public static String chips(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }

    /** A win or loss with its sign, such as {@code +300} or {@code -1,250}; zero has no sign. */
    public static String signed(long amount) {
        return (amount > 0 ? "+" : "") + chips(amount);
    }

    /** A seat as players count them: the first seat is 1, though the server numbers from 0. */
    public static String seat(int seatIndex) {
        return "Seat " + (seatIndex + 1);
    }
}
