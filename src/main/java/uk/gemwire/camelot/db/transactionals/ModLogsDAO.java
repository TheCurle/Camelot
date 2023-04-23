package uk.gemwire.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.ModLogEntry;

import java.time.temporal.ChronoUnit;
import java.util.List;

public interface ModLogsDAO extends Transactional<ModLogsDAO> {
    default List<ModLogEntry> getLogs(long user, long guild, int from, int limit, @Nullable ModLogEntry.Type include, @Nullable ModLogEntry.Type exclude) {
        final String statement;
        final int fl;
        if (include != null) {
            statement = "select * from modlogs where user = :user and guild = :guild and type == :type limit :limit offset :from";
            fl = include.ordinal();
        } else if (exclude != null) {
            statement = "select * from modlogs where user = :user and guild = :guild and type != :type limit :limit offset :from";
            fl = exclude.ordinal();
        } else {
            statement = "select * from modlogs where user = :user and guild = :guild limit :limit offset :from";
            fl = -1;
        }
        return getHandle().createQuery(statement)
                .bind("user", user)
                .bind("guild", guild)
                .bind("from", from)
                .bind("limit", limit)
                .bind("type", fl)
                .map(ModLogEntry.Mapper.INSTANCE)
                .list();
    }

    default int getLogCount(long user, long guild, @Nullable ModLogEntry.Type include, @Nullable ModLogEntry.Type exclude) {
        final String statement;
        final int fl;
        if (include != null) {
            statement = "select count(id) from modlogs where user = :user and guild = :guild and type == :type";
            fl = include.ordinal();
        } else if (exclude != null) {
            statement = "select count(id) from modlogs where user = :user and guild = :guild and type != :type";
            fl = exclude.ordinal();
        } else {
            statement = "select count(id) from modlogs where user = :user and guild = :guild";
            fl = -1;
        }
        return getHandle().createQuery(statement)
                .bind("user", user)
                .bind("guild", guild)
                .bind("type", fl)
                .mapTo(int.class)
                .one();
    }

    default int insert(ModLogEntry entry) {
        return getHandle().createUpdate("insert into modlogs (type, user, guild, moderator, timestamp, duration, reason) values (?, ?, ?, ?, ?, ?, ?) returning id;")
                .bind(0, entry.type().ordinal())
                .bind(1, entry.user())
                .bind(2, entry.guild())
                .bind(3, entry.moderator())
                .bind(4, entry.timestamp().getEpochSecond())
                .bind(5, entry.duration() == null ? null : entry.duration().get(ChronoUnit.SECONDS))
                .bind(6, entry.reason())
                .execute((statementSupplier, ctx) -> statementSupplier.get().getResultSet().getInt("id"));
    }
}
