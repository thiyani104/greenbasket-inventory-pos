package ui.kit;

import java.awt.*;
import java.util.Collection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/** Combo box with the same rounded look as {@link Field}. Works editable or not. */
public class Combo<E> extends JComboBox<E> {

    public Combo() { init(); }

    @SafeVarargs
    public Combo(E... items) {
        super(items);
        init();
    }

    public Combo(Collection<E> items) {
        super(new DefaultComboBoxModel<E>((E[]) items.toArray()));
        init();
    }

    private void init() {
        setOpaque(false);
        setFont(Theme.font(13.5f));
        setForeground(Theme.text);
        setBorder(new EmptyBorder(0, 6, 0, 0));
        setRenderer(new Renderer());
        setUI(new Ui());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMaximumRowCount(10);
    }

    @Override public void updateUI() { /* keep our own UI */ }

    @Override public void setEditable(boolean b) {
        super.setEditable(b);
        styleEditor();
    }

    private void styleEditor() {
        if (isEditable() && getEditor() != null && getEditor().getEditorComponent() instanceof JTextField) {
            JTextField t = (JTextField) getEditor().getEditorComponent();
            t.setBorder(new EmptyBorder(0, 6, 0, 0));
            t.setOpaque(false);
            t.setFont(getFont());
            t.setForeground(Theme.text);
            t.setCaretColor(Theme.primary);
            t.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
        }
    }

    public String editorText() {
        Object o = isEditable() ? getEditor().getItem() : getSelectedItem();
        return o == null ? "" : o.toString().trim();
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width + 12, 120), 40);
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 40); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        Gfx.fillRound(g2, 0, 0, w, h, 8, isEnabled() ? Theme.surface : Theme.surfaceAlt);
        boolean focus = isFocusOwner() || (isEditable() && getEditor().getEditorComponent().isFocusOwner());
        if (focus) {
            Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.alpha(Theme.primary, 70), 3.5f);
            Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.primary, 1.4f);
        } else Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.borderStrong, 1f);
        g2.dispose();
        super.paintComponent(g);
    }

    private static class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            l.setBorder(new EmptyBorder(8, index < 0 ? 4 : 12, 8, 12));
            l.setFont(Theme.font(13.5f));
            if (index < 0) {
                l.setOpaque(false);
                l.setForeground(Theme.text);
            } else {
                l.setOpaque(true);
                l.setBackground(sel ? Theme.primarySoft : Theme.surface);
                l.setForeground(Theme.text);
            }
            return l;
        }
    }

    private static class Ui extends BasicComboBoxUI {
        @Override protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Gfx.aa(g);
                    g2.setColor(Theme.muted);
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                    g2.dispose();
                }
            };
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setPreferredSize(new Dimension(30, 30));
            return b;
        }

        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }

        @Override protected ComboPopup createPopup() {
            BasicComboPopup p = new BasicComboPopup(comboBox) {
                @Override protected JScrollPane createScroller() {
                    JScrollPane sp = Tables.slimScroll(list);
                    return sp;
                }
            };
            p.setBorder(BorderFactory.createLineBorder(Theme.borderStrong));
            p.getList().setBackground(Theme.surface);
            return p;
        }

        @Override protected Rectangle rectangleForCurrentValue() {
            Rectangle r = super.rectangleForCurrentValue();
            return r;
        }
    }
}
