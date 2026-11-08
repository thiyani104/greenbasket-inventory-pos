package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A headline number with an icon, caption and a small supporting line. */
public class KpiCard extends Card {

    private final JLabel value = UI.label("–", Font.BOLD, 21, Theme.TEXT);
    private final JLabel caption;
    private final JLabel sub = UI.label(" ", Font.PLAIN, 12, Theme.MUTED);

    public KpiCard(Icons.Kind icon, String caption, Color accent) {
        super(new BorderLayout(12, 0));
        padding(14, 14, 14, 12);
        this.caption = UI.label(caption, Font.PLAIN, 12, Theme.MUTED);
        Color tint = Theme.alpha(accent, 28);
        JComponent badge = new JComponent() {
            { setPreferredSize(new Dimension(46, 46)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tint);
                g2.fillRoundRect(0, 0, 46, 46, 16, 16);
                g2.dispose();
                Icons.of(icon, 22, accent).paintIcon(this, g, 12, 12);
            }
        };
        JPanel badgeWrap = UI.transparent(new BorderLayout());
        badgeWrap.add(badge, BorderLayout.NORTH);
        add(badgeWrap, BorderLayout.WEST);
        JPanel text = UI.transparent(new BorderLayout(0, 1));
        text.add(this.caption, BorderLayout.NORTH);
        text.add(value, BorderLayout.CENTER);
        text.add(sub, BorderLayout.SOUTH);
        add(text, BorderLayout.CENTER);
        setPreferredSize(new Dimension(150, 96));
    }

    public void set(String valueText, String subText) {
        value.setText(valueText);
        sub.setText(subText == null || subText.isEmpty() ? " " : subText);
        sub.setForeground(Theme.MUTED);
    }

    public void set(String valueText, String subText, Color subColor) {
        set(valueText, subText);
        sub.setForeground(subColor);
    }
}
