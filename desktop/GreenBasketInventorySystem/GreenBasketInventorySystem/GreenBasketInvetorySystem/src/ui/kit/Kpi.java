package ui.kit;

import java.awt.*;
import javax.swing.JComponent;

/** A headline number with an icon, a label and a small coloured footnote. */
public class Kpi extends JComponent {

    public enum Tone { NEUTRAL, GOOD, BAD, WARN }

    private final String label, icon;
    private String value = "-", sub = "";
    private Tone tone = Tone.NEUTRAL;
    private final Color accent;

    public Kpi(String label, String icon, Color accent) {
        this.label = label;
        this.icon = icon;
        this.accent = accent;
        setOpaque(false);
    }

    public void set(String value, String sub, Tone tone) {
        this.value = value;
        this.sub = sub == null ? "" : sub;
        this.tone = tone;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(200, 112); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight() - 1;
        if (!Theme.dark) Gfx.fillRound(g2, 0, 2, w, h, 12, new Color(16, 40, 28, 10));
        Gfx.fillRound(g2, 0, 0, w, h, 12, Theme.surface);
        Gfx.strokeRound(g2, 0, 0, w, h, 12, Theme.border, 1f);
        // icon chip
        Gfx.fillRound(g2, 18, 18, 36, 36, 10, Theme.alpha(accent, Theme.dark ? 45 : 32));
        Icons.paint(g2, icon, 25, 25, 22, accent, 1.8f);
        Gfx.text(g2, label, 64, 30, Theme.font(12.5f), Theme.muted);
        Font vf = Theme.bold(23);
        FontMetrics fm = g2.getFontMetrics(vf);
        String v = Gfx.ellipsize(value, fm, w - 36);
        Gfx.text(g2, v, 64, 53, vf, Theme.text);
        if (!sub.isEmpty()) {
            Color c = tone == Tone.GOOD ? Theme.success : tone == Tone.BAD ? Theme.danger : tone == Tone.WARN ? Theme.warning : Theme.muted;
            FontMetrics sm = g2.getFontMetrics(Theme.font(12));
            Gfx.text(g2, Gfx.ellipsize(sub, sm, w - 36), 18, 86, Theme.font(12), c);
        }
        g2.dispose();
    }
}
