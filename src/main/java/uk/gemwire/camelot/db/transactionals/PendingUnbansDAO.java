package uk.gemwire.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;

import java.sql.Timestamp;
import java.util.List;

public interface PendingUnbansDAO extends Transactional<PendingUnbansDAO> {
    @SqlUpdate("insert or replace into pending_unbans(user, guild, deadline) values (:user, :guild, :deadline)")
    void insert(@Bind("user") long user, @Bind("guild") long guild, @Bind("deadline") Timestamp deadline);

    @SqlQuery("select user from pending_unbans where guild = :guild and deadline < datetime()")
    List<Long> getUsersToUnban(@Bind("guild") long guild);

    @SqlUpdate("delete from pending_unbans where user = :user and guild = :guild;")
    void delete(@Bind("user") long user, @Bind("guild") long guild);
}
