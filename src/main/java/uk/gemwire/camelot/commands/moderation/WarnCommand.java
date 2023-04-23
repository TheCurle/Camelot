package uk.gemwire.camelot.commands.moderation;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.Nullable;
import uk.gemwire.camelot.db.schemas.ModLogEntry;

import java.util.List;

public class WarnCommand extends ModerationCommand<Void> {
    public WarnCommand() {
        this.name = "warn";
        this.help = "Warns an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to warn", true),
                new OptionData(OptionType.STRING, "reason", "The user for warning the user", true)
        );
        this.userPermissions = new Permission[] {
                Permission.MODERATE_MEMBERS
        };
    }

    @Nullable
    @Override
    protected ModerationAction<Void> createEntry(SlashCommandEvent event) {
        final User target = event.optUser("user");
        if (target == null) {
            event.reply("Unknown user!").setEphemeral(true).queue();
            return null;
        }
        return new ModerationAction<>(
                ModLogEntry.warn(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), event.optString("reason")),
                null
        );
    }

    @Override
    protected RestAction<?> handle(User user, ModerationAction<Void> entry) {
        return null;
    }

}
