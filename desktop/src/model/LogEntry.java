package model;

import java.time.LocalDateTime;

/** Audit trail entry: who did what, when. */
public class LogEntry {

    private final LocalDateTime dateTime;
    private final String user;
    private final String action;
    private final String details;

    public LogEntry(LocalDateTime dateTime, String user, String action, String details) {
        this.dateTime = dateTime;
        this.user = user == null ? "" : user;
        this.action = action == null ? "" : action;
        this.details = details == null ? "" : details;
    }

    public LocalDateTime getDateTime() { return dateTime; }
    public String getUser() { return user; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
}
