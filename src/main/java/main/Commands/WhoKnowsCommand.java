package main.Commands;

import DAO.DaoImplementation;
import DAO.Entities.ArtistData;
import DAO.Entities.ReturnNowPlaying;
import DAO.Entities.WrapperReturnNowPlaying;
import main.APIs.Discogs.DiscogsApi;
import main.APIs.Discogs.DiscogsSingleton;
import main.APIs.Spotify.Spotify;
import main.APIs.Spotify.SpotifySingleton;
import main.ImageRenderer.WhoKnowsMaker;
import main.Parsers.WhoKnowsParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;


public class WhoKnowsCommand extends ConcurrentCommand {
	private final DiscogsApi discogsApi;
	private final Spotify spotify;


	public WhoKnowsCommand(DaoImplementation dao) {
		super(dao);
		this.discogsApi = DiscogsSingleton.getInstanceUsingDoubleLocking();
		this.spotify = SpotifySingleton.getInstanceUsingDoubleLocking();
		this.parser = new WhoKnowsParser();
		this.respondInPrivate = false;

	}

	@Override
	public void threadableCode(MessageReceivedEvent e) {
		String[] returned;
		returned = parser.parse(e);
		if (returned == null)
			return;
		ArtistData validable = new ArtistData(returned[0], 0, "");
		CommandUtil.lessHeavyValidate(getDao(),validable,lastFM,discogsApi,spotify);
		whoKnowsLogic(validable, Boolean.valueOf(returned[1]), e);

	}

	void whoKnowsLogic(ArtistData who, Boolean isImage, MessageReceivedEvent e) {
		MessageBuilder messageBuilder = new MessageBuilder();
		EmbedBuilder embedBuilder = new EmbedBuilder();

		WrapperReturnNowPlaying wrapperReturnNowPlaying = this.getDao()
				.whoKnows(who.getArtist(), e.getGuild().getIdLong());
		if (wrapperReturnNowPlaying.getRows() == 0) {
			messageBuilder.setContent("No nibba knows " + who.getArtist()).sendTo(e.getChannel()).queue();
			return;
		}
		wrapperReturnNowPlaying.setUrl(who.getUrl());

		if (!isImage) {
			StringBuilder builder = new StringBuilder();
			int counter = 1;
			for (ReturnNowPlaying returnNowPlaying : wrapperReturnNowPlaying.getReturnNowPlayings()) {

				String userName = getUserString(returnNowPlaying.getDiscordId(), e, returnNowPlaying.getLastFMId());
				builder.append(counter++)
						.append(". ")
						.append("[").append(userName).append("]")
						.append("(https://www.last.fm/user/").append(returnNowPlaying.getLastFMId())
						.append("/library/music/").append(wrapperReturnNowPlaying.getArtist().replaceAll(" ", "+").replaceAll("[)]", "%29")).append(") - ")
						.append(returnNowPlaying.getPlayNumber()).append(" plays\n");
			}

			embedBuilder.setTitle("Who knows " + who.getArtist() + " in " + e.getGuild().getName() + "?").
					setThumbnail(CommandUtil.noImageUrl(wrapperReturnNowPlaying.getUrl())).setDescription(builder)
					.setColor(CommandUtil.randomColor());
			//.setFooter("Command invoked by " + event.getMember().getLastFmId().getDiscriminator() + "" + LocalDateTime.now().format(DateTimeFormatter.ISO_WEEK_DATE).toApiFormat(), );
			messageBuilder.setEmbed(embedBuilder.build()).sendTo(e.getChannel()).submit();
			return;
		}

		wrapperReturnNowPlaying.getReturnNowPlayings().forEach(element ->
				element.setDiscordName(getUserString(element.getDiscordId(), e, element.getLastFMId()))
		);
		BufferedImage logo = CommandUtil.getLogo(getDao(), e);
		BufferedImage image = WhoKnowsMaker.generateWhoKnows(wrapperReturnNowPlaying, e.getGuild().getName(), logo);
		sendImage(image, e);
	}

	@Override
	public List<String> getAliases() {
		return Arrays.asList("!whoknows", "!wk");
	}

	@Override
	public String getDescription() {
		return "Returns List Of Users Who Know the inputted Artist";
	}

	@Override
	public String getName() {
		return "Who Knows";
	}


}
