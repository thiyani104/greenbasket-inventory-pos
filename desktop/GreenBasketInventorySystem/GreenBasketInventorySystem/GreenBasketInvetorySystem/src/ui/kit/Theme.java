package ui.kit;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** Colour tokens, fonts and global Swing defaults. Call {@link #apply(boolean)} before building any window. */
public final class Theme {

    public static boolean dark;

    // surfaces
    public static Color bg, surface, surfaceAlt, border, borderStrong, hover;
    // text
    public static Color text, muted, faint;
    // brand + status
    public static Color primary, primaryHover, primaryDark, onPrimary, primarySoft;
    public static Color danger, dangerSoft, warning, warningSoft, success, successSoft, info, infoSoft;
    // sidebar
    public static Color sidebar, sidebarText, sidebarActive, sidebarMuted, accent;
    public static Color[] chart;

    private static String family = "Dialog";

    private Theme() { }

    private static Color c(int rgb) { return new Color(rgb); }

    public static void apply(boolean darkMode) {
        dark = darkMode;
        if (!dark) {
            bg = c(0xF3F6F4); surface = c(0xFFFFFF); surfaceAlt = c(0xF7FAF8); border = c(0xE2E9E4);
            borderStrong = c(0xCBD6CF); hover = c(0xEEF4F0);
            text = c(0x14201A); muted = c(0x5F7067); faint = c(0x93A29A);
            primary = c(0x1B9A57); primaryHover = c(0x158447); primaryDark = c(0x0F6A38); onPrimary = Color.WHITE;
            primarySoft = c(0xE3F4EA);
            danger = c(0xD64545); dangerSoft = c(0xFBE9E9); warning = c(0xC98A0B); warningSoft = c(0xFDF3DA);
            success = c(0x1B9A57); successSoft = c(0xE3F4EA); info = c(0x2F7DD1); infoSoft = c(0xE5F0FB);
            sidebar = c(0x0D2E21); sidebarText = c(0xC4D8CC); sidebarMuted = c(0x7E9C8D);
            sidebarActive = c(0x17493A); accent = c(0x4ADE80);
            chart = new Color[]{c(0x1B9A57), c(0x2F7DD1), c(0xE0A020), c(0x8E5CD6), c(0xE5645A), c(0x17A2B8), c(0x7A8B83)};
        } else {
            bg = c(0x0D1411); surface = c(0x151F1A); surfaceAlt = c(0x1A2620); border = c(0x25332B);
            borderStrong = c(0x344539); hover = c(0x1D2B24);
            text = c(0xE7F0EB); muted = c(0x9BAFA4); faint = c(0x66786E);
            primary = c(0x2DB86C); primaryHover = c(0x38C97A); primaryDark = c(0x1B9A57); onPrimary = c(0x06140D);
            primarySoft = c(0x173A29);
            danger = c(0xF06A6A); dangerSoft = c(0x3A1D1F); warning = c(0xE3A62A); warningSoft = c(0x3A2F14);
            success = c(0x2DB86C); successSoft = c(0x173A29); info = c(0x5AA3F0); infoSoft = c(0x182E47);
            sidebar = c(0x08120D); sidebarText = c(0xB5C9BE); sidebarMuted = c(0x6B8577);
            sidebarActive = c(0x14352A); accent = c(0x4ADE80);
            chart = new Color[]{c(0x2DB86C), c(0x5AA3F0), c(0xE3A62A), c(0xA582EA), c(0xF2837A), c(0x3CC2D6), c(0x8CA096)};
        }
        pickFont();
        installDefaults();
    }

    private static void pickFont() {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Roboto", "Noto Sans", "Open Sans", "Ubuntu", "DejaVu Sans"}) {
            if (have.contains(f)) { family = f; return; }
        }
        family = "Dialog";
    }

    public static Font font(float size) { return new Font(family, Font.PLAIN, 1).deriveFont(size); }
    public static Font bold(float size) { return new Font(family, Font.BOLD, 1).deriveFont(Font.BOLD, size); }
    public static Font mono(float size) { return new Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(size); }

    /** Colour that reads well as text on top of {@code bg}. */
    public static Color readableOn(Color bg) {
        double l = (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) / 255;
        return l > 0.6 ? c(0x14201A) : Color.WHITE;
    }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static void installDefaults() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        FontUIResource f = new FontUIResource(font(13));
        for (Object key : java.util.Collections.list(UIManager.getDefaults().keys())) {
            if (key.toString().endsWith(".font")) UIManager.put(key, f);
        }
        ColorUIResource bgR = new ColorUIResource(surface), fg = new ColorUIResource(text);
        UIManager.put("Panel.background", new ColorUIResource(bg));
        UIManager.put("OptionPane.background", bgR);
        UIManager.put("control", bgR);
        UIManager.put("Label.foreground", fg);
        UIManager.put("ToolTip.background", new ColorUIResource(dark ? c(0x2B3A32) : c(0x14201A)));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(5, 9, 5, 9));
        UIManager.put("ToolTip.font", font(12));
        UIManager.put("PopupMenu.background", bgR);
        UIManager.put("PopupMenu.border", javax.swing.BorderFactory.createLineBorder(border));
        UIManager.put("MenuItem.background", bgR);
        UIManager.put("MenuItem.foreground", fg);
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(hover));
        UIManager.put("MenuItem.selectionForeground", fg);
        UIManager.put("MenuItem.border", javax.swing.BorderFactory.createEmptyBorder(8, 14, 8, 22));
        UIManager.put("MenuItem.acceleratorForeground", new ColorUIResource(muted));
        UIManager.put("Separator.foreground", new ColorUIResource(border));
        UIManager.put("Separator.background", bgR);
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("TextArea.selectionBackground", new ColorUIResource(primarySoft));
        UIManager.put("TextArea.selectionForeground", fg);
        UIManager.put("TextField.selectionBackground", new ColorUIResource(Theme.mix(primary, surface, 0.7)));
        UIManager.put("TextField.selectionForeground", fg);
        UIManager.put("List.selectionBackground", new ColorUIResource(primarySoft));
        UIManager.put("List.selectionForeground", fg);
        UIManager.put("FileChooser.background", bgR);
    }
}
