package core.commands;

import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.parsers.NpParser;
import core.parsers.Parser;
import core.parsers.params.NowPlayingParameters;
import dao.ChuuService;
import dao.entities.NowPlayingArtist;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

abstract class NpCommand extends ConcurrentCommand<NowPlayingParameters> {


    NpCommand(ChuuService dao) {
        super(dao);
    }

    @Override
    protected CommandCategory getCategory() {
        return CommandCategory.NOW_PLAYING;
    }

    @Override
    public Parser<NowPlayingParameters> getParser() {
        return new NpParser(getService(), lastFM);
    }

    @Override
    public void onCommand(MessageReceivedEvent e) throws LastFmException, InstanceNotFoundException {
        NowPlayingParameters parse = parser.parse(e);
        doSomethingWithArtist(parse.getNowPlayingArtist(), e, parse.getLastFMData().getDiscordId());


    }

    protected abstract void doSomethingWithArtist(NowPlayingArtist artist, MessageReceivedEvent e, long discordId);
}
