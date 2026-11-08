package ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Collection;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;

/** Rounded combo box with a chevron, flat popup and consistent row height. */
public class FlatComboBox<E> extends JComboBox<E> {

    public FlatComboBox() {
        setOpaque(false);
        setUI(new FlatUI());
        setFont(Theme.font(java.awt.Font.PLAIN, 13));
        setForeground(Theme.TEXT);
        setBorder(BorderFactory.createEmptyBorder());
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, false);
                setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
                setFont(FlatComboBox.this.getFont());
                if (index == -1) {
                    setOpaque(false); // the closed box draws its own rounded background
                    setForeground(Theme.TEXT);
                } else {
                    setOpaque(true);
                    setBackground(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
                    setForeground(Theme.TEXT);
                }
                return this;
            }
        });
        setMaximumRowCount(10);
    }

    public FlatComboBox(Collection<? extends E> items) {
        this();
        for (E e : items) addItem(e);
    }

    @SafeVarargs
    public FlatComboBox(E... items) {
        this();
        for (E e : items) addItem(e);
    }

    /** Turns the box into an editable one whose text area blends into the rounded frame. */
    public FlatComboBox<E> makeEditable() {
        setEditable(true);
        Component ed = getEditor().getEditorComponent();
        if (ed instanceof JTextField tf) {
            tf.setOpaque(false);
            tf.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
            tf.setFont(getFont());
            tf.setForeground(Theme.TEXT);
            tf.setCaretColor(Theme.PRIMARY_DARK);
        }
        return this;
    }

    public String editorText() {
        Object o = isEditable() ? getEditor().getItem() : getSelectedItem();
        return o == null ? "" : o.toString().trim();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(isEnabled() ? Color.WHITE : new Color(0xF3F6F4));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
        boolean focus = hasFocus() || (isEditable() && getEditor().getEditorComponent().hasFocus());
        g2.setColor(focus ? Theme.PRIMARY : Theme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = 38;
        return d;
    }

    private static final class FlatUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0x6B7B73));
                    g2.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(cx - 4, cy - 2);
                    p.lineTo(cx, cy + 2);
                    p.lineTo(cx + 4, cy - 2);
                    g2.draw(p);
                    g2.dispose();
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setOpaque(false);
            b.setPreferredSize(new Dimension(30, 30));
            return b;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // background is painted by FlatComboBox itself
        }

        @Override
        protected javax.swing.plaf.basic.ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox);
            popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            return popup;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            comboBox.setOpaque(false);
        }
    }
}
