package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import service.DemoData;
import service.ServiceException;
import util.AppSettings;
import util.Formats;
import util.SystemContext;

/** Store details, tax, stock rules, backup / restore and sample data. */
public class SettingsPage extends Page {

    private final FlatTextField storeName = new FlatTextField(20);
    private final FlatTextField address = new FlatTextField(20).placeholder("Shown on receipts");
    private final FlatTextField currency = new FlatTextField(6).placeholder("Rs.");
    private final FlatTextField footer = new FlatTextField(20);
    private final FlatTextField tax = new FlatTextField(6).placeholder("0");
    private final FlatTextField target = new FlatTextField(8).placeholder("0 = no target");
    private final FlatTextField expiryDays = new FlatTextField(6);
    private final FlatTextField reorder = new FlatTextField(6);
    private final JLabel dataInfo = UI.muted(" ");
    private final JLabel aboutInfo = UI.muted(" ");
    private final JLabel preview = UI.muted(" ");

    public SettingsPage() {
        JPanel body = new ScrollColumn(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weighty = 0;

        // ---- left column: forms
        JPanel left = UI.transparent(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.weightx = 1;
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.anchor = GridBagConstraints.NORTHWEST;

        BaseDialog.FormPanel store = new BaseDialog.FormPanel();
        store.addRow("Store name", storeName);
        store.addRow("Address", address);
        store.addPair("Currency symbol", currency, "Receipt footer", footer);
        lc.gridy = 0;
        lc.insets = new Insets(0, 0, 16, 0);
        left.add(UI.section("Store details", null, store), lc);

        BaseDialog.FormPanel sales = new BaseDialog.FormPanel();
        sales.addPair("Sales tax (%)", tax, "Daily sales target", target);
        sales.addFull(preview);
        lc.gridy = 1;
        left.add(UI.section("Sales & tax", null, sales), lc);

        BaseDialog.FormPanel stock = new BaseDialog.FormPanel();
        stock.addPair("Warn about expiry within (days)", expiryDays, "Default reorder level", reorder);
        lc.gridy = 2;
        left.add(UI.section("Stock rules", null, stock), lc);

        FlatButton save = FlatButton.primary("Save settings").icon(Icons.Kind.CHECK);
        save.addActionListener(e -> save());
        FlatButton reset = FlatButton.secondary("Discard changes");
        reset.addActionListener(e -> load());
        lc.gridy = 3;
        lc.insets = new Insets(0, 0, 0, 0);
        left.add(UI.flow(FlowLayout.LEFT, 8, 0, save, reset), lc);

        // ---- right column: data & about
        JPanel right = UI.transparent(new GridBagLayout());
        GridBagConstraints rc = new GridBagConstraints();
        rc.gridx = 0;
        rc.weightx = 1;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.anchor = GridBagConstraints.NORTHWEST;

        JPanel data = UI.transparent(new BorderLayout(0, 12));
        data.add(UI.body("<html>Everything is saved automatically as CSV files you can open in Excel. A backup is also made once a day "
                + "(the last 7 are kept).</html>"), BorderLayout.NORTH);
        JPanel dataButtons = UI.transparent(new GridLayout(0, 1, 0, 8));
        FlatButton backup = FlatButton.secondary("Back up now…").icon(Icons.Kind.DOWNLOAD);
        backup.addActionListener(e -> backup());
        FlatButton restore = FlatButton.secondary("Restore from backup…").icon(Icons.Kind.UPLOAD);
        restore.addActionListener(e -> restore());
        FlatButton folder = FlatButton.secondary("Open data folder");
        folder.addActionListener(e -> openFolder());
        FlatButton demo = FlatButton.secondary("Add sample data…");
        demo.addActionListener(e -> loadDemo());
        dataButtons.add(backup);
        dataButtons.add(restore);
        dataButtons.add(folder);
        dataButtons.add(demo);
        JPanel dataBottom = UI.transparent(new BorderLayout(0, 10));
        dataBottom.add(dataButtons, BorderLayout.NORTH);
        dataBottom.add(dataInfo, BorderLayout.CENTER);
        data.add(dataBottom, BorderLayout.CENTER);
        rc.gridy = 0;
        rc.insets = new Insets(0, 0, 16, 0);
        right.add(UI.section("Data & backup", null, data), rc);

        JPanel about = UI.transparent(new BorderLayout());
        about.add(aboutInfo, BorderLayout.NORTH);
        rc.gridy = 1;
        rc.insets = new Insets(0, 0, 0, 0);
        right.add(UI.section("About", null, about), rc);

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 3;
        c.insets = new Insets(0, 0, 0, 16);
        body.add(left, c);
        c.gridx = 1;
        c.weightx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(right, c);
        GridBagConstraints fill = new GridBagConstraints();
        fill.gridy = 1;
        fill.weighty = 1;
        body.add(UI.transparent(null), fill);

        JScrollPane sp = UI.scroll(body);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(sp, BorderLayout.CENTER);

        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview(); }
        };
        tax.getDocument().addDocumentListener(dl);
        currency.getDocument().addDocumentListener(dl);
    }

    @Override public String getTitle() { return "Settings"; }

    @Override public String getSubtitle() { return "Store details, tax, stock rules and your data"; }

    @Override
    public void refresh() { load(); }

    private void load() {
        AppSettings s = SystemContext.settings;
        storeName.setText(s.getStoreName());
        address.setText(s.getStoreAddress());
        currency.setText(s.getCurrency());
        footer.setText(s.getReceiptFooter());
        tax.setText(trim(s.getTaxRate()));
        target.setText(s.getDailyTarget() > 0 ? trim(s.getDailyTarget()) : "");
        expiryDays.setText(String.valueOf(s.getExpiryWarnDays()));
        reorder.setText(String.valueOf(s.getDefaultReorderLevel()));
        dataInfo.setText("<html>Folder: " + SystemContext.files.getDir().toAbsolutePath() + "</html>");
        aboutInfo.setText("<html>Green Basket Inventory System<br>" + SystemContext.inventory.size() + " products · "
                + SystemContext.sales.getAll().size() + " sales · " + SystemContext.users.getAll().size() + " staff accounts · "
                + SystemContext.log.getAll().size() + " log entries<br>Java " + System.getProperty("java.version") + "</html>");
        updatePreview();
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private void updatePreview() {
        try {
            double rate = Double.parseDouble(tax.getText().trim().isEmpty() ? "0" : tax.getText().trim());
            String cur = currency.getText().trim();
            double total = 1000 * (1 + rate / 100);
            preview.setText("Example: a " + (cur.isEmpty() ? "" : cur + " ") + "1,000.00 sale becomes "
                    + (cur.isEmpty() ? "" : cur + " ") + Formats.number(total) + " with tax.");
        } catch (NumberFormatException e) {
            preview.setText(" ");
        }
    }

    private double number(FlatTextField f, String name, boolean allowEmpty) {
        String t = f.getText().trim().replace(",", "");
        if (t.isEmpty() && allowEmpty) return 0;
        try {
            double v = Double.parseDouble(t);
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            f.setError(true);
            throw new IllegalArgumentException(name + " must be a number, zero or more.");
        }
    }

    private void save() {
        for (FlatTextField f : new FlatTextField[]{tax, target, expiryDays, reorder, storeName}) f.setError(false);
        try {
            if (storeName.getText().isBlank()) { storeName.setError(true); throw new IllegalArgumentException("Store name is required."); }
            double rate = number(tax, "Sales tax", true);
            if (rate > 100) { tax.setError(true); throw new IllegalArgumentException("Sales tax cannot be more than 100%."); }
            double tgt = number(target, "Daily sales target", true);
            int warn = (int) number(expiryDays, "Expiry warning days", false);
            int re = (int) number(reorder, "Default reorder level", false);
            if (warn < 1 || warn > 365) { expiryDays.setError(true); throw new IllegalArgumentException("Expiry warning must be between 1 and 365 days."); }
            AppSettings s = SystemContext.settings;
            s.setStoreName(storeName.getText());
            s.setStoreAddress(address.getText());
            s.setCurrency(currency.getText());
            s.setReceiptFooter(footer.getText());
            s.setTaxRate(rate);
            s.setDailyTarget(tgt);
            s.setExpiryWarnDays(warn);
            s.setDefaultReorderLevel(re);
            SystemContext.files.saveSettings(s);
            SystemContext.log.add(SystemContext.actor(), "Settings", "Store settings updated");
            if (MainFrame.get() != null) MainFrame.get().refreshBrand();
            SystemContext.fireChanged();
            load();
            Toast.success(this, "Settings saved");
        } catch (IllegalArgumentException | ServiceException ex) {
            Toast.show(this, ex.getMessage(), Dialogs.Kind.ERROR);
        }
    }

    private void backup() {
        Path p = Dialogs.chooseSave(this, "Save backup", "green-basket-backup-" + Formats.date(LocalDate.now()) + ".zip");
        if (p == null) return;
        try {
            int n = SystemContext.files.backupTo(p);
            SystemContext.log.add(SystemContext.actor(), "Backup", "Saved " + n + " files to " + p.getFileName());
            Dialogs.success(this, "Backup saved", n + " data files were saved to " + p.getFileName() + ".");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void restore() {
        Path p = Dialogs.chooseOpen(this, "Choose a backup to restore");
        if (p == null) return;
        if (!Dialogs.confirm(this, "Restore this backup?",
                "This replaces ALL current products, sales, staff accounts and settings with the contents of the backup. "
                + "A safety copy of your current data is saved first. You will be signed out afterwards.", "Restore", true)) return;
        try {
            SystemContext.files.backupTo(SystemContext.files.getDir().resolve("before-restore-" + System.currentTimeMillis() + ".zip"));
            int n = SystemContext.files.restoreFrom(p);
            SystemContext.reload();
            SystemContext.log.add("system", "Restore", "Restored " + n + " files from " + p.getFileName());
            Dialogs.success(this, "Backup restored", "Data restored. Please sign in again.");
            if (MainFrame.get() != null) MainFrame.get().forceSignOut();
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void openFolder() {
        try {
            java.awt.Desktop.getDesktop().open(SystemContext.files.getDir().toFile());
        } catch (Exception ex) {
            Dialogs.info(this, "Data folder", SystemContext.files.getDir().toAbsolutePath().toString());
        }
    }

    private void loadDemo() {
        if (!Dialogs.confirm(this, "Add sample data?",
                "Adds about 30 grocery products (skipping any that already exist), two sample staff accounts "
                + "and six weeks of sales history if there are no sales yet. Your own data is not changed.", "Add sample data", false)) return;
        try {
            String msg = DemoData.load(SystemContext.inventory, SystemContext.sales, SystemContext.users, SystemContext.log, SystemContext.currentUser);
            SystemContext.fireChanged();
            load();
            Toast.success(this, msg);
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }
}
