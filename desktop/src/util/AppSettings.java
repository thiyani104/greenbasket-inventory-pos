package util;

import java.util.Properties;

/** Store-wide configuration, persisted to settings.properties. */
public class AppSettings {

    private String storeName = "Green Basket";
    private String storeAddress = "";
    private String currency = "Rs.";
    private double taxRate = 0;
    private int expiryWarnDays = 7;
    private int defaultReorderLevel = 10;
    private String receiptFooter = "Thank you for shopping green!";
    private double dailyTarget = 0;
    private boolean demoPrompted = false;

    public static AppSettings from(Properties p) {
        AppSettings s = new AppSettings();
        s.storeName = p.getProperty("store.name", s.storeName);
        s.storeAddress = p.getProperty("store.address", s.storeAddress);
        s.currency = p.getProperty("currency", s.currency);
        s.taxRate = parseD(p.getProperty("tax.rate"), s.taxRate);
        s.expiryWarnDays = (int) parseD(p.getProperty("expiry.warn.days"), s.expiryWarnDays);
        s.defaultReorderLevel = (int) parseD(p.getProperty("default.reorder"), s.defaultReorderLevel);
        s.receiptFooter = p.getProperty("receipt.footer", s.receiptFooter);
        s.dailyTarget = parseD(p.getProperty("daily.target"), s.dailyTarget);
        s.demoPrompted = Boolean.parseBoolean(p.getProperty("demo.prompted", "false"));
        return s;
    }

    public Properties toProperties() {
        Properties p = new Properties();
        p.setProperty("store.name", storeName);
        p.setProperty("store.address", storeAddress);
        p.setProperty("currency", currency);
        p.setProperty("tax.rate", String.valueOf(taxRate));
        p.setProperty("expiry.warn.days", String.valueOf(expiryWarnDays));
        p.setProperty("default.reorder", String.valueOf(defaultReorderLevel));
        p.setProperty("receipt.footer", receiptFooter);
        p.setProperty("daily.target", String.valueOf(dailyTarget));
        p.setProperty("demo.prompted", String.valueOf(demoPrompted));
        return p;
    }

    private static double parseD(String s, double def) {
        try {
            return s == null ? def : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String getStoreName() { return storeName; }
    public void setStoreName(String v) { storeName = v == null || v.isBlank() ? "Green Basket" : v.trim(); }
    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String v) { storeAddress = v == null ? "" : v.trim(); }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v == null ? "" : v.trim(); Formats.setCurrency(currency); }
    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double v) { taxRate = Math.max(0, Math.min(100, v)); }
    public int getExpiryWarnDays() { return expiryWarnDays; }
    public void setExpiryWarnDays(int v) { expiryWarnDays = Math.max(1, Math.min(365, v)); }
    public int getDefaultReorderLevel() { return defaultReorderLevel; }
    public void setDefaultReorderLevel(int v) { defaultReorderLevel = Math.max(0, v); }
    public String getReceiptFooter() { return receiptFooter; }
    public void setReceiptFooter(String v) { receiptFooter = v == null ? "" : v.trim(); }
    public double getDailyTarget() { return dailyTarget; }
    public void setDailyTarget(double v) { dailyTarget = Math.max(0, v); }
    public boolean isDemoPrompted() { return demoPrompted; }
    public void setDemoPrompted(boolean v) { demoPrompted = v; }
}
