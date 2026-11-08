package main;

import javax.swing.SwingUtilities;
import ui.App;

public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(App::start);
    }
}
