package uk.gemwire.camelot.db.schemas;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.util.DateUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ModLogEntry {
    private int id;
    private final Type type;
    private final long user;
    private final long guild;
    private final long moderator;
    private final Instant timestamp;
    @Nullable
    private final Duration duration;
    @Nullable
    private final String reason;

    private ModLogEntry(int id, Type type, long user, long guild, long moderator, Instant timestamp, @Nullable Duration duration, @Nullable String reason) {
        this.id = id;
        this.type = type;
        this.user = user;
        this.guild = guild;
        this.moderator = moderator;
        this.timestamp = timestamp;
        this.duration = duration;
        this.reason = reason;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int id() {
        if (id == -1) {
            throw new UnsupportedOperationException("This entry is not from a database!");
        }
        return id;
    }

    public Type type() {
        return type;
    }

    public long user() {
        return user;
    }

    public long guild() {
        return guild;
    }

    public long moderator() {
        return moderator;
    }

    public Instant timestamp() {
        return timestamp;
    }

    @Nullable
    public Duration duration() {
        return duration;
    }

    @Nullable
    public String reason() {
        return reason;
    }

    public String reasonOrDefault() {
        return Objects.requireNonNullElse(reason(), "Reason not specified");
    }

    @Override
    public String toString() {
        return "ModLogEntry{" +
                "id=" + id +
                ", type=" + type +
                ", user=" + user +
                ", guild=" + guild +
                ", moderator=" + moderator +
                ", timestamp=" + timestamp +
                ", duration=" + duration +
                ", reason='" + reason + '\'' +
                '}';
    }

    public CompletableFuture<MessageEmbed.Field> format(JDA jda) {
        return type().format(this, jda);
    }

    public enum Type {
        WARN("warned", false, 0x00BFFF),
        KICK("kicked", false, 0xFFFFE0),

        MUTE("muted", true, 0xD3D3D3),
        UNMUTE("un-muted", false, 0xFFFFFF),

        BAN("banned", true, 0xFF0000),
        UNBAN("un-banned", false, 0x32CD32),

        NOTE("noted", false, 0x00FFFF) {
            @Override
            public CompletableFuture<MessageEmbed.Field> format(ModLogEntry entry, JDA jda) {
                return jda.retrieveUserById(entry.moderator())
                        .submit()
                        .thenApply(mod -> mod.getAsTag() + " (" + mod.getId() + ")")
                        .exceptionally(ex -> String.valueOf(entry.moderator()))
                        .thenApply(mod -> Lists.newArrayList(
                                "**Type**: note",
                                "**Moderator**: " + mod,
                                "**Note**: " + entry.reason()
                        ))
                        .thenApply(lines -> new MessageEmbed.Field(
                                "Note " + entry.id,
                                String.join("\n", lines),
                                false
                        ));
            }
        };

        private final String action;
        private final boolean supportsDuration;
        private final int color;

        Type(String action, boolean supportsDuration, int color) {
            this.action = action;
            this.supportsDuration = supportsDuration;
            this.color = color;
        }

        public String getAction() {
            return action;
        }

        public boolean supportsDuration() {
            return supportsDuration;
        }

        public int getColor() {
            return color;
        }

        public CompletableFuture<MessageEmbed.Field> format(ModLogEntry entry, JDA jda) {
            if (supportsDuration) {
                return collectInformation(entry, jda)
                        .thenApply(accept(list -> list.add(entry.formatDuration())))
                        .thenApply(lines -> buildEmbed(entry, lines));
            }
            return collectInformation(entry, jda)
                    .thenApply(lines -> buildEmbed(entry, lines));
        }

        protected final CompletableFuture<List<String>> collectInformation(ModLogEntry entry, JDA jda) {
            return jda.retrieveUserById(entry.moderator())
                    .submit()
                    .thenApply(mod -> mod.getAsTag() + " (" + mod.getId() + ")")
                    .exceptionally(ex -> String.valueOf(entry.moderator()))
                    .thenApply(data -> Lists.newArrayList(
                            "**Type**: " + name().toLowerCase(Locale.ROOT),
                            "**Moderator**: " + data,
                            "**Reason**: " + entry.reasonOrDefault() + " - " + TimeFormat.DATE_TIME_LONG.format(entry.timestamp())
                    ));
        }

        protected final MessageEmbed.Field buildEmbed(ModLogEntry entry, List<String> lines) {
            return new MessageEmbed.Field(
                    "Case " + entry.id(),
                    String.join("\n", lines),
                    false
            );
        }

        protected final <T> Function<T, T> accept(Consumer<T> cons) {
            return t -> {
                cons.accept(t);
                return t;
            };
        }
    }

    public String formatDuration() {
        return "**Duration**: " +
                (duration() == null ? "Indefinite" :
                        DateUtils.formatDuration(duration) + " (until " +
                                TimeFormat.DATE_TIME_LONG.format(timestamp.plus(duration())) + ")");
    }

    public static ModLogEntry kick(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.KICK, user, guild, moderator, Instant.now(), null, reason);
    }

    public static ModLogEntry ban(long user, long guild, long moderator, @Nullable Duration duration, @Nullable String reason) {
        return new ModLogEntry(-1, Type.BAN, user, guild, moderator, Instant.now(), duration, reason);
    }

    public static ModLogEntry unban(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.UNBAN, user, guild, moderator, Instant.now(), null, reason);
    }

    public static ModLogEntry warn(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.WARN, user, guild, moderator, Instant.now(), null, reason);
    }

    public static ModLogEntry note(long user, long guild, long moderator, @Nullable String note) {
        return new ModLogEntry(-1, Type.NOTE, user, guild, moderator, Instant.now(), null, note);
    }

    public static ModLogEntry mute(long user, long guild, long moderator, @Nullable Duration duration, @Nullable String reason) {
        return new ModLogEntry(-1, Type.MUTE, user, guild, moderator, Instant.now(), duration, reason);
    }

    public static ModLogEntry unmute(long user, long guild, long moderator, @Nullable String reason) {
        return new ModLogEntry(-1, Type.UNMUTE, user, guild, moderator, Instant.now(), null, reason);
    }

    public static final class Mapper implements RowMapper<ModLogEntry> {
        public static final Mapper INSTANCE = new Mapper();

        @Override
        public ModLogEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
            final long duration = rs.getLong(7);
            return new ModLogEntry(
                    rs.getInt(1),
                    Type.values()[rs.getInt(2)],
                    rs.getLong(3),
                    rs.getLong(4),
                    rs.getLong(5),
                    Instant.ofEpochSecond(rs.getLong(6)),
                    duration == 0 ? null : Duration.of(duration, ChronoUnit.SECONDS),
                    rs.getString(8)
            );
        }
    }

}
