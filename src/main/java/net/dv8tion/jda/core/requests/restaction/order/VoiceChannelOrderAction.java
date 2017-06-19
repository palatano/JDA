package net.dv8tion.jda.core.requests.restaction.order;

import java.util.Collection;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;

public class VoiceChannelOrderAction extends ChannelOrderAction<VoiceChannel>
{

    public VoiceChannelOrderAction(Guild guild)
    {
        super(guild, VoiceChannelOrderAction.class, ChannelType.VOICE);
    }

    @Override
    protected Collection<VoiceChannel> getChannels()
    {
        return guild.getVoiceChannels();
    }
}
