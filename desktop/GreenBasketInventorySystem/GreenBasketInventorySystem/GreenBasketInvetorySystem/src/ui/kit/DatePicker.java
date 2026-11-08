package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import util.Fmt;

/** Text field (yyyy-MM-dd) with a month-grid popup. Empty means "no date". */
public class DatePicker extends JPanel {

    private final Field field = new Field("yyyy-mm-dd");
    private final JPopupMenu popup = new JPopupMenu();
    private YearMonth shown = YearMonth.now();
    private Runnable listener = () -> { };

    public DatePicker() {
        super(new BorderLayout());
        setOpaque(false);
        add(field, BorderLayout.CENTER);
        Btn pick = new Btn("", "calendar", Btn.Kind.SECONDARY);
        pick.setToolTipText("Pick a date");
        pick.addActionListener(e -> openPopup());
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(0, 6, 0, 0));
        right.add(pick);
        add(right, BorderLayout.EAST);
        popup.setBorder(BorderFactory.createLineBorder(Theme.borderStrong));
        popup.setBackground(Theme.surface);
        field.addActionListener(e -> listener.run());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { listener.run(); }
        });
    }

    public DatePicker onChange(Runnable r) { this.listener = r; return this; }

    public void setDate(LocalDate d) { field.setText(d == null ? "" : Fmt.date(d)); field.setInvalid(false); }

    /** The chosen date, or null when empty. Throws IllegalArgumentException when malformed. */
    public LocalDate getDate() {
        try {
            LocalDate d = Fmt.parseDate(field.getText());
            field.setInvalid(false);
            return d;
        } catch (IllegalArgumentException e) {
            field.setInvalid(true);
            throw e;
        }
    }

    /** Like {@link #getDate()} but returns null (instead of throwing) for empty or malformed text. */
    public LocalDate getDateOrNull() {
        try {
            return getDate();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override public void setEnabled(boolean b) { super.setEnabled(b); field.setEnabled(b); }

    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(160, super.getPreferredSize().width), 40); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 40); }

    private void openPopup() {
        try {
            LocalDate d = Fmt.parseDate(field.getText());
            if (d != null) shown = YearMonth.from(d);
        } catch (IllegalArgumentException ignored) { }
        rebuild();
        popup.show(this, 0, getHeight() + 4);
    }

    private void rebuild() {
        popup.removeAll();
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Theme.surface);
        p.setBorder(new EmptyBorder(12, 12, 10, 12));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        Btn prev = new Btn("<", Btn.Kind.GHOST).compact();
        Btn next = new Btn(">", Btn.Kind.GHOST).compact();
        prev.addActionListener(e -> { shown = shown.minusMonths(1); rebuild(); popup.show(this, 0, getHeight() + 4); });
        next.addActionListener(e -> { shown = shown.plusMonths(1); rebuild(); popup.show(this, 0, getHeight() + 4); });
        JLabel title = new JLabel(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + shown.getYear(), SwingConstants.CENTER);
        title.setFont(Theme.bold(13.5f));
        title.setForeground(Theme.text);
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        grid.setOpaque(false);
        for (DayOfWeek dw : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY}) {
            JLabel l = new JLabel(dw.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2), SwingConstants.CENTER);
            l.setFont(Theme.bold(11));
            l.setForeground(Theme.faint);
            grid.add(l);
        }
        int offset = shown.atDay(1).getDayOfWeek().getValue() - 1;
        for (int i = 0; i < offset; i++) grid.add(new JLabel());
        LocalDate selected = null;
        try { selected = Fmt.parseDate(field.getText()); } catch (IllegalArgumentException ignored) { }
        for (int day = 1; day <= shown.lengthOfMonth(); day++) {
            LocalDate d = shown.atDay(day);
            grid.add(new DayCell(d, d.equals(selected), d.equals(LocalDate.now())));
        }
        p.add(grid, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        foot.setOpaque(false);
        Btn today = new Btn("Today", Btn.Kind.SOFT).compact();
        Btn clear = new Btn("Clear", Btn.Kind.GHOST).compact();
        today.addActionListener(e -> choose(LocalDate.now()));
        clear.addActionListener(e -> choose(null));
        foot.add(today);
        foot.add(clear);
        p.add(foot, BorderLayout.SOUTH);
        p.setPreferredSize(new Dimension(272, 300));
        popup.add(p);
    }

    private void choose(LocalDate d) {
        setDate(d);
        popup.setVisible(false);
        listener.run();
    }

    private class DayCell extends JComponent {
        private final LocalDate date;
        private final boolean sel, today;
        private boolean hover;

        DayCell(LocalDate d, boolean sel, boolean today) {
            this.date = d;
            this.sel = sel;
            this.today = today;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { choose(date); }
            });
        }

        @Override public Dimension getPreferredSize() { return new Dimension(34, 34); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int s = Math.min(getWidth(), getHeight());
            int x = (getWidth() - s) / 2, y = (getHeight() - s) / 2;
            if (sel) Gfx.fillRound(g2, x, y, s, s, s / 2.0, Theme.primary);
            else if (hover) Gfx.fillRound(g2, x, y, s, s, s / 2.0, Theme.hover);
            else if (today) Gfx.strokeRound(g2, x, y, s, s, s / 2.0, Theme.primary, 1.4f);
            String t = String.valueOf(date.getDayOfMonth());
            Font f = sel ? Theme.bold(12.5f) : Theme.font(12.5f);
            FontMetrics fm = g2.getFontMetrics(f);
            Gfx.text(g2, t, (getWidth() - fm.stringWidth(t)) / 2.0, (getHeight() - fm.getHeight()) / 2.0 + fm.getAscent(), f,
                    sel ? Theme.onPrimary : Theme.text);
            g2.dispose();
        }
    }
}
