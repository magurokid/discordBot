package main.commands;

import dao.DaoImplementation;
import dao.entities.AlbumUserPlays;
import dao.entities.ArtistAlbums;
import dao.entities.ArtistData;
import dao.entities.WrapperReturnNowPlaying;
import main.Chuu;
import main.exceptions.LastFmEntityNotFoundException;
import main.exceptions.LastFmException;
import main.imagerenderer.BandRendered;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.management.InstanceNotFoundException;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BandInfoCommand extends WhoKnowsCommand {

	public BandInfoCommand(DaoImplementation dao) {
		super(dao);
	}

	@Override
	public List<String> getAliases() {
		return Arrays.asList("artist", "a");
	}

	@Override
	void whoKnowsLogic(ArtistData who, Boolean isList, MessageReceivedEvent e, long userId) {
		ArtistAlbums ai;
		String lastFmName;
		try {
			lastFmName = getDao().findLastFMData(userId).getName();
		} catch (InstanceNotFoundException ex) {
			sendMessageQueue(e, "Error f");
			return;
		}

		try {
			ai = lastFM.getAlbumsFromArtist(who.getArtist(), 14);
		} catch (LastFmEntityNotFoundException ex) {
			//parser.sendError(parser.getErrorMessage(2), e);
			parser.sendError(who.getArtist() + " doesn't exist on Last.fm", e);
			return;
		} catch (LastFmException ex) {
			parser.sendError(parser.getErrorMessage(2), e);
			return;
		}

		String artist = ai.getArtist();
		final String username = lastFmName;
		List<AlbumUserPlays> list = ai.getAlbumList();

		int plays = getDao().getArtistPlays(artist, username);
		if (plays == 0) {
			parser.sendError("You still haven't listened  to " + artist, e);
			return;
		}

		list =
				list.stream().peek(albumInfo -> {
					try {
						albumInfo.setPlays(lastFM.getPlaysAlbum_Artist(username, artist, albumInfo.getAlbum())
								.getPlays());

					} catch (LastFmException ex) {
						Chuu.getLogger().warn(ex.getMessage(), ex);
					}
				})
						.filter(a -> a.getPlays() > 0)
						.collect(Collectors.toList());

		list.sort(Comparator.comparing(AlbumUserPlays::getPlays).reversed());
		ai.setAlbumList(list);
		WrapperReturnNowPlaying np = getDao().whoKnows(artist, e.getGuild().getIdLong(), 5);
		np.getReturnNowPlayings().forEach(element ->
				element.setDiscordName(getUserString(element.getDiscordId(), e, element.getLastFMId()))
		);

		BufferedImage logo = CommandUtil.getLogo(getDao(), e);
		BufferedImage returnedImage = BandRendered
				.makeBandImage(np, ai, plays, logo, getUserString(userId, e, username));
		sendImage(returnedImage, e);
	}

	@Override
	public String getDescription() {
		return "Band info";
	}

	@Override
	public String getName() {
		return "Band";
	}


}