package uk.gemwire.camelot.db.schemas;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public record UserAndGuild(long user, long guild) {
    public static final class Mapper implements RowMapper<UserAndGuild> {

        @Override
        public UserAndGuild map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserAndGuild(
                    rs.getLong("user"),
                    rs.getLong("guild")
            );
        }
    }
}
