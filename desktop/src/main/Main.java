package main;

import java.nio.file.Path;
import javax.swing.SwingUtilities;
import ui.Dialogs;
import ui.LoginFrame;
import ui.Theme;
import util.SystemContext;

/** Starts Green Basket. Data lives in the "data" folder next to where the app is run (override with -Dgb.data=PATH). */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            String note;
            try {
                note = SystemContext.init(Path.of(System.getProperty("gb.data", "data")));
            } catch (RuntimeException ex) {
                Dialogs.error(null, "Green Basket could not start: " + ex.getMessage());
                System.exit(1);
                return;
            }
            LoginFrame login = new LoginFrame();
            login.setVisible(true);
            if (note != null) Dialogs.info(login, "Old data imported", note);
        });
    }
}
