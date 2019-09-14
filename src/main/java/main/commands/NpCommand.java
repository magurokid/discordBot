package main.commands;

import dao.DaoImplementation;
import dao.entities.NowPlayingArtist;
import main.exceptions.LastFMNoPlaysException;
import main.exceptions.LastFmEntityNotFoundException;
import main.exceptions.LastFmException;
import main.parsers.NpParser;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public abstract class NpCommand extends ConcurrentCommand {


	NpCommand(DaoImplementation dao) {
		super(dao);
		this.parser = new NpParser(getDao());

	}

	@Override
	public void onCommand(MessageReceivedEvent e) {

		String[] returned = parser.parse(e);
		if (returned == null) {
			return;
		}
		String username = returned[0];
		try {
			NowPlayingArtist nowPlayingArtist = lastFM.getNowPlayingInfo(username);
			doSomethingWithArtist(nowPlayingArtist, e);


		} catch (
				LastFMNoPlaysException e1) {
			parser.sendError(parser.getErrorMessage(3), e);
		} catch (LastFmEntityNotFoundException e2) {
			parser.sendError(parser.getErrorMessage(4), e);
		} catch (
				LastFmException ex) {
			parser.sendError(parser.getErrorMessage(2), e);
		}

	}

	protected abstract void doSomethingWithArtist(NowPlayingArtist artist, MessageReceivedEvent e);
}