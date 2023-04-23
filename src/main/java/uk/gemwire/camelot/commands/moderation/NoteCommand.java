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

public class NoteCommand extends ModerationCommand<Void> {
    public NoteCommand() {
        this.name = "note";
        this.help = "Add a note to an user";
        this.options = List.of(
                new OptionData(OptionType.USER, "user", "The user to add a note to", true),
                new OptionData(OptionType.STRING, "note", "The note content", true)
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
        return new ModerationAction<>(
                ModLogEntry.note(target.getIdLong(), event.getGuild().getIdLong(), event.getUser().getIdLong(), event.optString("reason")),
                null
        );
    }

    @Override
    protected RestAction<?> handle(User user, ModerationAction<Void> action) {
        return null;
    }

}
