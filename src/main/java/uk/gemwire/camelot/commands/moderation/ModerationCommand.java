package uk.gemwire.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.ModLogsDAO;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ModerationCommand<T> extends SlashCommand {

    protected ModerationCommand() {
        this.guildOnly = true;
    }

    protected boolean shouldDMUser = true;

    @Nullable
    protected abstract ModerationAction<T> createEntry(SlashCommandEvent event);

    @Override
    protected final void execute(SlashCommandEvent event) {
        final ModerationAction<T> action = createEntry(event);
        if (action == null) return;
        final ModLogEntry entry = action.entry;

        BotMain.jdbi().useExtension(ModLogsDAO.class, dao -> dao.insert(entry));
        event.deferReply().queue();
        event.getJDA().retrieveUserById(entry.user())
            .submit()
            .thenCompose(usr -> {
                if (shouldDMUser) {
                    return dmUser(entry, usr).submit();
                }
                return CompletableFuture.completedFuture(null);
            })
            .whenComplete((msg, t) -> {
                if (t == null) {
                    logAndExecute(action, event.getHook(), true);
                } else {
                    logAndExecute(action, event.getHook(), false);
                    if (t instanceof ErrorResponseException ex && ex.getErrorResponse() != ErrorResponse.CANNOT_SEND_TO_USER) {
                        BotMain.LOGGER.error("Encountered exception DMing user {}: ", entry.user(), ex);
                    }
                }
            });
    }

    protected abstract RestAction<?> handle(User user, ModerationAction<T> action);

    protected RestAction<Message> dmUser(ModLogEntry entry, User user) {
        final Guild guild = user.getJDA().getGuildById(entry.guild());
        final EmbedBuilder builder = new EmbedBuilder()
                .setAuthor(guild.getName(), null, guild.getIconUrl())
                .setDescription("You have been **" + entry.type().getAction() + "** in **" + guild.getName() + "**.")
                .addField("Reason", entry.reasonOrDefault(), false)
                .setColor(entry.type().getColor())
                .setTimestamp(entry.timestamp());
        if (entry.duration() != null) {
            builder.addField("Duration", entry.formatDuration(), false);
        }
        return user.openPrivateChannel()
                .flatMap(ch -> ch.sendMessageEmbeds(builder.build()));
    }

    protected void logAndExecute(ModerationAction<T> action, InteractionHook interaction, boolean dmedUser) {
        interaction.getJDA().retrieveUserById(action.entry.user())
                .flatMap(user -> {
                    final EmbedBuilder builder = new EmbedBuilder()
                            .setDescription("%s has been %s. | **%s**".formatted(user.getAsTag(), action.entry.type().getAction(), action.entry.reasonOrDefault()))
                            .setTimestamp(action.entry.timestamp())
                            .setColor(action.entry().type().getColor());
                    if (!dmedUser && shouldDMUser) {
                        builder.setFooter("User could not be DMed");
                    }
                    final var edit = interaction.editOriginal(MessageEditData.fromEmbeds(builder.build()));

                    final var handle = handle(user, action);
                    if (handle == null) {
                        return edit;
                    }
                    return handle.flatMap(it -> edit);
                })
                .queue();
    }

    public record ModerationAction<T>(
            ModLogEntry entry,
            T additionalData
    ) {}
}
