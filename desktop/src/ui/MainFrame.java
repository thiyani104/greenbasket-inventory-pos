package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import model.User;
import util.Formats;
import util.SystemContext;

/** The application window: sidebar navigation, page header and the page area. */
public class MainFrame extends JFrame {

    private static MainFrame instance;

    private final User user = SystemContext.currentUser;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel title = UI.title("");
    private final JLabel subtitle = UI.muted(" ");
    private final JPanel alertChipHolder = UI.transparent(new BorderLayout());
    private final JLabel dateLabel = UI.label(Formats.NICE_DATE.format(LocalDate.now()), Font.PLAIN, 13, Theme.MUTED);
    private final JLabel storeLabel = UI.label(SystemContext.settings.getStoreName(), Font.BOLD, 16, Color.WHITE);
    private String currentKey;

    private static final class Entry {
        final String key;
        final String label;
        final Icons.Kind icon;
        final Supplier<Page> factory;
        final NavButton button;

        Entry(String key, String label, Icons.Kind icon, Supplier<Page> factory) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.factory = factory;
            this.button = new NavButton(label, icon);
        }
    }

    public MainFrame() {
        super(SystemContext.settings.getStoreName());
        instance = this;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { quit(); }
        });

        register("dashboard", "Dashboard", Icons.Kind.DASHBOARD, DashboardPage::new, true);
        register("pos", "Point of Sale", Icons.Kind.CART, PosPage::new, true);
        register("products", "Products", Icons.Kind.PRODUCTS, ProductsPage::new, true);
        register("stock", "Stock & Orders", Icons.Kind.STOCK, StockPage::new, user.canAdjustStock());
        register("sales", "Sales", Icons.Kind.RECEIPT, SalesPage::new, true);
        register("reports", "Reports", Icons.Kind.REPORT, ReportsPage::new, user.canViewReports());
        register("staff", "Staff", Icons.Kind.USERS, UsersPage::new, user.canManageUsers());
        register("activity", "Activity log", Icons.Kind.ACTIVITY, ActivityPage::new, user.canManageUsers());
        register("settings", "Settings", Icons.Kind.SETTINGS, SettingsPage::new, user.canChangeSettings());

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildMain(), BorderLayout.CENTER);
        setContentPane(root);

        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(1100, 680));
        setSize(Math.min(1380, screen.width), Math.min(860, screen.height));
        setLocationRelativeTo(null);

        bindShortcuts();
        SystemContext.clearListeners();
        SystemContext.addListener(this::updateBadges);
        showPage("dashboard");
    }

    public static MainFrame get() { return instance; }

    private void register(String key, String label, Icons.Kind icon, Supplier<Page> factory, boolean visible) {
        if (visible) entries.put(key, new Entry(key, label, icon, factory));
    }

    // ============================================================ layout

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.SIDEBAR);
        side.setPreferredSize(new Dimension(240, 100));

        // brand
        JPanel brand = UI.transparent(new BorderLayout(12, 0));
        brand.setBorder(BorderFactory.createEmptyBorder(26, 22, 22, 18));
        JComponent logo = new JComponent() {
            { setPreferredSize(new Dimension(40, 40)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PRIMARY);
                g2.fillRoundRect(0, 0, 40, 40, 14, 14);
                g2.dispose();
                Icons.of(Icons.Kind.LEAF, 22, Color.WHITE).paintIcon(this, g, 9, 9);
            }
        };
        JPanel logoWrap = UI.transparent(new BorderLayout());
        logoWrap.add(logo, BorderLayout.NORTH);
        brand.add(logoWrap, BorderLayout.WEST);
        JPanel names = UI.transparent(new BorderLayout());
        names.add(storeLabel, BorderLayout.CENTER);
        names.add(UI.label("Inventory & sales", Font.PLAIN, 11, Theme.SIDEBAR_TEXT), BorderLayout.SOUTH);
        brand.add(names, BorderLayout.CENTER);
        side.add(brand, BorderLayout.NORTH);

        // nav
        JPanel nav = UI.transparent(null);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 12));
        int n = 1;
        for (Entry e : entries.values()) {
            e.button.setAlignmentX(0);
            e.button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            e.button.setShortcutHint(n <= 9 ? "Ctrl+" + n : null);
            n++;
            e.button.addActionListener(() -> showPage(e.key));
            nav.add(e.button);
            nav.add(Box.createVerticalStrut(3));
        }
        side.add(nav, BorderLayout.CENTER);

        // user card
        JPanel bottom = UI.transparent(new BorderLayout(0, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 16, 18, 16));
        JPanel who = UI.transparent(new BorderLayout(10, 0));
        who.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        who.add(UI.avatar(user.getInitials(), 38, Theme.PRIMARY), BorderLayout.WEST);
        JPanel wt = UI.transparent(new BorderLayout());
        wt.add(UI.label(user.getFullName(), Font.BOLD, 13, Color.WHITE), BorderLayout.CENTER);
        wt.add(UI.label(user.getRole().getLabel(), Font.PLAIN, 11, Theme.SIDEBAR_TEXT), BorderLayout.SOUTH);
        who.add(wt, BorderLayout.CENTER);
        bottom.add(who, BorderLayout.NORTH);
        JPanel buttons = UI.transparent(new java.awt.GridLayout(1, 2, 8, 0));
        FlatButton pw = FlatButton.dark("Password").compact();
        FlatButton out = FlatButton.dark("Sign out").icon(Icons.Kind.LOGOUT).compact();
        pw.addActionListener(e -> {
            if (PasswordDialog.changeOwn(this, user)) Toast.success(this, "Password updated");
        });
        out.addActionListener(e -> signOut());
        buttons.add(pw);
        buttons.add(out);
        bottom.add(buttons, BorderLayout.SOUTH);
        JPanel sep = new JPanel(new BorderLayout());
        sep.setOpaque(false);
        sep.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 25)));
        sep.add(bottom);
        side.add(sep, BorderLayout.SOUTH);
        return side;
    }

    private JComponent buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);

        JPanel header = UI.transparent(new BorderLayout(16, 0));
        header.setBorder(BorderFactory.createEmptyBorder(22, 28, 14, 28));
        JPanel titles = UI.transparent(new BorderLayout(0, 2));
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.CENTER);
        header.add(titles, BorderLayout.CENTER);
        JPanel right = UI.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 14, 8));
        right.add(alertChipHolder);
        right.add(dateLabel);
        header.add(right, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        content.setOpaque(false);
        main.add(content, BorderLayout.CENTER);
        return main;
    }

    // ============================================================ navigation

    public void showPage(String key) {
        Entry e = entries.get(key);
        if (e == null) return;
        Page page = pages.get(key);
        if (page == null) {
            page = e.factory.get();
            pages.put(key, page);
            content.add(page, key);
        }
        currentKey = key;
        cards.show(content, key);
        for (Entry x : entries.values()) x.button.setSelected(x == e);
        title.setText(page.getTitle());
        subtitle.setText(page.getSubtitle().isEmpty() ? " " : page.getSubtitle());
        page.onShow();
        updateBadges();
        content.revalidate();
        content.repaint();
    }

    /** Picks up a changed store name. */
    public void refreshBrand() {
        setTitle(SystemContext.settings.getStoreName());
        storeLabel.setText(SystemContext.settings.getStoreName());
        dateLabel.setText(Formats.NICE_DATE.format(LocalDate.now()));
    }

    /** Signs out without asking (used after a restore replaced the accounts). */
    public void forceSignOut() {
        SystemContext.currentUser = null;
        SystemContext.clearListeners();
        instance = null;
        dispose();
        new LoginFrame().setVisible(true);
    }

    /** Re-reads the header text of the visible page (a page can change its own subtitle). */
    public void refreshHeader() {
        Page p = pages.get(currentKey);
        if (p == null) return;
        title.setText(p.getTitle());
        subtitle.setText(p.getSubtitle().isEmpty() ? " " : p.getSubtitle());
    }

    private void bindShortcuts() {
        var im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var am = getRootPane().getActionMap();
        int i = 1;
        for (String key : new ArrayList<>(entries.keySet())) {
            if (i > 9) break;
            String id = "nav-" + key;
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, java.awt.event.InputEvent.CTRL_DOWN_MASK), id);
            am.put(id, new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent ev) { showPage(key); } });
            i++;
        }
    }

    // ============================================================ alerts

    private void updateBadges() {
        int low = SystemContext.inventory.checkLowStockProducts().size();
        int expiring = SystemContext.inventory.getExpiringSoon().size();
        int expired = SystemContext.inventory.getExpired().size();
        int total = low + expiring + expired;
        Entry stock = entries.get("stock");
        if (stock != null) stock.button.setBadge(total);
        alertChipHolder.removeAll();
        if (total > 0) {
            UI.Badge chip = new UI.Badge(total + (total == 1 ? " stock alert" : " stock alerts"),
                    expired > 0 ? new Color(0xA82A2A) : new Color(0x9A6200), expired > 0 ? Theme.DANGER_LIGHT : Theme.WARN_LIGHT);
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.setToolTipText("Low stock: " + low + "  ·  Expiring soon: " + expiring + "  ·  Expired: " + expired);
            chip.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent ev) { showPage(entries.containsKey("stock") ? "stock" : "products"); }
            });
            alertChipHolder.add(chip, BorderLayout.CENTER);
        }
        alertChipHolder.revalidate();
        alertChipHolder.repaint();
    }

    // ============================================================ session

    private void signOut() {
        if (!Dialogs.confirm(this, "Sign out?", "You will return to the sign-in screen.", "Sign out", false)) return;
        SystemContext.log.add(user.getUsername(), "Logout", "Signed out");
        SystemContext.currentUser = null;
        SystemContext.clearListeners();
        instance = null;
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void quit() {
        if (Dialogs.confirm(this, "Exit " + SystemContext.settings.getStoreName() + "?", "Your data is saved automatically.", "Exit", false)) {
            SystemContext.log.add(user.getUsername(), "Logout", "Closed the application");
            dispose();
            System.exit(0);
        }
    }

    // ============================================================ nav button

    private static final class NavButton extends JComponent {
        private final String text;
        private final Icons.Kind icon;
        private boolean selected;
        private boolean hover;
        private int badge;
        private String hint;
        private Runnable action = () -> { };

        NavButton(String text, Icons.Kind icon) {
            this.text = text;
            this.icon = icon;
            setPreferredSize(new Dimension(200, 42));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
            });
        }

        void addActionListener(Runnable r) { action = r; }
        void setSelected(boolean s) { selected = s; repaint(); }
        void setBadge(int n) { badge = n; repaint(); }
        void setShortcutHint(String h) { hint = h; setToolTipText(h == null ? null : text + "  (" + h + ")"); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (selected) {
                g2.setColor(Theme.PRIMARY);
                g2.fillRoundRect(0, 0, w, h, 14, 14);
            } else if (hover) {
                g2.setColor(Theme.SIDEBAR_HOVER);
                g2.fillRoundRect(0, 0, w, h, 14, 14);
            }
            Color fg = selected ? Color.WHITE : hover ? Color.WHITE : Theme.SIDEBAR_TEXT;
            Icons.of(icon, 20, fg).paintIcon(this, g2, 14, (h - 20) / 2);
            g2.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 13));
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 46, (h - fm.getHeight()) / 2 + fm.getAscent());
            if (badge > 0) {
                String b = badge > 99 ? "99+" : String.valueOf(badge);
                g2.setFont(Theme.font(Font.BOLD, 11));
                FontMetrics bf = g2.getFontMetrics();
                int bw = Math.max(20, bf.stringWidth(b) + 12);
                int bx = w - bw - 12, by = (h - 20) / 2;
                g2.setColor(selected ? new Color(255, 255, 255, 60) : Theme.ORANGE);
                g2.fillRoundRect(bx, by, bw, 20, 20, 20);
                g2.setColor(Color.WHITE);
                g2.drawString(b, bx + (bw - bf.stringWidth(b)) / 2, by + (20 - bf.getHeight()) / 2 + bf.getAscent());
            }
            g2.dispose();
        }
    }
}
