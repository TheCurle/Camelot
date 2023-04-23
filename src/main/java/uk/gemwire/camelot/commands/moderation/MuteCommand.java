package uk.gemwire.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.util.DateUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class MuteCommand extends ModerationCommand<Void> {
    public static final long MAX_DURATION = Duration.ofDays(27).getSeconds();

    public MuteCommand() {
        this.name = "mute";
        this.help = "Mutes an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to mute", true),
                new OptionData(OptionType.STRING, "reason", "The user for muting the user", true),
                new OptionData(OptionType.STRING, "duration", "How much to mute the user for", false)
        );
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
    }

    @Nullable
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
        final User target = event.optUser("user");
        if (target == null) {
            event.reply("Unknown user!").setEphemeral(true).queue();
            return null;
        }
        final Duration time = event.getOption("duration", () -> Duration.ofSeconds(MAX_DURATION), it -> DateUtils.getDurationFromInput(it.getAsString()));
        if (time != null && time.getSeconds() > MAX_DURATION) {
            event.reply("Cannot mute for more than " + Duration.ofSeconds(MAX_DURATION).get(ChronoUnit.DAYS) + " days.").setEphemeral(true).queue();
            return null;
        }
        return new ModerationAction<>(
                ModLogEntry.mute(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), time, event.optString("reason")),
                null
        );
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    protected RestAction<?> handle(User user, ModerationAction<Void> action) {
        final ModLogEntry entry = action.entry();
        return user.getJDA().getGuildById(entry.guild())
                .retrieveMemberById(entry.user())
                .map(mem -> mem.timeoutFor(entry.duration()).reason("rec: " + entry.reasonOrDefault()));
    }

}
