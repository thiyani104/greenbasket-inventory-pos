package ui.kit;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Consistent modal window: title, body, and a footer with right-aligned actions. */
public class ModalDialog extends JDialog {

    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    private final JPanel footerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    private boolean accepted;

    public ModalDialog(Window owner, String title, String subtitle) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.surface);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBackground(Theme.surface);
        head.setBorder(new EmptyBorder(22, 26, 8, 26));
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(18));
        t.setForeground(Theme.text);
        t.setAlignmentX(0);
        head.add(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            head.add(Box.createVerticalStrut(4));
            JLabel s = Ui.wrapped(subtitle, 380, Theme.font(12.5f), Theme.muted);
            s.setAlignmentX(0);
            head.add(s);
        }
        root.add(head, BorderLayout.NORTH);

        body.setBackground(Theme.surface);
        body.setBorder(new EmptyBorder(10, 26, 18, 26));
        root.add(body, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.surfaceAlt);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border), new EmptyBorder(14, 20, 14, 16)));
        footer.setOpaque(false);
        footerLeft.setOpaque(false);
        bar.add(footerLeft, BorderLayout.WEST);
        bar.add(footer, BorderLayout.EAST);
        root.add(bar, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public JPanel body() { return body; }

    public void setContent(JComponent c) { body.add(c, BorderLayout.CENTER); }

    public Btn addAction(Btn b) { footer.add(b); return b; }

    public void addLeft(JComponent c) { footerLeft.add(c); }

    public Btn cancelButton(String text) {
        Btn b = new Btn(text, Btn.Kind.SECONDARY);
        b.addActionListener(e -> dispose());
        footer.add(b);
        return b;
    }

    public void makeDefault(JButton b) { getRootPane().setDefaultButton(b); }

    public void accept() { accepted = true; dispose(); }

    public boolean wasAccepted() { return accepted; }

    /** Sizes to fit, centres over the owner, shows modally, returns true if accept() was called. */
    public boolean open() {
        pack();
        Dimension d = getSize();
        setSize(Math.max(d.width, 440), d.height);
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return accepted;
    }
}
