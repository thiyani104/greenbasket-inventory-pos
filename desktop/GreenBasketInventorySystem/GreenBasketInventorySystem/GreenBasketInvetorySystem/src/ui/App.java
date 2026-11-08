package ui;

import javax.swing.JFrame;
import model.User;
import ui.kit.Theme;
import util.SystemContext;

/** Application lifecycle: start-up, sign-in, sign-out, theme switch and restore. */
public final class App {

    private static JFrame current;

    private App() { }

    public static void start() {
        try {
            SystemContext.init();
        } catch (RuntimeException e) {
            javax.swing.JOptionPane.showMessageDialog(null, "Green Basket could not open its data folder:\n" + e.getMessage(),
                    "Start-up problem", javax.swing.JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        SystemContext.backup.autoBackup();
        Theme.apply(SystemContext.settings.isDarkMode());
        showLogin();
    }

    private static void swap(JFrame next) {
        JFrame old = current;
        current = next;
        next.setVisible(true);
        if (old != null) {
            if (old instanceof MainFrame) ((MainFrame) old).detach();
            old.dispose();
        }
    }

    public static void showLogin() {
        SystemContext.currentUser = null;
        Theme.apply(SystemContext.settings.isDarkMode());
        swap(new LoginFrame());
    }

    public static void signedIn(User u) {
        SystemContext.currentUser = u;
        swap(new MainFrame(u, MainFrame.Nav.DASHBOARD));
    }

    /** Rebuilds the window (used after the theme changes) and returns to the same page. */
    public static void rebuild(MainFrame.Nav page) {
        Theme.apply(SystemContext.settings.isDarkMode());
        swap(new MainFrame(SystemContext.currentUser, page));
    }

    /** Reloads every service from disk (after a restore) and goes back to the sign-in screen. */
    public static void reloadFromDisk() {
        SystemContext.init(SystemContext.files.dir());
        showLogin();
    }
}
