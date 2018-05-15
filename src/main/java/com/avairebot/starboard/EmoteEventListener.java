package com.avairebot.starboard;

import com.avairebot.contracts.handlers.EventListener;
import com.avairebot.database.collection.DataRow;
import com.avairebot.factories.MessageFactory;
import com.avairebot.utilities.RestActionUtil;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class EmoteEventListener extends EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmoteEventListener.class);

    private final Starboard starboard;

    EmoteEventListener(Starboard starboard) {
        this.starboard = starboard;
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (!event.getReactionEmote().getName().equals("\u2B50")) {
            return;
        }
        handleStarEvent(event.getReaction(), event.getMember(), event.getMessageIdLong(), true);
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        if (!event.getReactionEmote().getName().equals("\u2B50")) {
            return;
        }
        handleStarEvent(event.getReaction(), event.getMember(), event.getMessageIdLong(), false);
    }

    private int getEmoteCount(MessageReaction reaction) {
        try {
            return reaction.getCount();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private MessageReaction getReactionFromList(List<MessageReaction> reactions) {
        for (MessageReaction reaction : reactions) {
            if (reaction.getReactionEmote().getName().equals("\u2B50")) {
                return reaction;
            }
        }
        return null;
    }

    private void handleStarEvent(MessageReaction messageReaction, Member member, long messageId, boolean isAdd) {
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

            MessageReaction reaction = getReactionFromList(message.getReactions());
            int emoteCount = getEmoteCount(reaction);
            if (isAdd && emoteCount < starboard.getReactionRequirement()) {
                return;
            }

            EmbedBuilder embedBuilder = MessageFactory.createEmbeddedBuilder()
                    .setColor(starboard.getColor((float) emoteCount / 13))
                    .setTimestamp(message.getCreationTime())
                    .setAuthor(message.getAuthor().getName(), null, message.getAuthor().getEffectiveAvatarUrl())
                    .setDescription(message.getContentRaw());

            if (!message.getAttachments().isEmpty()) {
                for (Message.Attachment attachment : message.getAttachments()) {
                    if (attachment.isImage()) {
                        embedBuilder.setImage(attachment.getUrl());
                        break;
                    }
                }
            }

            Message build = (new MessageBuilder())
                    .setContent(String.format("%s **%s** %s ID: %s",
                            starboard.getStarEmote(emoteCount),
                            emoteCount,
                            message.getTextChannel().getAsMention(),
                            message.getId()
                    ))
                    .setEmbed(embedBuilder.build())
                    .build();

            try {
                DataRow row = starboard.getDatabase()
                        .newQueryBuilder(Starboard.STARBOARD_TABLE)
                        .where("original_id", messageId)
                        .get()
                        .first();

                if (row == null) {
                    if (emoteCount < starboard.getReactionRequirement()) {
                        return;
                    }
                    starboardChannel.sendMessage(build).queue(consumer -> createNewRecord(consumer, messageId));
                } else {
                    starboardChannel.getMessageById(row.getString("message_id")).queue(starMessage -> {
                        if (emoteCount < starboard.getReactionRequirement()) {
                            starMessage.delete().queue(aVoid -> deleteMessageRecord(messageId));

                            deleteMessageRecord(messageId);
                        } else {
                            starMessage.editMessage(build).queue();
                        }
                    }, err -> deleteMessageRecord(messageId));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to fetch the starboard message ID from the database: " + messageId, e);
            }
        }, RestActionUtil.IGNORE);
    }

    private void createNewRecord(Message message, long originalId) {
        try {
            starboard.getAvaire().getDatabase()
                    .newQueryBuilder(Starboard.STARBOARD_TABLE)
                    .insert(statement -> {
                        statement.set("original_id", originalId);
                        statement.set("message_id", message.getId());
                    });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deleteMessageRecord(long originalId) {
        try {
            starboard.getDatabase()
                    .newQueryBuilder(Starboard.STARBOARD_TABLE)
                    .where("original_id", originalId)
                    .delete();
        } catch (SQLException e) {
            LOGGER.error("Failed to fetch the starboard message ID from the database: " + originalId, e);
        }
    }
}
