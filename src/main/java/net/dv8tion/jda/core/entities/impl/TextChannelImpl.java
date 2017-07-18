/*
 *     Copyright 2015-2017 Austin Keener & Michael Ritter & Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.core.entities.impl;

import net.dv8tion.jda.client.exceptions.VerificationLevelException;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.requests.Request;
import net.dv8tion.jda.core.requests.Response;
import net.dv8tion.jda.core.requests.RestAction;
import net.dv8tion.jda.core.requests.Route;
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.core.utils.MiscUtil;
import net.dv8tion.jda.core.utils.Checks;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class TextChannelImpl extends AbstractChannelImpl<TextChannelImpl> implements TextChannel
{
    private String topic;
    private long lastMessageId;

    public TextChannelImpl(long id, GuildImpl guild)
    {
        super(id, guild);
    }

    @Override
    public String getAsMention()
    {
        return "<#" + id + '>';
    }

    @Override
    public RestAction<List<Webhook>> getWebhooks()
    {
        checkPermission(Permission.MANAGE_WEBHOOKS);

        Route.CompiledRoute route = Route.Channels.GET_WEBHOOKS.compile(getId());
        return new RestAction<List<Webhook>>(getJDA(), route)
        {
            @Override
            protected void handleResponse(Response response, Request<List<Webhook>> request)
            {
                if (!response.isOk())
                {
                    request.onFailure(response);
                    return;
                }

                List<Webhook> webhooks = new LinkedList<>();
                JSONArray array = response.getArray();
                EntityBuilder builder = api.getEntityBuilder();

                for (Object object : array)
                {
                    try
                    {
                        webhooks.add(builder.createWebhook((JSONObject) object));
                    }
                    catch (JSONException | NullPointerException e)
                    {
                        JDAImpl.LOG.log(e);
                    }
                }

                request.onSuccess(webhooks);
            }
        };
    }

    @Override
    public RestAction<Void> deleteMessages(Collection<Message> messages)
    {
        Checks.notEmpty(messages, "Messages collection");

        return deleteMessagesByIds(messages.stream()
                .map(ISnowflake::getId)
                .collect(Collectors.toList()));
    }

    @Override
    public RestAction<Void> deleteMessagesByIds(Collection<String> messageIds)
    {
        checkPermission(Permission.MESSAGE_MANAGE, "Must have MESSAGE_MANAGE in order to bulk delete messages in this channel regardless of author.");
        if (messageIds.size() < 2 || messageIds.size() > 100)
            throw new IllegalArgumentException("Must provide at least 2 or at most 100 messages to be deleted.");

        long twoWeeksAgo = ((System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000)) - MiscUtil.DISCORD_EPOCH) << MiscUtil.TIMESTAMP_OFFSET;
        for (String id : messageIds)
        {
            Checks.notEmpty(id, "Message id in messageIds");
            Checks.check(MiscUtil.parseSnowflake(id) > twoWeeksAgo, "Message Id provided was older than 2 weeks. Id: " + id);
        }

        JSONObject body = new JSONObject().put("messages", messageIds);
        Route.CompiledRoute route = Route.Messages.DELETE_MESSAGES.compile(getId());
        return new RestAction<Void>(getJDA(), route, body)
        {
            @Override
            protected void handleResponse(Response response, Request<Void> request)
            {
                if (response.isOk())
                    request.onSuccess(null);
                else
                    request.onFailure(response);
            }
        };
    }

    @Override
    public AuditableRestAction<Void> deleteWebhookById(String id)
    {
        Checks.notEmpty(id, "webhook id");

        if (!guild.getSelfMember().hasPermission(this, Permission.MANAGE_WEBHOOKS))
            throw new PermissionException(Permission.MANAGE_WEBHOOKS);

        Route.CompiledRoute route = Route.Webhooks.DELETE_WEBHOOK.compile(id);
        return new AuditableRestAction<Void>(getJDA(), route)
        {
            @Override
            protected void handleResponse(Response response, Request<Void> request)
            {
                if (response.isOk())
                    request.onSuccess(null);
                else
                    request.onFailure(response);
            }
        };
    }

    @Override
    public boolean canTalk()
    {
        return canTalk(guild.getSelfMember());
    }

    @Override
    public boolean canTalk(Member member)
    {
        if (!guild.equals(member.getGuild()))
            throw new IllegalArgumentException("Provided Member is not from the Guild that this TextChannel is part of.");

        return member.hasPermission(this, Permission.MESSAGE_READ, Permission.MESSAGE_WRITE);
    }

    @Override
    public long getLatestMessageIdLong()
    {
        final long messageId = lastMessageId;
        if (messageId < 0)
            throw new IllegalStateException("No last message id found.");
        return messageId;
    }

    @Override
    public boolean hasLatestMessage()
    {
        return lastMessageId > 0;
    }

    @Override
    public ChannelType getType()
    {
        return ChannelType.TEXT;
    }

    @Override
    public String getTopic()
    {
        return topic;
    }

    @Override
    public boolean isNSFW() {
        return name.equals("nsfw") || name.startsWith("nsfw-");
    }

    @Override
    public List<Member> getMembers()
    {
        guild.checkDisposed();
        return Collections.unmodifiableList(guild.getMembersMap().valueCollection().stream()
                .filter(m -> m.hasPermission(this, Permission.MESSAGE_READ))
                .collect(Collectors.toList()));
    }

    @Override
    public int getPosition()
    {
        //We call getTextChannels instead of directly accessing the GuildImpl.getTextChannelMap because
        // getTextChannels does the sorting logic.
        List<TextChannel> channels = guild.getTextChannels();
        for (int i = 0; i < channels.size(); i++)
        {
            if (channels.get(i) == this)
                return i;
        }
        throw new RuntimeException("Somehow when determining position we never found the TextChannel in the Guild's channels? wtf?");
    }

    @Override
    public RestAction<Message> sendMessage(Message msg)
    {
        Checks.notNull(msg, "Message");

        checkVerification();
        checkPermission(Permission.MESSAGE_READ);
        checkPermission(Permission.MESSAGE_WRITE);
        if (msg.getRawContent().isEmpty() && !msg.getEmbeds().isEmpty())
            checkPermission(Permission.MESSAGE_EMBED_LINKS);

        //Call MessageChannel's default
        return TextChannel.super.sendMessage(msg);
    }

    @Override
    public RestAction<Message> sendFile(InputStream data, String fileName, Message message)
    {
        checkVerification();
        checkPermission(Permission.MESSAGE_READ);
        checkPermission(Permission.MESSAGE_WRITE);
        checkPermission(Permission.MESSAGE_ATTACH_FILES);

        //Call MessageChannel's default method
        return TextChannel.super.sendFile(data, fileName, message);
    }

    @Override
    public RestAction<Message> sendFile(byte[] data, String fileName, Message message)
    {
        checkVerification();
        checkPermission(Permission.MESSAGE_READ);
        checkPermission(Permission.MESSAGE_WRITE);
        checkPermission(Permission.MESSAGE_ATTACH_FILES);

        //Call MessageChannel's default method
        return TextChannel.super.sendFile(data, fileName, message);
    }

    @Override
    public RestAction<Message> getMessageById(String messageId)
    {
        checkPermission(Permission.MESSAGE_READ);
        checkPermission(Permission.MESSAGE_HISTORY);

        //Call MessageChannel's default method
        return TextChannel.super.getMessageById(messageId);
    }

    @Override
    public AuditableRestAction<Void> deleteMessageById(String messageId)
    {
        Checks.notEmpty(messageId, "messageId");
        checkPermission(Permission.MESSAGE_READ);

        //Call MessageChannel's default method
        return TextChannel.super.deleteMessageById(messageId);
    }

    @Override
    public RestAction<MessageHistory> getHistoryAround(String messageId, int limit)
    {
        checkPermission(Permission.MESSAGE_READ);
        checkPermission(Permission.MESSAGE_HISTORY);

        //Call MessageChannel's default method
        return TextChannel.super.getHistoryAround(messageId, limit);
    }

    @Override
    public RestAction<Void> pinMessageById(String messageId)
    {
        checkPermission(Permission.MESSAGE_READ, "You cannot pin a message in a channel you can't access. (MESSAGE_READ)");
        checkPermission(Permission.MESSAGE_MANAGE, "You need MESSAGE_MANAGE to pin or unpin messages.");

        //Call MessageChannel's default method
        return TextChannel.super.pinMessageById(messageId);
    }

    @Override
    public RestAction<Void> unpinMessageById(String messageId)
    {
        checkPermission(Permission.MESSAGE_READ, "You cannot unpin a message in a channel you can't access. (MESSAGE_READ)");
        checkPermission(Permission.MESSAGE_MANAGE, "You need MESSAGE_MANAGE to pin or unpin messages.");

        //Call MessageChannel's default method
        return TextChannel.super.unpinMessageById(messageId);
    }

    @Override
    public RestAction<List<Message>> getPinnedMessages()
    {
        checkPermission(Permission.MESSAGE_READ, "Cannot get the pinned message of a channel without MESSAGE_READ access.");

        //Call MessageChannel's default method
        return TextChannel.super.getPinnedMessages();
    }

    @Override
    public RestAction<Void> addReactionById(String messageId, String unicode)
    {
        checkPermission(Permission.MESSAGE_ADD_REACTION);
        checkPermission(Permission.MESSAGE_HISTORY);

        //Call MessageChannel's default method
        return TextChannel.super.addReactionById(messageId, unicode);
    }

    @Override
    public RestAction<Void> addReactionById(String messageId, Emote emote)
    {
        checkPermission(Permission.MESSAGE_ADD_REACTION);
        checkPermission(Permission.MESSAGE_HISTORY);

        //Call MessageChannel's default method
        return TextChannel.super.addReactionById(messageId, emote);
    }

    @Override
    public RestAction<Void> clearReactionsById(String messageId)
    {
        Checks.notEmpty(messageId, "Message ID");

        checkPermission(Permission.MESSAGE_MANAGE);
        Route.CompiledRoute route = Route.Messages.REMOVE_ALL_REACTIONS.compile(getId(), messageId);
        return new RestAction<Void>(getJDA(), route)
        {
            @Override
            protected void handleResponse(Response response, Request<Void> request)
            {
                if (response.isOk())
                    request.onSuccess(null);
                else
                    request.onFailure(response);
            }
        };
    }

    @Override
    public RestAction<Message> editMessageById(String id, Message newContent)
    {
        Checks.notNull(newContent, "Message");

        //checkVerification(); no verification needed to edit a message
        checkPermission(Permission.MESSAGE_READ);
        checkPermission(Permission.MESSAGE_WRITE);
        if (newContent.getRawContent().isEmpty() && !newContent.getEmbeds().isEmpty())
            checkPermission(Permission.MESSAGE_EMBED_LINKS);

        //Call MessageChannel's default
        return TextChannel.super.editMessageById(id, newContent);
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof TextChannelImpl))
            return false;
        TextChannelImpl oTChannel = (TextChannelImpl) o;
        return this == oTChannel || this.id == oTChannel.id;
    }

    @Override
    public String toString()
    {
        return "TC:" + getName() + '(' + id + ')';
    }

    @Override
    public int compareTo(TextChannel chan)
    {
        if (this == chan)
            return 0;

        if (!this.getGuild().equals(chan.getGuild()))
            throw new IllegalArgumentException("Cannot compare TextChannels that aren't from the same guild!");

        if (this.getPositionRaw() != chan.getPositionRaw())
            return chan.getPositionRaw() - this.getPositionRaw();

        OffsetDateTime thisTime = this.getCreationTime();
        OffsetDateTime chanTime = chan.getCreationTime();

        //We compare the provided channel's time to this's time instead of the reverse as one would expect due to how
        // discord deals with hierarchy. The more recent a channel was created, the lower its hierarchy ranking when
        // it shares the same position as another channel.
        return chanTime.compareTo(thisTime);
    }

    @Override
    public boolean dispose()
    {
        guild.getJDA().getTextChannelMap().remove(id);
        return super.dispose();
    }

    // -- Setters --

    public TextChannelImpl setTopic(String topic)
    {
        this.topic = topic;
        return this;
    }

    public TextChannelImpl setLastMessageId(long id)
    {
        this.lastMessageId = id;
        return this;
    }

    // -- internal --

    private void checkVerification()
    {
        if (!guild.checkVerification())
            throw new VerificationLevelException(guild.getVerificationLevel());
    }
}
