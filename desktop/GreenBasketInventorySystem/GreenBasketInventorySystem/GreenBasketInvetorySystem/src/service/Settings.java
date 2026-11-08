package service;

import java.util.Properties;
import util.FileManager;
import util.Fmt;

/** Store-wide configuration persisted in data/settings.properties. */
public class Settings {

    private static final String FILE = "settings.properties";

    private final FileManager fm;
    private final Properties props;

    public Settings(FileManager fm) {
        this.fm = fm;
        this.props = fm.loadProperties(FILE);
        Fmt.setCurrency(getCurrency());
    }

    // ---- store profile
    public String getStoreName() { return props.getProperty("storeName", "Green Basket"); }
    public String getStoreAddress() { return props.getProperty("storeAddress", ""); }
    public String getStorePhone() { return props.getProperty("storePhone", ""); }
    public String getReceiptFooter() { return props.getProperty("receiptFooter", "Thank you for shopping with us!"); }

    // ---- money & stock rules
    public String getCurrency() { return props.getProperty("currency", "Rs."); }
    public double getTaxRate() { return num("taxRate", 0.0); }
    public int getExpiryWarnDays() { return (int) num("expiryWarnDays", 7); }
    public int getDefaultReorderLevel() { return (int) num("defaultReorderLevel", 10); }

    /** Largest manual discount (percent) a sales assistant may give at the till; managers are unlimited. */
    public int getAssistantMaxDiscount() { return (int) num("assistantMaxDiscount", 10); }

    // ---- behaviour
    public boolean isDarkMode() { return Boolean.parseBoolean(props.getProperty("darkMode", "false")); }
    public boolean isAutoBackup() { return Boolean.parseBoolean(props.getProperty("autoBackup", "true")); }
    public boolean isDemoOffered() { return Boolean.parseBoolean(props.getProperty("demoOffered", "false")); }

    public String getLastUser() { return props.getProperty("lastUser", ""); }
    public void setLastUser(String u) { put("lastUser", u == null ? "" : u); }

    public void setDarkMode(boolean dark) { put("darkMode", String.valueOf(dark)); }
    public void setAutoBackup(boolean on) { put("autoBackup", String.valueOf(on)); }
    public void setDemoOffered(boolean b) { put("demoOffered", String.valueOf(b)); }

    private void put(String key, String value) {
        props.setProperty(key, value);
        fm.saveProperties(FILE, props);
    }

    public void update(String storeName, String currency, double taxRate, int expiryWarnDays, int defaultReorder) {
        if (storeName == null || storeName.isBlank()) throw new IllegalArgumentException("Store name is required.");
        if (taxRate < 0 || taxRate > 100) throw new IllegalArgumentException("Tax rate must be between 0 and 100.");
        if (expiryWarnDays < 1 || expiryWarnDays > 365) throw new IllegalArgumentException("Expiry warning must be 1-365 days.");
        if (defaultReorder < 0) throw new IllegalArgumentException("Default reorder level cannot be negative.");
        props.setProperty("storeName", storeName.trim());
        props.setProperty("currency", currency == null || currency.isBlank() ? "Rs." : currency.trim());
        props.setProperty("taxRate", String.valueOf(taxRate));
        props.setProperty("expiryWarnDays", String.valueOf(expiryWarnDays));
        props.setProperty("defaultReorderLevel", String.valueOf(defaultReorder));
        fm.saveProperties(FILE, props);
        Fmt.setCurrency(getCurrency());
    }

    public void updateReceipt(String address, String phone, String footer, int assistantMaxDiscount) {
        if (assistantMaxDiscount < 0 || assistantMaxDiscount > 100)
            throw new IllegalArgumentException("The assistant discount limit must be between 0 and 100%.");
        props.setProperty("storeAddress", address == null ? "" : address.trim());
        props.setProperty("storePhone", phone == null ? "" : phone.trim());
        props.setProperty("receiptFooter", footer == null || footer.isBlank() ? "Thank you for shopping with us!" : footer.trim());
        props.setProperty("assistantMaxDiscount", String.valueOf(assistantMaxDiscount));
        fm.saveProperties(FILE, props);
    }

    private double num(String key, double def) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
