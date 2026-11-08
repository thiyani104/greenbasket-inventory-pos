package ui;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** DocumentListener that runs one callback for any change. */
public class SimpleDoc implements DocumentListener {

    private final Runnable r;

    public SimpleDoc(Runnable r) { this.r = r; }

    @Override public void insertUpdate(DocumentEvent e) { r.run(); }
    @Override public void removeUpdate(DocumentEvent e) { r.run(); }
    @Override public void changedUpdate(DocumentEvent e) { r.run(); }
}
