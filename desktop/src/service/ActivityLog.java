package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.LogEntry;
import util.FileManager;

/** Append-only audit trail of important actions. */
public class ActivityLog {

    private final FileManager files;
    private final List<LogEntry> entries = new ArrayList<>();

    public ActivityLog(FileManager files) {
        this.files = files;
    }

    public void load() {
        entries.clear();
        entries.addAll(files.loadLog());
    }

    public void add(String user, String action, String details) {
        LogEntry e = new LogEntry(LocalDateTime.now().withNano(0), user, action, details);
        entries.add(e);
        try {
            files.appendLog(e);
        } catch (ServiceException ignored) {
            // the audit trail must never break the action being logged
        }
    }

    /** Newest first. */
    public List<LogEntry> getAll() {
        List<LogEntry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy;
    }

    public List<LogEntry> recent(int n) {
        List<LogEntry> all = getAll();
        return all.subList(0, Math.min(n, all.size()));
    }
}
