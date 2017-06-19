package net.dv8tion.jda.core.requests.restaction.order;

import java.util.Collection;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;

public class TextChannelOrderAction extends ChannelOrderAction<TextChannel>
{

    public TextChannelOrderAction(Guild guild)
    {
        super(guild, TextChannelOrderAction.class, ChannelType.TEXT);
    }

    @Override
    protected Collection<TextChannel> getChannels()
    {
        return guild.getTextChannels();
    }
}
