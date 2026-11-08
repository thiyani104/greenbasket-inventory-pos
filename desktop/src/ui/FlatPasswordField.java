package ui;

import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JPasswordField;

public class FlatPasswordField extends JPasswordField {

    private String placeholder = "";
    private boolean error;
    private final char defaultEcho = getEchoChar();

    public FlatPasswordField() { this(20); }

    public FlatPasswordField(int columns) {
        super(columns);
        FlatTextField.init(this);
    }

    public FlatPasswordField placeholder(String p) {
        placeholder = p;
        return this;
    }

    public void setError(boolean e) {
        error = e;
        repaint();
    }

    public void setRevealed(boolean reveal) {
        setEchoChar(reveal ? (char) 0 : defaultEcho);
        revealed = reveal;
        repaint();
    }

    private boolean toggleEnabled;
    private boolean revealed;

    /** Paints an eye icon inside the field that shows / hides the password when clicked. */
    public FlatPasswordField withRevealToggle() {
        toggleEnabled = true;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(9, 12, 9, 38));
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getX() > getWidth() - 36) setRevealed(!revealed);
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                setCursor(java.awt.Cursor.getPredefinedCursor(e.getX() > getWidth() - 36
                        ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.TEXT_CURSOR));
            }
        });
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        FlatTextField.paintFrame(this, g, error);
        super.paintComponent(g);
        FlatTextField.paintPlaceholder(this, g, placeholder);
        if (toggleEnabled) {
            Icons.of(revealed ? Icons.Kind.EYE_OFF : Icons.Kind.EYE, 18, new java.awt.Color(0x8A9A92))
                    .paintIcon(this, g, getWidth() - 28, (getHeight() - 18) / 2);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 38);
        return d;
    }

    @Override
    public Dimension getMinimumSize() { return new Dimension(60, 38); }
}
