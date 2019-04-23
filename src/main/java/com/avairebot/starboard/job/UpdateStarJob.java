package com.avairebot.starboard.job;

import com.avairebot.contracts.scheduler.Job;
import com.avairebot.database.collection.DataRow;
import com.avairebot.factories.MessageFactory;
import com.avairebot.starboard.Starboard;
import com.avairebot.starboard.handlers.EmoteEventListener;
import com.avairebot.starboard.handlers.StarReaction;
import com.avairebot.utilities.RestActionUtil;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UpdateStarJob extends Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateStarJob.class);

    private final Starboard starboard;

    public UpdateStarJob(Starboard starboard) {
        super(starboard.getAvaire(), 10000, 2500, TimeUnit.MILLISECONDS);

        this.starboard = starboard;
    }

    @Override
    public void run() {
        Map<Long, StarReaction> reactions;
        synchronized (EmoteEventListener.queue) {
            reactions = new HashMap<>(EmoteEventListener.queue);
            EmoteEventListener.queue.clear();
        }

        for (Map.Entry<Long, StarReaction> entry : reactions.entrySet()) {
            try {
                handleReaction(
                        entry.getValue().getMessage(),
                        entry.getValue().getStarboardChannel(),
                        entry.getKey()
                );
            } catch (SQLException e) {
                LOGGER.error("Failed to fetch the starboard message ID from the database: " + entry.getKey(), e);
            }
        }
    }

    private void handleReaction(Message message, TextChannel starboardChannel, long messageId) throws SQLException {
        MessageReaction reaction = getReactionFromList(message.getReactions());
        String dynamicJumpUrl = String.format("[**Jump to original**](%s)", message.getJumpUrl());
        int emoteCount = getEmoteCount(reaction);
        if (emoteCount < starboard.getReactionRequirement()) {
            DataRow row = starboard.getDatabase()
                    .newQueryBuilder(Starboard.STARBOARD_TABLE)
                    .where("original_id", messageId)
                    .get()
                    .first();

            if (row == null) {
                return;
            }

            starboardChannel.getMessageById(row.getString("message_id")).queue(starboardMessage -> {
                starboardMessage.delete().queue(aVoid -> {
                    try {
                        starboard.getDatabase()
                                .newQueryBuilder(Starboard.STARBOARD_TABLE)
                                .where("original_id", messageId)
                                .delete();
                    } catch (SQLException e) {
                        LOGGER.error("Failed to delete the original star reaction record with an ID of {}", messageId);
                    }
                }, RestActionUtil.ignore);
            }, RestActionUtil.ignore);
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
                .setContent(String.format("%s **%s** %s",
                        starboard.getStarEmote(emoteCount),
                        emoteCount,
                        message.getTextChannel().getAsMention()
                ))
                .setEmbed(embedBuilder.setDescription(message.getContentRaw() + "\n\n" + dynamicJumpUrl).build())
                .build();

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
    }

    private MessageReaction getReactionFromList(List<MessageReaction> reactions) {
        for (MessageReaction reaction : reactions) {
            if (reaction.getReactionEmote().getName().equals("\u2B50")) {
                return reaction;
            }
        }
        return null;
    }

    private int getEmoteCount(MessageReaction reaction) {
        try {
            return reaction.getCount();
        } catch (Exception ignored) {
            return 0;
        }
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
