package ui;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.User;
import service.AuthService;
import ui.kit.*;
import util.SystemContext;

/** Sign-in screen: brand panel on the left, credentials on the right. */
public class LoginFrame extends JFrame {

    private final Field username = new Field("Username").icon("user");
    private final PassField password = new PassField().hint("Password");
    private final JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.danger);
    private final Btn signIn = new Btn("Sign in", "check", Btn.Kind.PRIMARY);

    public LoginFrame() {
        super(SystemContext.settings.getStoreName() + " - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(980, 620);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(new BrandPanel(), BorderLayout.WEST);
        root.add(buildForm(), BorderLayout.CENTER);
        setContentPane(root);

        String last = SystemContext.settings.getLastUser();
        username.setText(last);
        signIn.addActionListener(e -> attempt());
        username.addActionListener(e -> password.requestFocusInWindow());
        password.addActionListener(e -> attempt());
        getRootPane().setDefaultButton(signIn);
        SwingUtilities.invokeLater(() -> (last.isEmpty() ? username : password).requestFocusInWindow());
    }

    private JComponent buildForm() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.surface);

        // top-right theme switch
        Btn theme = new Btn("", Theme.dark ? "sun" : "moon", Btn.Kind.GHOST);
        theme.setToolTipText(Theme.dark ? "Switch to light mode" : "Switch to dark mode");
        theme.addActionListener(e -> {
            SystemContext.settings.setDarkMode(!Theme.dark);
            App.showLogin();
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        top.setOpaque(false);
        top.add(theme);
        side.add(top, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, 0, 40, 0));
        form.setPreferredSize(new Dimension(380, 380));
        form.setMaximumSize(new Dimension(380, 480));

        JLabel h = Ui.label("Welcome back", Theme.bold(28), Theme.text);
        JLabel sub = Ui.label("Sign in to manage your store", Theme.font(14), Theme.muted);
        JLabel ul = Ui.label("Username", Theme.bold(12), Theme.muted);
        JLabel pl = Ui.label("Password", Theme.bold(12), Theme.muted);
        for (JComponent c : new JComponent[]{h, sub, ul, username, pl, password, error, signIn}) c.setAlignmentX(0);
        username.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        signIn.setPreferredSize(new Dimension(380, 44));

        form.add(h);
        form.add(Box.createVerticalStrut(4));
        form.add(sub);
        form.add(Box.createVerticalStrut(30));
        form.add(ul);
        form.add(Box.createVerticalStrut(6));
        form.add(username);
        form.add(Box.createVerticalStrut(16));
        form.add(pl);
        form.add(Box.createVerticalStrut(6));
        form.add(password);
        form.add(Box.createVerticalStrut(10));
        form.add(error);
        form.add(Box.createVerticalStrut(14));
        form.add(signIn);

        if (SystemContext.auth.isUsingDefaultPassword(SystemContext.auth.findByUsername(AuthService.DEFAULT_ADMIN))) {
            form.add(Box.createVerticalStrut(22));
            JPanel tip = new JPanel(new BorderLayout());
            tip.setBackground(Theme.primarySoft);
            tip.setBorder(new EmptyBorder(12, 14, 12, 14));
            JLabel t = Ui.wrapped("<b>First time here?</b> Sign in as <b>" + AuthService.DEFAULT_ADMIN + "</b> with password <b>"
                    + AuthService.DEFAULT_PASSWORD + "</b>. You will be asked to choose a new password.", 330,
                    Theme.font(12.5f), Theme.dark ? Theme.primaryHover : Theme.primaryDark);
            tip.add(t);
            tip.setAlignmentX(0);
            tip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            form.add(tip);
        }

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(form);
        side.add(center, BorderLayout.CENTER);
        return side;
    }

    private void attempt() {
        username.setInvalid(false);
        password.setInvalid(false);
        error.setText(" ");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            User u = SystemContext.auth.authenticate(username.getText(), password.text());
            SystemContext.settings.setLastUser(u.getUsername());
            App.signedIn(u);
        } catch (AuthService.AuthException ex) {
            error.setText(ex.getMessage());
            boolean blank = username.getText().isBlank();
            username.setInvalid(blank);
            password.setInvalid(true);
            password.setText("");
            (blank ? username : password).requestFocusInWindow();
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    /** Dark green brand panel with a soft decorative pattern. */
    private static class BrandPanel extends JPanel {
        BrandPanel() {
            setPreferredSize(new Dimension(430, 100));
            setOpaque(true);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(0x0F3A2A), w, h, new Color(0x0A2018)));
            g2.fillRect(0, 0, w, h);
            // soft glow circles
            g2.setPaint(new RadialGradientPaint(new Point(w - 40, h - 60), 320, new float[]{0f, 1f},
                    new Color[]{new Color(0x34D27C & 0xFFFFFF | 0x38000000, true), new Color(0x34D27C & 0xFFFFFF, true)}));
            g2.fill(new Ellipse2D.Double(w - 360, h - 380, 640, 640));
            g2.setPaint(new RadialGradientPaint(new Point(40, 40), 220, new float[]{0f, 1f},
                    new Color[]{new Color(0x4ADE80 & 0xFFFFFF | 0x26000000, true), new Color(0x4ADE80 & 0xFFFFFF, true)}));
            g2.fill(new Ellipse2D.Double(-180, -180, 440, 440));

            // logo
            int lx = 52, ly = 56;
            g2.setPaint(new GradientPaint(lx, ly, new Color(0x34D27C), lx + 46, ly + 46, new Color(0x12894B)));
            g2.fill(new RoundRectangle2D.Double(lx, ly, 46, 46, 14, 14));
            Icons.paint(g2, "basket", lx + 9, ly + 9, 28, Color.WHITE, 2.1f);
            Gfx.text(g2, SystemContext.settings.getStoreName(), lx + 60, ly + 22, Theme.bold(20), Color.WHITE);
            Gfx.text(g2, "Inventory & Point of Sale", lx + 60, ly + 41, Theme.font(12.5f), new Color(0x8FB7A2));

            Gfx.text(g2, "Run your store with", 52, h / 2.0 - 34, Theme.bold(30), Color.WHITE);
            Gfx.text(g2, "confidence.", 52, h / 2.0 + 4, Theme.bold(30), new Color(0x4ADE80));

            String[][] feats = {{"box", "Live stock levels, expiry dates and reorder alerts"},
                    {"cart", "A fast point of sale with receipts, holds and loyalty"},
                    {"chart", "Sales, profit and customer reports at a glance"}};
            int y = h / 2 + 46;
            for (String[] f : feats) {
                Gfx.fillRound(g2, 52, y, 34, 34, 10, new Color(255, 255, 255, 22));
                Icons.paint(g2, f[0], 59, y + 7, 20, new Color(0x4ADE80), 1.8f);
                Gfx.text(g2, f[1], 98, y + 22, Theme.font(13.5f), new Color(0xC4D8CC));
                y += 50;
            }
            Gfx.text(g2, "Fresh. Organic. Local.", 52, h - 36, Theme.font(12), new Color(0x6B8577));
            g2.dispose();
        }
    }
}
