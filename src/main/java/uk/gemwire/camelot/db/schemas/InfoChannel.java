package uk.gemwire.camelot.db.schemas;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

public record InfoChannel(long channel, GithubLocation location, boolean forceRecreate, @Nullable String hash) {
    public static final class Mapper implements RowMapper<InfoChannel> {

        @Override
        public InfoChannel map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new InfoChannel(
                    rs.getLong(1),
                    GithubLocation.parse(rs.getString(2)),
                    rs.getBoolean(3),
                    rs.getString(4)
            );
        }
    }
}
