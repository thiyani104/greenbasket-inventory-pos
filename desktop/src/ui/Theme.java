package ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Collections;
import java.util.Set;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

/** Colours, fonts and global Swing defaults. One place to restyle the whole app. */
public final class Theme {

    public static final Color PRIMARY = new Color(0x1E8E5A);
    public static final Color PRIMARY_DARK = new Color(0x15714A);
    public static final Color PRIMARY_LIGHT = new Color(0xE4F3EA);
    public static final Color SIDEBAR = new Color(0x0E2A1F);
    public static final Color SIDEBAR_HOVER = new Color(0x18412F);
    public static final Color SIDEBAR_TEXT = new Color(0xB9CFC3);
    public static final Color BG = new Color(0xF3F6F4);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xDCE5DF);
    public static final Color BORDER_SOFT = new Color(0xEBF0EC);
    public static final Color TEXT = new Color(0x1B2B24);
    public static final Color MUTED = new Color(0x6B7B73);
    public static final Color DANGER = new Color(0xD64545);
    public static final Color DANGER_LIGHT = new Color(0xFBE9E9);
    public static final Color WARN = new Color(0xD98A0B);
    public static final Color WARN_LIGHT = new Color(0xFDF1DB);
    public static final Color INFO = new Color(0x2F7DD1);
    public static final Color INFO_LIGHT = new Color(0xE5EFFB);
    public static final Color ORANGE = new Color(0xD9641F);
    public static final Color ORANGE_LIGHT = new Color(0xFCE9DD);

    private static final String FAMILY = pickFamily();

    private Theme() {}

    private static String pickFamily() {
        Set<String> avail = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String f : new String[]{"Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Roboto", "Noto Sans", "Ubuntu", "DejaVu Sans"}) {
            if (avail.contains(f)) return f;
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, 1).deriveFont(style, size);
    }

    public static Font mono(float size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(size);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Applies fonts and colours to every standard Swing component. Call once before creating windows. */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep the default look and feel
        }
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, new FontUIResource(font(Font.PLAIN, 13)));
            }
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("ToolTip.background", new Color(0x24352C));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
        UIManager.put("ToolTip.font", new FontUIResource(font(Font.PLAIN, 12)));
        UIManager.put("TextField.selectionBackground", PRIMARY_LIGHT);
        UIManager.put("TextField.selectionForeground", TEXT);
        UIManager.put("PasswordField.selectionBackground", PRIMARY_LIGHT);
        UIManager.put("PasswordField.selectionForeground", TEXT);
        UIManager.put("TextArea.selectionBackground", PRIMARY_LIGHT);
        UIManager.put("TextArea.selectionForeground", TEXT);
        UIManager.put("PopupMenu.border", javax.swing.BorderFactory.createLineBorder(BORDER));
        UIManager.put("PopupMenu.background", Color.WHITE);
        UIManager.put("MenuItem.background", Color.WHITE);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", PRIMARY_LIGHT);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("MenuItem.acceleratorForeground", MUTED);
        UIManager.put("MenuItem.acceleratorSelectionForeground", MUTED);
        UIManager.put("MenuItem.borderPainted", false);
        UIManager.put("OptionPane.background", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", PRIMARY_LIGHT);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("ToolTipManager.enableToolTipMode", "allWindows");
        javax.swing.ToolTipManager.sharedInstance().setInitialDelay(350);
    }
}
