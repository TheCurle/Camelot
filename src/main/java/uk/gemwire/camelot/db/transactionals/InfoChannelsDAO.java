package uk.gemwire.camelot.db.transactionals;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.InfoChannel;

import java.util.List;

@RegisterRowMapper(InfoChannel.Mapper.class)
public interface InfoChannelsDAO extends Transactional<InfoChannelsDAO> {
    @SqlQuery("select * from info_channels")
    List<InfoChannel> getChannels();

    @Nullable
    @SqlQuery("select * from info_channels where channel = :id")
    InfoChannel getChannel(@Bind("id") long id);

    @SqlUpdate("delete from info_channels where channel = :channel")
    void delete(@Bind("channel") long channelId);

    @SqlUpdate("update info_channels set hash = :hash where channel = :channel")
    void updateHash(@Bind("channel") long channelId, @Bind("hash") String hash);

    default void insert(InfoChannel infoChannel) {
        getHandle().createUpdate("insert or replace into info_channels (channel, location, force_recreate, hash) values (?, ?, ?, ?)")
                .bind(0, infoChannel.channel())
                .bind(1, infoChannel.location().toString())
                .bind(2, infoChannel.forceRecreate())
                .bind(3, infoChannel.hash())
                .execute();
    }
}
