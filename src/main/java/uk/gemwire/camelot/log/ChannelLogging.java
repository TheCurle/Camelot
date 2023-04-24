package uk.gemwire.camelot.log;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import uk.gemwire.camelot.BotMain;

import java.util.Objects;
import java.util.function.Consumer;

public final class ChannelLogging {
    private final JDA jda;
    private final long channelId;
    private final Consumer<? super Object> successHandler = o -> {};
    private final Consumer<Throwable> errorHandler;

    private boolean acnowledgedUnknownChannel;

    public ChannelLogging(JDA jda, long channelId) {
        this.jda = jda;
        this.channelId = channelId;

        this.errorHandler = err -> BotMain.LOGGER.error("Could not send log message in channel with ID '{}'", channelId, err);
    }

    public void log(MessageEmbed... embeds) {
        log(MessageCreateData.fromEmbeds(embeds));
    }

    public void log(MessageCreateData createData) {
        withChannel(ch -> ch.sendMessage(createData).queue(this.successHandler, this.errorHandler));
    }

    public void withChannel(Consumer<MessageChannel> consumer) {
        final MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel != null) {
            consumer.accept(channel);
        } else if (!acnowledgedUnknownChannel) {
            acnowledgedUnknownChannel = true;
            BotMain.LOGGER.warn("Unknown logging channel with id '{}'", channelId);
        }
    }
}
