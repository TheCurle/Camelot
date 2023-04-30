package uk.gemwire.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.Trick;

import java.util.List;

@RegisterRowMapper(Trick.Mapper.class)
public interface TricksDAO extends Transactional<TricksDAO> {

    @SqlQuery("select * from tricks limit :limit offset :from")
    List<Trick> getTricks(@Bind("from") int from, @Bind("limit") int limit);

    @Nullable
    @SqlQuery("select trick from trick_names where name = :name")
    Integer getTrickByName(@Bind("name") String name);

    @Nullable
    @SqlQuery("select * from tricks where id = :id")
    Trick getTrick(@Bind("id") int id);

    @Nullable
    default Trick getTrick(String name) {
        try {
            final int id = Integer.parseInt(name);
            final Trick byId = getTrick(id);
            if (byId != null) return byId;
        } catch (Exception ignored) {}
        final Integer trickId = getTrickByName(name);
        if (trickId == null) return null;
        return getTrick(trickId);
    }

    @SqlUpdate("update tricks set script = :script where id = :id")
    void updateScript(@Bind("id") int trickId, @Bind("script") String script);

    @SqlQuery("select name from trick_names where trick = :id")
    List<String> getTrickNames(@Bind("id") int trickId);

    default int insertTrick(String script, long owner) {
        return getHandle().createUpdate("insert into tricks(script, owner) values (?, ?) returning id;")
                .bind(0, script)
                .bind(1, owner)
                .execute((rs, $) -> rs.get().getResultSet().getInt("id"));
    }

    @SqlUpdate("insert into trick_names(name, trick) values (:alias, :trick)")
    void addAlias(@Bind("trick") int trickId, @Bind("alias") String alias);

    @SqlUpdate("delete from trick_names where name = :alias")
    void deleteAlias(@Bind("alias") String alias);

    @SqlQuery("select name from trick_names where name like :query")
    List<String> findTricksMatching(@Bind("query") String query);

    @SqlUpdate("delete from tricks where id = :id")
    void delete(@Bind("id") int trickId);

}
