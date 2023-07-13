package uk.gemwire.camelot.commands.information;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.commands.Commands;
import uk.gemwire.camelot.configuration.Common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class HelpCommand {

    private static final int COMMANDS_PER_PAGE = 25;

    /**
     * Prepare the potential scrolling buttons for a help command,
     *  and send the message with the proper embeds.
     *
     * See {@link #getHelpStartingAt(int)} for the implementation.
     * @param e
     */
    public static void help(CommandEvent e) {
        MessageAction reply = e.getChannel().sendMessageEmbeds(getHelpStartingAt(0).build());
        Component[] buttons = createScrollButtons(0);
        if (buttons.length > 0)
            reply.setActionRow(buttons);

        reply.queue();
    }

    /**
     * Create the row of Component interaction buttons.
     * <p>
     * Currently, this just creates a left and right arrow.
     * Left arrow scrolls back a page. Right arrow scrolls forward a page.
     *
     * @param start The quote number at the start of the current page.
     * @return A row of buttons to go back and forth by one page in a quote list.
     */
    private static Component[] createScrollButtons(int start) {
        List<Component> components = new ArrayList<>();
        if (start != 0) {
            components.add(Button.secondary(ButtonListener.BUTTON_ID_PREFIX + "-" + start + "-prev",
                    Emoji.fromUnicode("◀️")));
        }
        if (start + COMMANDS_PER_PAGE < Commands.get().getSlashCommands().size() + Commands.get().getSlashCommands().size()) {
            components.add(Button.primary(ButtonListener.BUTTON_ID_PREFIX + "-" + start + "-next",
                    Emoji.fromUnicode("▶️")));
        }
        return components.toArray(new Component[0]);
    }

    /**
     * Given a starting index, build an embed that we can display for users
     *  to summarise all available commands.
     * Intended to be used with pagination in the case of servers with LOTS of commands.
     */
    private static EmbedBuilder getHelpStartingAt(int index) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(Common.NAME, Common.REPO, BotMain.get().getSelfUser().getAvatarUrl());
        embed.setDescription("All registered commands:");

        List<Command> commandList = Commands.get().getCommands();
        commandList.addAll(Commands.get().getSlashCommands());

        // Embeds have a 25 field limit. We need to make sure we don't exceed that.
        if(commandList.size() < 25) {
            for (Command c : commandList)
                embed.addField(c.getName(), c.getHelp(), true);
        } else {
            // Make sure we only go up to the limit.
            for (int i = index; i < index + 25; i++)
                if (i < commandList.size())
                    embed.addField(commandList.get(i).getName(), commandList.get(i).getHelp(), true);
        }

        embed.setFooter("Camelot").setTimestamp(Instant.now());

        return embed;
    }

    public static class ButtonListener extends ListenerAdapter {
        private static final String BUTTON_ID_PREFIX = "help";

        @Override
        public void onButtonClick(@NotNull final ButtonClickEvent event) {
            var button = event.getButton();
            if (button == null || button.getId() == null) {
                return;
            }

            String[] idParts = button.getId().split("-");
            if (idParts.length != 3) {
                return;
            }

            if (!idParts[0].equals(BUTTON_ID_PREFIX)) {
                return;
            }

            int current = Integer.parseInt(idParts[1]);

            if (idParts[2].equals("next")) {
                event
                        .editMessageEmbeds(getHelpStartingAt(current + COMMANDS_PER_PAGE).build())
                        .setActionRow(createScrollButtons(current + COMMANDS_PER_PAGE))
                        .queue();
            } else {
                if (idParts[2].equals("prev")) {
                    event
                            .editMessageEmbeds(getHelpStartingAt(current - COMMANDS_PER_PAGE).build())
                            .setActionRow(createScrollButtons(current - COMMANDS_PER_PAGE))
                            .queue();
                }
            }
        }
    }
}
