package com.avairebot.starboard.command;

import com.avairebot.commands.Category;
import com.avairebot.commands.CategoryHandler;
import com.avairebot.commands.CommandMessage;
import com.avairebot.contracts.commands.Command;
import com.avairebot.starboard.Starboard;
import com.avairebot.utilities.ComparatorUtil;
import com.avairebot.utilities.MentionableUtil;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StarboardCommand extends Command {

    private final Starboard starboard;

    public StarboardCommand(Starboard starboard) {
        super(starboard, false);

        this.starboard = starboard;
    }

    @Override
    public String getName() {
        return "Starboard Command";
    }

    @Override
    public String getDescription() {
        return "Can be used to see the current starboard channel, if an channel is mentioned, the mentioned channel will be set to the new starboard channel.";
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
                "`:command` - Displays the current starboard channel.",
                "`:command off` - Disables the starboard channel.",
                "`:command <channel>` - Sets the starboard channel to the mentioned channel."
        );
    }

    @Override
    public List<String> getExampleUsage() {
        return Collections.singletonList(
                "`:command #starboard` - Enables the starboard for the starboard channel."
        );
    }

    @Override
    public List<String> getTriggers() {
        return Collections.singletonList("starboard");
    }

    @Override
    public List<String> getMiddleware() {
        return Arrays.asList(
                "require:user,general.manage_server",
                "throttle:guild,1,5"
        );
    }

    @Override
    public Category getCategory() {
        return CategoryHandler.fromLazyName("administration");
    }

    @Override
    public boolean onCommand(CommandMessage context, String[] args) {
        String starboardValue = starboard.getStarboardValueFromGuild(context.getGuild().getId());

        if (args.length == 0) {
            if (starboardValue == null || starboardValue.trim().length() == 0) {
                return sendStarboardNotSetMessage(context);
            }

            TextChannel starboardChannel = avaire.getShardManager().getTextChannelById(starboardValue);
            if (starboardChannel == null) {
                return sendStarboardNotSetMessage(context);
            }

            context.makeInfo("The :starboard channel is currently used for star messages.")
                    .set("starboard", starboardChannel.getAsMention())
                    .queue();

            return true;
        }

        if (ComparatorUtil.isFuzzyFalse(args[0])) {
            if (starboard.updateStarboardDatabaseValue(context.getGuild().getId(), null)) {
                context.makeSuccess("The starboard channel has been disabled successfully.")
                        .queue();

                return true;
            }

            return sendErrorMessage(context, "Failed to update the starboard value for the server, please try again later.");
        }

        Channel channel = MentionableUtil.getChannel(context.getMessage(), args);
        if (channel == null || !(channel instanceof TextChannel)) {
            return sendErrorMessage(context, "Invalid channel mentioned, you just mention or name a valid text channel.");
        }

        if (!starboard.updateStarboardDatabaseValue(context.getGuild().getId(), channel.getId())) {
            return sendErrorMessage(context, "Failed to update the starboard value for the server, please try again later.");
        }

        context.makeSuccess("The starboard channel has been successfully set to the :starboard channel!")
                .set("starboard", ((TextChannel) channel).getAsMention())
                .queue();

        return true;
    }

    private boolean sendStarboardNotSetMessage(CommandMessage context) {
        context.makeInfo("There are not any starboard channel set right now, you can set one by using the `:command` command")
                .set("command", generateCommandTrigger(context.getMessage()) + " <channel>")
                .queue();

        return true;
    }
}
