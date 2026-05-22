package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A single entry on a dungeon controller's leaderboard. One leaderboard per tier per
 * controller; entries are appended on each WIN outcome.
 *
 * <p>Sorting and ranking are the consumer's responsibility — this record is pure data.
 *
 * @param playerName            display name of the run participant. Multiple players per
 *                              run produce multiple entries (one per participant) so each
 *                              gets credit for the time.
 * @param timeSeconds           run duration in seconds, from start to boss defeated.
 * @param recordedAtEpochMillis system time when the entry was created. Used for tie-breaking
 *                              and showing "set 3 days ago" style UI.
 */
public record LeaderboardEntry(
    String playerName,
    int timeSeconds,
    long recordedAtEpochMillis
) {
    public static final Codec<LeaderboardEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("name").forGetter(LeaderboardEntry::playerName),
            Codec.INT.fieldOf("timeSeconds").forGetter(LeaderboardEntry::timeSeconds),
            Codec.LONG.fieldOf("recordedAt").forGetter(LeaderboardEntry::recordedAtEpochMillis)
        ).apply(instance, LeaderboardEntry::new)
    );
}
