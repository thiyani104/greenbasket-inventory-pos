package ui;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.User;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** The signed-in shell: sidebar navigation, top bar and the page area. */
public class MainFrame extends JFrame {

    public enum Nav {
        DASHBOARD("Dashboard", "dashboard", false),
        POS("Point of Sale", "cart", false),
        INVENTORY("Inventory", "box", false),
        PURCHASING("Purchasing", "truck", true),
        SALES("Sales", "receipt", false),
        REPORTS("Reports", "chart", true),
        CUSTOMERS("Customers", "star", true),
        USERS("Users", "users", true),
        ACTIVITY("Activity log", "activity", true),
        SETTINGS("Settings", "gear", true);

        final String title, icon;
        final boolean managerOnly;

        Nav(String title, String icon, boolean managerOnly) {
            this.title = title;
            this.icon = icon;
            this.managerOnly = managerOnly;
        }
    }

    private final User user;
    private final Map<Nav, Page> pages = new EnumMap<>(Nav.class);
    private final Map<Nav, NavItem> items = new EnumMap<>(Nav.class);
    private final Set<Nav> stale = EnumSet.noneOf(Nav.class);
    private final JPanel content = new JPanel(new CardLayout());
    private final Runnable dataListener = this::onDataChanged;
    private Nav current;
    private BellButton bell;

    public MainFrame(User user, Nav start) {
        super("Green Basket");
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 700));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1380, screen.width), Math.min(860, screen.height));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.bg);
        root.add(buildSidebar(), BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.bg);
        right.add(buildTopBar(), BorderLayout.NORTH);
        content.setOpaque(false);
        right.add(content, BorderLayout.CENTER);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);

        SystemContext.inventory.addListener(dataListener);
        SystemContext.sales.addListener(dataListener);
        SystemContext.purchases.addListener(dataListener);

        registerShortcuts();
        go(canSee(start) ? start : Nav.DASHBOARD);
        SwingUtilities.invokeLater(this::firstRunChecks);
    }

    /** Stops listening to services - call before discarding this window. */
    public void detach() {
        SystemContext.inventory.removeListener(dataListener);
        SystemContext.sales.removeListener(dataListener);
        SystemContext.purchases.removeListener(dataListener);
    }

    // ------------------------------------------------------------ accessors

    public User user() { return user; }
    public boolean isManager() { return user.isManager(); }
    public boolean canSee(Nav n) { return !n.managerOnly || isManager(); }

    public void toast(String msg) { Toast.ok(getRootPane(), msg); }
    public void toastError(String msg) { Toast.err(getRootPane(), msg); }
    public void toastInfo(String msg) { Toast.info(getRootPane(), msg); }

    // ----------------------------------------------------------- navigation

    public Page page(Nav n) {
        Page p = pages.get(n);
        if (p == null) {
            switch (n) {
                case DASHBOARD: p = new DashboardPage(this); break;
                case POS: p = new PosPage(this); break;
                case INVENTORY: p = new InventoryPage(this); break;
                case PURCHASING: p = new PurchasingPage(this); break;
                case SALES: p = new SalesPage(this); break;
                case REPORTS: p = new ReportsPage(this); break;
                case CUSTOMERS: p = new CustomersPage(this); break;
                case USERS: p = new UsersPage(this); break;
                case ACTIVITY: p = new ActivityPage(this); break;
                default: p = new SettingsPage(this);
            }
            pages.put(n, p);
            content.add(p, n.name());
        }
        return p;
    }

    public void go(Nav n) {
        if (!canSee(n)) return;
        Page p = page(n);
        current = n;
        ((CardLayout) content.getLayout()).show(content, n.name());
        stale.remove(n);
        p.onShow();
        for (Map.Entry<Nav, NavItem> e : items.entrySet()) e.getValue().setActive(e.getKey() == n);
        updateBadges();
    }

    private void onDataChanged() {
        stale.addAll(pages.keySet());
        stale.remove(current);
        if (current != null) page(current).refresh();
        updateBadges();
    }

    private void updateBadges() {
        int restock = SystemContext.inventory.needsRestock().size();
        NavItem inv = items.get(Nav.INVENTORY);
        if (inv != null) inv.setCount(restock);
        NavItem po = items.get(Nav.PURCHASING);
        if (po != null) po.setCount(SystemContext.purchases.open().size());
        if (bell != null) {
            int days = SystemContext.settings.getExpiryWarnDays();
            bell.setCount(restock + SystemContext.inventory.expired().size() + SystemContext.inventory.expiringSoon(days).size());
        }
    }

    // -------------------------------------------------------------- sidebar

    private JComponent buildSidebar() {
        JPanel s = new JPanel();
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setBackground(Theme.sidebar);
        s.setPreferredSize(new Dimension(248, 100));
        s.setBorder(new EmptyBorder(22, 14, 16, 14));

        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(0);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        brand.setBorder(new EmptyBorder(0, 8, 0, 0));
        brand.add(new LogoMark(38), BorderLayout.WEST);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(Ui.label(SystemContext.settings.getStoreName(), Theme.bold(15.5f), Color.WHITE));
        names.add(Ui.label("Inventory & POS", Theme.font(11.5f), Theme.sidebarMuted));
        brand.add(names, BorderLayout.CENTER);
        s.add(brand);
        s.add(Box.createVerticalStrut(26));

        String[][] sections = {{"MAIN", "DASHBOARD", "POS", "INVENTORY"},
                {"OPERATIONS", "PURCHASING", "SALES", "CUSTOMERS"},
                {"INSIGHTS", "REPORTS"},
                {"ADMIN", "USERS", "ACTIVITY", "SETTINGS"}};
        for (String[] sec : sections) {
            List<Nav> navs = new ArrayList<>();
            for (int i = 1; i < sec.length; i++) {
                Nav n = Nav.valueOf(sec[i]);
                if (canSee(n)) navs.add(n);
            }
            if (navs.isEmpty()) continue;
            JLabel l = Ui.label(sec[0], Theme.bold(10.5f), Theme.sidebarMuted);
            l.setBorder(new EmptyBorder(8, 12, 6, 0));
            l.setAlignmentX(0);
            s.add(l);
            for (Nav n : navs) {
                NavItem it = new NavItem(n);
                items.put(n, it);
                it.setAlignmentX(0);
                s.add(it);
                s.add(Box.createVerticalStrut(2));
            }
            s.add(Box.createVerticalStrut(8));
        }
        s.add(Box.createVerticalGlue());

        JPanel me = new JPanel(new BorderLayout(10, 0));
        me.setOpaque(false);
        me.setAlignmentX(0);
        me.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        me.setBorder(new EmptyBorder(10, 8, 0, 4));
        me.add(Ui.avatar(user.getFullName(), 36), BorderLayout.WEST);
        JPanel nm = new JPanel();
        nm.setOpaque(false);
        nm.setLayout(new BoxLayout(nm, BoxLayout.Y_AXIS));
        nm.add(Ui.label(user.getFullName(), Theme.bold(12.5f), Color.WHITE));
        nm.add(Ui.label(user.getRole().label(), Theme.font(11.5f), Theme.sidebarMuted));
        me.add(nm, BorderLayout.CENTER);
        s.add(me);
        return s;
    }

    private static class LogoMark extends JComponent {
        private final int size;
        LogoMark(int size) { this.size = size; }
        @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x34D27C), size, size, new Color(0x12894B)));
            g2.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, size, size, 12, 12));
            Icons.paint(g2, "basket", size * 0.2, size * 0.2, size * 0.6, Color.WHITE, 2f);
            g2.dispose();
        }
    }

    private class NavItem extends JComponent {
        private final Nav nav;
        private boolean active, hover;
        private int count;

        NavItem(Nav nav) {
            this.nav = nav;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setPreferredSize(new Dimension(200, 42));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { go(nav); }
            });
        }

        void setActive(boolean a) { active = a; repaint(); }
        void setCount(int c) { count = c; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            if (active) {
                Gfx.fillRound(g2, 0, 0, w, h, 10, Theme.sidebarActive);
                Gfx.fillRound(g2, 0, 10, 4, h - 20, 2, Theme.accent);
            } else if (hover) Gfx.fillRound(g2, 0, 0, w, h, 10, Theme.alpha(Theme.sidebarActive, 140));
            Color fg = active ? Color.WHITE : Theme.sidebarText;
            Icons.paint(g2, nav.icon, 16, (h - 20) / 2.0, 20, active ? Theme.accent : fg, 1.8f);
            Font f = active ? Theme.bold(13.5f) : Theme.font(13.5f);
            FontMetrics fm = g2.getFontMetrics(f);
            Gfx.text(g2, nav.title, 48, (h - fm.getHeight()) / 2.0 + fm.getAscent(), f, fg);
            if (count > 0) {
                String t = count > 99 ? "99+" : String.valueOf(count);
                Font bf = Theme.bold(10.5f);
                FontMetrics bm = g2.getFontMetrics(bf);
                int bw = Math.max(20, bm.stringWidth(t) + 12);
                Gfx.fillRound(g2, w - bw - 12, (h - 20) / 2.0, bw, 20, 10, nav == Nav.PURCHASING ? Theme.info : new Color(0xE5A526));
                Gfx.text(g2, t, w - bw - 12 + (bw - bm.stringWidth(t)) / 2.0, (h - bm.getHeight()) / 2.0 + bm.getAscent(), bf, new Color(0x1A1A1A));
            }
            g2.dispose();
        }
    }

    // -------------------------------------------------------------- top bar

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.surface);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(0, 30, 0, 22)));
        bar.setPreferredSize(new Dimension(100, 64));

        JLabel date = Ui.label(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                Theme.font(13), Theme.muted);
        bar.add(date, BorderLayout.WEST);

        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        r.setOpaque(false);
        bell = new BellButton();
        bell.addActionListener(e -> {
            if (!SystemContext.inventory.needsRestock().isEmpty() || !SystemContext.inventory.expired().isEmpty()
                    || !SystemContext.inventory.expiringSoon(SystemContext.settings.getExpiryWarnDays()).isEmpty()) {
                go(Nav.INVENTORY);
                ((InventoryPage) page(Nav.INVENTORY)).showAttention();
            } else toastInfo("All clear - nothing needs your attention.");
        });
        r.add(bell);
        Btn theme = new Btn("", Theme.dark ? "sun" : "moon", Btn.Kind.GHOST);
        theme.setToolTipText(Theme.dark ? "Switch to light mode" : "Switch to dark mode");
        theme.addActionListener(e -> {
            SystemContext.settings.setDarkMode(!Theme.dark);
            App.rebuild(current);
        });
        r.add(theme);
        r.add(Box.createHorizontalStrut(4));
        r.add(new UserChip());
        bar.add(r, BorderLayout.EAST);
        return bar;
    }

    private class BellButton extends Btn {
        private int count;
        BellButton() { super("", "alert", Btn.Kind.GHOST); setToolTipText("Stock alerts"); }
        void setCount(int c) { count = c; setToolTipText(c == 0 ? "No alerts" : c + " item(s) need attention"); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (count > 0) {
                Graphics2D g2 = Gfx.aa(g);
                g2.setColor(Theme.danger);
                g2.fillOval(getWidth() - 17, 7, 10, 10);
                g2.setColor(Theme.surface);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(getWidth() - 17, 7, 10, 10);
                g2.dispose();
            }
        }
    }

    private class UserChip extends JPanel {
        UserChip() {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(0, 4, 0, 4));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            add(Ui.avatar(user.getFullName(), 34), BorderLayout.WEST);
            JPanel t = new JPanel();
            t.setOpaque(false);
            t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
            t.add(Ui.label(user.getFullName(), Theme.bold(12.5f), Theme.text));
            t.add(Ui.label(user.getRole().label(), Theme.font(11.5f), Theme.muted));
            add(t, BorderLayout.CENTER);
            add(new JComponent() {
                @Override public Dimension getPreferredSize() { return new Dimension(14, 20); }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Gfx.aa(g);
                    g2.setColor(Theme.muted);
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(3, getHeight() / 2 - 2, 7, getHeight() / 2 + 2);
                    g2.drawLine(7, getHeight() / 2 + 2, 11, getHeight() / 2 - 2);
                    g2.dispose();
                }
            }, BorderLayout.EAST);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { menu().show(UserChip.this, 0, getHeight() + 6); }
            });
        }

        private JPopupMenu menu() {
            JPopupMenu m = new JPopupMenu();
            JMenuItem pw = new JMenuItem("Change password");
            pw.addActionListener(e -> ChangePasswordDialog.show(MainFrame.this, user, false));
            JMenuItem out = new JMenuItem("Sign out");
            out.addActionListener(e -> {
                SystemContext.log.log(user.getUsername(), "LOGOUT", "Signed out");
                App.showLogin();
            });
            m.add(pw);
            m.addSeparator();
            m.add(out);
            return m;
        }
    }

    // ------------------------------------------------------------ shortcuts

    private void registerShortcuts() {
        JRootPane rp = getRootPane();
        Nav[] order = Nav.values();
        for (int i = 0; i < order.length && i < 9; i++) {
            Nav n = order[i];
            rp.registerKeyboardAction(e -> go(n), KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        rp.registerKeyboardAction(e -> go(Nav.POS), KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void firstRunChecks() {
        if (SystemContext.auth.isUsingDefaultPassword(user)) {
            if (Dialogs.confirm(this, "Secure your account",
                    "You are still using the factory password for \"" + user.getUsername()
                            + "\". Anyone who knows it can sign in as the manager.\n\nChange it now?", "Change password", false)) {
                ChangePasswordDialog.show(this, user, false);
            }
        }
    }
}
