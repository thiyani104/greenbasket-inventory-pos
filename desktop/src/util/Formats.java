package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Shared number / date formatting so every screen shows values the same way. */
public final class Formats {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter DATE_TIME_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter NICE_DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private static String currency = "Rs.";

    private Formats() {}

    public static void setCurrency(String symbol) {
        currency = (symbol == null || symbol.isBlank()) ? "" : symbol.trim();
    }

    public static String getCurrency() {
        return currency;
    }

    public static String number(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    public static String money(double v) {
        String sign = v < 0 ? "-" : "";
        String n = number(Math.abs(v));
        return currency.isEmpty() ? sign + n : sign + currency + " " + n;
    }

    /** Whole-number money for headline tiles. */
    public static String money0(double v) {
        String sign = v < 0 ? "-" : "";
        String n = String.format(Locale.US, "%,.0f", Math.abs(v));
        return currency.isEmpty() ? sign + n : sign + currency + " " + n;
    }

    public static String percent(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String dateTime(LocalDateTime d) {
        return d == null ? "" : DATE_TIME.format(d);
    }

    /** Parses yyyy-MM-dd; blank input returns null; bad input throws. */
    public static LocalDate parseDate(String s) throws DateTimeParseException {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim(), DATE);
    }
}
