package net.ledok.arenas_ld.screen;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings fields of an admin screen, each with the server value it was last taken from, so a
 * fresh snapshot is merged field by field instead of overwriting what the admin is typing. Several
 * admins (or one admin in several tabs) can edit at once: a save only changes the fields it saved.
 * <ul>
 *   <li>an untouched field follows the server;</li>
 *   <li>an edited field keeps the edit while the server value stays the same;</li>
 *   <li>an edited field whose server value changed too keeps the edit and becomes a conflict, which
 *       the screen shows; saving then overwrites the other value knowingly, {@link #takeServer} drops
 *       the edit.</li>
 * </ul>
 * Values are compared as the strings the boxes show. Keys are free-form; screens prefix them with
 * their tab (see {@link #dirty(String)}).
 */
public final class AdminDrafts {
    /** The server value each field was last taken from. */
    private final Map<String, String> base = new HashMap<>();
    /** What each field holds now. */
    private final Map<String, String> value = new HashMap<>();
    /** Conflicted fields: the server value that collided with the edit. */
    private final Map<String, String> conflicts = new LinkedHashMap<>();
    /** Saved fields still waiting for their echo: the server value from before the save. */
    private final Map<String, String> savedFrom = new HashMap<>();
    /** Snapshots still due in answer to saves; the last one is authoritative. */
    private int pendingEchoes;

    public String get(String key) {
        return value.getOrDefault(key, "");
    }

    /** The admin changed a field. */
    public void set(String key, String text) {
        value.put(key, text);
        if (text.equals(base.get(key))) {
            conflicts.remove(key);
        }
    }

    /** Takes every field's current text at once (screens that keep their own input variables). */
    public void setAll(Map<String, String> texts) {
        texts.forEach(this::set);
    }

    public Map<String, String> values() {
        return value;
    }

    public boolean dirty(String key) {
        return value.containsKey(key) && !value.get(key).equals(base.get(key));
    }

    /** Whether any field whose key starts with {@code prefix} has an unsaved edit. */
    public boolean dirtyAny(String prefix) {
        for (String key : value.keySet()) {
            if (key.startsWith(prefix) && dirty(key)) {
                return true;
            }
        }
        return false;
    }

    /** The conflicted fields whose key starts with {@code prefix}, with the server's value. */
    public Map<String, String> conflicts(String prefix) {
        Map<String, String> found = new LinkedHashMap<>();
        conflicts.forEach((key, server) -> {
            if (key.startsWith(prefix)) {
                found.put(key, server);
            }
        });
        return found;
    }

    /** Drops the edits of the given conflicted fields: they show the server value again. */
    public void takeServer(Collection<String> keys) {
        for (String key : keys) {
            if (base.containsKey(key)) {
                value.put(key, base.get(key));
            }
            conflicts.remove(key);
        }
    }

    /**
     * The fields were just sent in a save that the server answers with {@code echoes} snapshots:
     * they follow the server again, so whatever it accepted (or clamped, or refused) shows. Until
     * the last echo, a snapshot still carrying a field's old value doesn't flip the box back.
     */
    public void saved(Collection<String> keys, int echoes) {
        for (String key : keys) {
            String before = base.get(key);
            if (before != null) {
                savedFrom.putIfAbsent(key, before);
            }
            base.put(key, get(key));
            conflicts.remove(key);
        }
        pendingEchoes += echoes;
    }

    /**
     * Merges a fresh snapshot of every field's server value.
     *
     * @return the fields whose shown value changed (taken from the server, or newly conflicted)
     */
    public List<String> merge(Map<String, String> server) {
        boolean last = pendingEchoes <= 1;
        if (pendingEchoes > 0) {
            pendingEchoes--;
        }
        List<String> changed = new java.util.ArrayList<>();
        server.forEach((key, fresh) -> {
            String from = savedFrom.get(key);
            if (from != null) {
                if (!last && fresh.equals(from)) {
                    return; // the save hasn't landed yet
                }
                savedFrom.remove(key);
            }
            String known = base.get(key);
            String shown = value.get(key);
            if (known == null || shown == null) {
                base.put(key, fresh);
                value.put(key, fresh);
                return; // first sight: nothing to merge
            }
            if (fresh.equals(known)) {
                return; // the server didn't change it
            }
            base.put(key, fresh);
            if (shown.equals(known)) {
                value.put(key, fresh); // untouched: follow the server
                changed.add(key);
            } else if (shown.equals(fresh)) {
                conflicts.remove(key); // both made the same change
            } else {
                conflicts.put(key, fresh); // both changed it: keep the edit, warn
                changed.add(key);
            }
        });
        if (last) {
            savedFrom.clear();
        }
        return changed;
    }

    /** Whether a snapshot field is one of {@code prefix}'s. */
    public static boolean any(Collection<String> keys, String prefix) {
        for (String key : keys) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Joins a list field (reward commands, one per line) into one comparable value. */
    public static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    public static List<String> splitLines(@Nullable String joined) {
        if (joined == null || joined.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return new java.util.ArrayList<>(List.of(joined.split("\n", -1)));
    }
}
