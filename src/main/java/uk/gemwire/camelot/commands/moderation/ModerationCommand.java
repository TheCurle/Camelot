package uk.gemwire.camelot.commands.moderation;

import com.google.common.base.Preconditions;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.configuration.Config;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.ModLogsDAO;

import javax.annotation.ParametersAreNullableByDefault;
import java.util.concurrent.CompletableFuture;

public abstract class ModerationCommand<T> extends SlashCommand {

    protected ModerationCommand() {
        this.guildOnly = true;
    }

    protected boolean shouldDMUser = true;

    @Nullable
    protected abstract ModerationAction<T> createEntry(SlashCommandEvent event);

    @Override
    protected final void execute(SlashCommandEvent event) {
        final ModerationAction<T> action;
        try {
            action = createEntry(event);
        } catch (IllegalArgumentException exception) {
            event.reply("Failed to validate arguments: " + exception.getMessage())
                    .setEphemeral(true).queue();
            return;
        }

        if (action == null) return;
        final ModLogEntry entry = action.entry;

        entry.setId(BotMain.jdbi().withExtension(ModLogsDAO.class, dao -> dao.insert(entry)));
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

    @ParametersAreNullableByDefault
    protected final boolean canModerate(Member target, Member moderator) {
        Preconditions.checkArgument(target != null, "Unknown user!");
        Preconditions.checkArgument(moderator != null, "Can only run command in guild!");
        final Guild guild = target.getGuild();
        return moderator.canInteract(target) && guild.getSelfMember().canInteract(target);
    }

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
                    action.entry.format(interaction.getJDA())
                            .thenAccept(caseData -> Config.MODERATION_LOGS.log(new EmbedBuilder()
                                    .setTitle("%s has been %s".formatted(user.getAsTag(), action.entry.type().getAction()))
                                    .setDescription("Case information below:")
                                    .addField(caseData)
                                    .setTimestamp(action.entry.timestamp())
                                    .setFooter("User ID: " + user.getId(), user.getAvatarUrl())
                                    .setColor(action.entry.type().getColor())
                                    .build()))
                            .exceptionally((ex) -> {
                                return null;
                            });

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
