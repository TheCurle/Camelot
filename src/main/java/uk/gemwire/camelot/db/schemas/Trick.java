package uk.gemwire.camelot.db.schemas;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public record Trick(int id, String script, long owner) {
    public static final class Mapper implements RowMapper<Trick> {

        @Override
        public Trick map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Trick(
                    rs.getInt(1),
                    rs.getString(2),
                    rs.getLong(3)
            );
        }
    }
}
