package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.ActivityEntry;
import util.FileManager;

/** Append-only audit trail. */
public class ActivityLog {

    private static final String FILE = "activity.csv";
    private final FileManager fm;
    private final List<ActivityEntry> entries = new ArrayList<>();

    public ActivityLog(FileManager fm) {
        this.fm = fm;
        for (String[] r : fm.readCsv(FILE)) {
            try {
                entries.add(ActivityEntry.fromCsv(r));
            } catch (RuntimeException ignored) { }
        }
    }

    public void log(String user, String action, String detail) {
        ActivityEntry e = new ActivityEntry(LocalDateTime.now(), user == null ? "system" : user, action, detail);
        entries.add(e);
        fm.appendCsv(FILE, ActivityEntry.CSV_HEADER, e.toCsv());
    }

    /** Newest first. */
    public List<ActivityEntry> all() {
        List<ActivityEntry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy;
    }

    public int size() { return entries.size(); }

    /** Entries on or between the given dates (null = open ended), newest first. */
    public List<ActivityEntry> between(java.time.LocalDate from, java.time.LocalDate to) {
        List<ActivityEntry> out = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            ActivityEntry e = entries.get(i);
            java.time.LocalDate d = e.getTime().toLocalDate();
            if ((from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))) out.add(e);
        }
        return out;
    }

    /** Number of entries with this action since the given moment. */
    public int count(String action, LocalDateTime since) {
        int n = 0;
        for (ActivityEntry e : entries) if (e.getAction().equals(action) && !e.getTime().isBefore(since)) n++;
        return n;
    }

    /** Distinct user names that appear in the log, sorted. */
    public List<String> users() {
        java.util.TreeSet<String> s = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ActivityEntry e : entries) s.add(e.getUser());
        return new ArrayList<>(s);
    }
}
