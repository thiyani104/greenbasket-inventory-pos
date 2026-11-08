package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Formatting and parsing helpers shared by the whole application. */
public final class Fmt {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_TIME_SHORT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    public static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");
    public static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE d");

    private static String currency = "Rs.";

    private Fmt() { }

    public static void setCurrency(String symbol) {
        currency = (symbol == null || symbol.isBlank()) ? "Rs." : symbol.trim();
    }

    public static String currency() {
        return currency;
    }

    public static String money(double v) {
        return currency + " " + String.format("%,.2f", v);
    }

    /** Compact money for chart axes: 1.2K, 3.4M. */
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return trim(v / 1_000_000) + "M";
        if (a >= 1_000) return trim(v / 1_000) + "K";
        return trim(v);
    }

    private static String trim(double v) {
        String s = String.format("%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    public static String dateTime(LocalDateTime t) {
        return t == null ? "" : t.format(DATE_TIME);
    }

    /** Parses yyyy-MM-dd. Returns null for blank input, throws for malformed input. */
    public static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date must be in the format yyyy-MM-dd (e.g. 2026-12-31).");
        }
    }

    public static LocalDateTime parseDateTime(String s) {
        try {
            return LocalDateTime.parse(s.trim(), DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public static double parseDouble(String s, String field) {
        try {
            double v = Double.parseDouble(s.trim().replace(",", ""));
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a valid number.");
        }
    }

    public static int parseInt(String s, String field) {
        try {
            return Integer.parseInt(s.trim().replace(",", ""));
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }
    }
}
