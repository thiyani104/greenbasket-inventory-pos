package util;

import model.Sale;
import model.SaleItem;

/** Builds the fixed-width text receipt shown on screen, printed or saved. */
public final class ReceiptFormatter {

    public static final int WIDTH = 42;

    private ReceiptFormatter() {}

    public static String format(Sale s, AppSettings settings) {
        StringBuilder sb = new StringBuilder();
        line(sb, center(settings.getStoreName().toUpperCase()));
        if (!settings.getStoreAddress().isBlank()) line(sb, center(settings.getStoreAddress()));
        line(sb, rule('='));
        line(sb, pair("Receipt", s.getReceiptNumber()));
        line(sb, pair("Date", Formats.DATE_TIME.format(s.getDateTime())));
        line(sb, pair("Cashier", s.getCashier()));
        line(sb, pair("Payment", s.getPayment().getLabel()));
        if (s.isVoided()) {
            line(sb, rule('-'));
            line(sb, center("*** VOIDED ***"));
        }
        line(sb, rule('-'));
        for (SaleItem i : s.getItems()) {
            line(sb, truncate(i.getProductName(), WIDTH));
            line(sb, pair("   " + i.getQuantity() + " x " + Formats.number(i.getUnitPrice()),
                    Formats.number(i.getLineTotal())));
        }
        line(sb, rule('-'));
        line(sb, pair("Subtotal", Formats.number(s.getSubtotal())));
        if (s.getDiscountPercent() > 0) {
            line(sb, pair("Discount (" + trim(s.getDiscountPercent()) + "%)", "-" + Formats.number(s.getDiscountAmount())));
        }
        if (s.getTaxRate() > 0) {
            line(sb, pair("Tax (" + trim(s.getTaxRate()) + "%)", Formats.number(s.getTaxAmount())));
        }
        line(sb, rule('='));
        line(sb, pair("TOTAL " + Formats.getCurrency(), Formats.number(s.getTotal())));
        line(sb, rule('='));
        if (s.getPayment() == model.PaymentMethod.CASH) {
            line(sb, pair("Cash tendered", Formats.number(s.getTendered())));
            line(sb, pair("Change", Formats.number(s.getChange())));
        }
        line(sb, pair("Items sold", String.valueOf(s.getItemCount())));
        if (!settings.getReceiptFooter().isBlank()) {
            line(sb, "");
            line(sb, center(settings.getReceiptFooter()));
        }
        return sb.toString();
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void line(StringBuilder sb, String text) { sb.append(text).append('\n'); }

    private static String rule(char c) { return String.valueOf(c).repeat(WIDTH); }

    private static String center(String t) {
        t = truncate(t, WIDTH);
        int pad = Math.max(0, (WIDTH - t.length()) / 2);
        return " ".repeat(pad) + t;
    }

    private static String pair(String left, String right) {
        int space = WIDTH - left.length() - right.length();
        if (space < 1) {
            left = truncate(left, Math.max(1, WIDTH - right.length() - 1));
            space = Math.max(1, WIDTH - left.length() - right.length());
        }
        return left + " ".repeat(space) + right;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
