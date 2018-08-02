package com.avairebot.starboard.handlers;

import com.avairebot.contracts.handlers.EventListener;
import com.avairebot.starboard.Starboard;
import com.avairebot.utilities.RestActionUtil;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class EmoteEventListener extends EventListener {

    public static final Map<Long, StarReaction> queue = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(EmoteEventListener.class);

    private final Starboard starboard;

    public EmoteEventListener(Starboard starboard) {
        this.starboard = starboard;
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getReactionEmote().getName().equals("\u2B50")) {
            return;
        }
        handleStarEvent(event.getReaction(), event.getMember(), event.getMessageIdLong());
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        if (!event.getReactionEmote().getName().equals("\u2B50")) {
            return;
        }
        handleStarEvent(event.getReaction(), event.getMember(), event.getMessageIdLong());
    }

    private void handleStarEvent(MessageReaction messageReaction, Member member, long messageId) {
        String starboardChannelId = starboard.getStarboardValueFromGuild(messageReaction.getGuild().getId());
        if (starboardChannelId == null || starboardChannelId.trim().length() == 0) {
            return;
        }

        TextChannel starboardChannel = messageReaction.getGuild().getTextChannelById(starboardChannelId);
        if (starboardChannel == null) {
            LOGGER.warn("A starboard channel ID has been not, but no channel was found with the set ID {0}, maybe a mistake?", starboardChannelId);
            return;
        }

        messageReaction.getTextChannel().getMessageById(messageId).queue(message -> {
            if (starboard.getIgnoreOwnMessages() && message.getAuthor().getIdLong() == member.getUser().getIdLong()) {
                return;
            }

            queue.put(messageId, new StarReaction(message, starboardChannel));
        }, RestActionUtil.IGNORE);
    }
}
