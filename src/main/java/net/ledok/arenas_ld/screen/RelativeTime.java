package net.ledok.arenas_ld.screen;

/**
 * Compact "how long ago" formatting for leaderboard entries ("now", "5m", "3h", "2d", "8w").
 * Client-side display only — compares a recorded epoch-millis stamp against the local clock.
 */
public final class RelativeTime {
    private RelativeTime() {
    }

    public static String ago(long recordedAtEpochMillis) {
        if (recordedAtEpochMillis <= 0) {
            return "";
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - recordedAtEpochMillis) / 1000);
        if (seconds < 60) return "now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        if (days < 14) return days + "d";
        long weeks = days / 7;
        if (weeks < 9) return weeks + "w";
        long months = days / 30;
        if (months < 12) return months + "mo";
        return (days / 365) + "y";
    }
}
