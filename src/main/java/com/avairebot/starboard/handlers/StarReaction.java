package com.avairebot.starboard.handlers;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;

public class StarReaction {

    private final TextChannel starboardChannel;
    private final Message message;

    StarReaction(Message message, TextChannel starboardChannel) {
        this.message = message;
        this.starboardChannel = starboardChannel;
    }

    public Message getMessage() {
        return message;
    }

    public TextChannel getStarboardChannel() {
        return starboardChannel;
    }
}
