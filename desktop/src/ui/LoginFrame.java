package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import model.User;
import service.DemoData;
import service.ServiceException;
import util.SystemContext;

/** Sign-in screen: brand panel on the left, form on the right. */
public class LoginFrame extends JFrame {

    private final FlatTextField username = new FlatTextField(20).placeholder("Username");
    private final FlatPasswordField password = new FlatPasswordField(20).placeholder("Password").withRevealToggle();
    private final JLabel error = UI.label(" ", Font.PLAIN, 12, Theme.DANGER);
    private final FlatButton signIn = FlatButton.primary("Sign in");

    public LoginFrame() {
        super(SystemContext.settings.getStoreName() + " · Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.add(brandPanel(), BorderLayout.WEST);
        root.add(formPanel(), BorderLayout.CENTER);
        setContentPane(root);
        setSize(960, 600);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
        getRootPane().setDefaultButton(signIn);
        SwingUtilities.invokeLater(username::requestFocusInWindow);
    }

    private JComponent brandPanel() {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, Theme.SIDEBAR, getWidth(), getHeight(), new Color(0x1B6B49)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                // soft decorative circles
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillOval(-120, getHeight() - 260, 380, 380);
                g2.fillOval(getWidth() - 170, -90, 280, 280);
                g2.dispose();
            }
        };
        p.setPreferredSize(new Dimension(400, 100));
        p.setBorder(BorderFactory.createEmptyBorder(40, 44, 40, 44));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JPanel logo = new JPanel() {
            { setOpaque(false); setPreferredSize(new Dimension(56, 56)); setMinimumSize(new Dimension(56, 56)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PRIMARY);
                g2.fillRoundRect(0, 0, 56, 56, 18, 18);
                g2.dispose();
                Icons.of(Icons.Kind.LEAF, 30, Color.WHITE).paintIcon(this, g, 13, 13);
            }
        };
        JPanel logoWrap = UI.transparent(new BorderLayout());
        logoWrap.add(logo, BorderLayout.WEST);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 22, 0);
        p.add(logoWrap, c);

        JLabel name = UI.label(SystemContext.settings.getStoreName(), Font.BOLD, 30, Color.WHITE);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 8, 0);
        p.add(name, c);
        JLabel tag = UI.label("<html><body style='width:250px'>Inventory, sales and stock control for a fresh-food store.</body></html>", Font.PLAIN, 15, new Color(0xB9CFC3));
        c.gridy = 2;
        c.insets = new Insets(0, 0, 34, 0);
        p.add(tag, c);

        String[] points = {"Fast point of sale with receipts", "Stock ledger, reorder planning and expiry alerts",
            "Daily, weekly and monthly sales reports", "Staff accounts with role-based access"};
        int y = 3;
        for (String s : points) {
            JPanel row = UI.transparent(new BorderLayout(10, 0));
            JComponent tick = new JComponent() {
                { setPreferredSize(new Dimension(18, 18)); setMinimumSize(new Dimension(18, 18)); }
                @Override protected void paintComponent(Graphics g) {
                    Icons.of(Icons.Kind.CHECK, 16, new Color(0x6FD3A2)).paintIcon(this, g, 1, 1);
                }
            };
            row.add(tick, BorderLayout.WEST);
            row.add(UI.label("<html><body style='width:240px'>" + s + "</body></html>", Font.PLAIN, 13, new Color(0xD7E6DD)), BorderLayout.CENTER);
            c.gridy = y++;
            c.insets = new Insets(0, 0, 12, 0);
            p.add(row, c);
        }
        return p;
    }

    private JComponent formPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Color.WHITE);
        JPanel form = UI.transparent(new GridBagLayout());
        form.setPreferredSize(new Dimension(340, 400));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        int y = 0;
        c.gridy = y++;
        c.insets = new Insets(0, 0, 4, 0);
        form.add(UI.label("Welcome back", Font.BOLD, 26, Theme.TEXT), c);
        c.gridy = y++;
        c.insets = new Insets(0, 0, 26, 0);
        form.add(UI.muted("Sign in to continue to your store."), c);
        c.gridy = y++;
        c.insets = new Insets(0, 0, 5, 0);
        form.add(UI.label("Username", Font.BOLD, 12, Theme.TEXT), c);
        c.gridy = y++;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(username, c);
        c.gridy = y++;
        c.insets = new Insets(0, 0, 5, 0);
        form.add(UI.label("Password", Font.BOLD, 12, Theme.TEXT), c);
        c.gridy = y++;
        c.insets = new Insets(0, 0, 8, 0);
        form.add(password, c);
        c.gridy = y++;
        c.insets = new Insets(0, 0, 10, 0);
        form.add(error, c);
        signIn.setPreferredSize(new Dimension(100, 44));
        signIn.setFont(Theme.font(Font.BOLD, 14));
        c.gridy = y++;
        c.insets = new Insets(0, 0, 18, 0);
        form.add(signIn, c);

        if (firstRun()) {
            Card hint = new Card(new BorderLayout(0, 2)).padding(12, 14, 12, 14).fill(Theme.PRIMARY_LIGHT);
            hint.add(UI.label("First time here?", Font.BOLD, 12, Theme.PRIMARY_DARK), BorderLayout.NORTH);
            hint.add(UI.label("<html><body style='width:240px'>Sign in with <b>admin</b> and password <b>1234</b>. You will be asked to choose a new password.</body></html>",
                    Font.PLAIN, 12, Theme.TEXT), BorderLayout.CENTER);
            c.gridy = y++;
            c.insets = new Insets(0, 0, 0, 0);
            form.add(hint, c);
        }
        outer.add(form);
        signIn.addActionListener(e -> attempt());
        username.addActionListener(e -> password.requestFocusInWindow());
        return outer;
    }

    private static boolean firstRun() {
        User admin = SystemContext.users.findByUsername("admin");
        return admin != null && SystemContext.users.isUsingDefaultPassword(admin);
    }

    private void attempt() {
        error.setText(" ");
        username.setError(false);
        password.setError(false);
        try {
            User u = SystemContext.users.authenticate(username.getText(), new String(password.getPassword()));
            SystemContext.currentUser = u;
            afterSignIn(u);
        } catch (ServiceException ex) {
            error.setText("<html>" + ex.getMessage().replace("<", "&lt;") + "</html>");
            password.setError(true);
            password.setText("");
            password.requestFocusInWindow();
        }
    }

    private void afterSignIn(User u) {
        MainFrame main = new MainFrame();
        main.setVisible(true);
        dispose();
        // first-run conveniences, shown over the main window
        SwingUtilities.invokeLater(() -> {
            if (SystemContext.users.isUsingDefaultPassword(u)
                    && Dialogs.confirm(main, "Change the default password",
                    "You are still using the default password. Anyone who knows it can sign in as manager.",
                    "Change it now", false)) {
                PasswordDialog.changeOwn(main, u);
            }
            if (u.canChangeSettings() && SystemContext.inventory.size() == 0 && !SystemContext.settings.isDemoPrompted()) {
                SystemContext.settings.setDemoPrompted(true);
                SystemContext.files.saveSettings(SystemContext.settings);
                if (Dialogs.confirm(main, "Start with sample data?",
                        "Load about 30 grocery products, two sample staff accounts and six weeks of sales so you can explore every screen. "
                        + "You can add your own products at any time.", "Load sample data", false)) {
                    String msg = DemoData.load(SystemContext.inventory, SystemContext.sales, SystemContext.users, SystemContext.log, u);
                    SystemContext.fireChanged();
                    main.showPage("dashboard");
                    Toast.success(main, msg);
                }
            }
        });
    }
}
