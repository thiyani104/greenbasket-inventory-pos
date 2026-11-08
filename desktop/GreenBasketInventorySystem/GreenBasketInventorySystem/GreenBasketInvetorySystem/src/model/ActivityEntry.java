package model;

import java.time.LocalDateTime;

/** One audit-trail line (logins, edits, deletions, sales...). */
public class ActivityEntry {

    public static final String[] CSV_HEADER = {"time", "user", "action", "detail"};

    private final LocalDateTime time;
    private final String user;
    private final String action;
    private final String detail;

    public ActivityEntry(LocalDateTime time, String user, String action, String detail) {
        this.time = time;
        this.user = user;
        this.action = action;
        this.detail = detail == null ? "" : detail;
    }

    public LocalDateTime getTime() { return time; }
    public String getUser() { return user; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }

    public String[] toCsv() {
        return new String[]{util.Fmt.dateTime(time), user, action, detail};
    }

    public static ActivityEntry fromCsv(String[] f) {
        return new ActivityEntry(util.Fmt.parseDateTime(f[0]), f[1], f[2], f.length > 3 ? f[3] : "");
    }
}
