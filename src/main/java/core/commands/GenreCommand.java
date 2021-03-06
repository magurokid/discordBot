package core.commands;

import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.imagerenderer.GraphicUtils;
import core.imagerenderer.util.IPieableMap;
import core.otherlisteners.Reactionary;
import core.parsers.NumberParser;
import core.parsers.OptionalEntity;
import core.parsers.Parser;
import core.parsers.TimerFrameParser;
import core.parsers.params.NumberParameters;
import core.parsers.params.TimeFrameParameters;
import dao.ChuuService;
import dao.entities.*;
import dao.musicbrainz.MusicBrainzService;
import dao.musicbrainz.MusicBrainzServiceSingleton;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.text.WordUtils;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieSeries;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static core.parsers.ExtraParser.LIMIT_ERROR;

public class GenreCommand extends ConcurrentCommand<NumberParameters<TimeFrameParameters>> {
    private final MusicBrainzService musicBrainz;


    private final IPieableMap<Genre, Integer, NumberParameters<TimeFrameParameters>> pieable;

    public GenreCommand(ChuuService dao) {
        super(dao);
        this.musicBrainz = MusicBrainzServiceSingleton.getInstance();
        pieable = (chart, params, data) -> {
            data.entrySet().stream().sorted(((o1, o2) -> o2.getValue().compareTo(o1.getValue()))).sequential().limit(params.getExtraParam())
                    .forEach(entry -> {


                        Genre genre = entry.getKey();
                        int plays = entry.getValue();
                        chart.addSeries(genre.getGenreName() + "\u200B", plays);
                    });
            return chart;

        };
    }

    @Override
    protected CommandCategory getCategory() {
        return CommandCategory.USER_STATS;
    }

    @Override
    public Parser<NumberParameters<TimeFrameParameters>> getParser() {
        Map<Integer, String> map = new HashMap<>(2);
        map.put(LIMIT_ERROR, "The number introduced must be between 1 and a big number");
        String s = "You can also introduce a number to vary the number of genres shown in the pie," +
                "defaults to 10";

        TimerFrameParser timerFrameParser = new TimerFrameParser(getService(), TimeFrameEnum.YEAR);
        timerFrameParser.addOptional(new OptionalEntity("--artist", "use artists instead of albums for the genres"));
        timerFrameParser.addOptional(new OptionalEntity("--list", "display in list format"));


        return new NumberParser<>(timerFrameParser,
                10L,
                Integer.MAX_VALUE,
                map, s, false, true, false);
    }

    @Override
    public String getDescription() {
        return "Top 10 genres from an user";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("genre");
    }

    @Override
    public String getName() {
        return "Top Genres";
    }

    @Override
    protected void onCommand(MessageReceivedEvent e) throws LastFmException, InstanceNotFoundException {
        NumberParameters<TimeFrameParameters> parse = parser.parse(e);
        if (parse == null) {
            return;
        }
        TimeFrameParameters returned = parse.getInnerParams();
        String username = returned.getLastFMData().getName();
        long discordId = returned.getLastFMData().getDiscordId();

        TimeFrameEnum timeframe = returned.getTime();
        DiscordUserDisplay userInfo = CommandUtil.getUserInfoNotStripped(e, discordId);
        String usableString = userInfo.getUsername();
        String urlImage = userInfo.getUrlImage();
        Map<Genre, Integer> map;
        boolean doArtits = parse.hasOptional("--artist");
        if (doArtits) {
            List<ArtistInfo> albumInfos = lastFM.getTopArtists(username, timeframe.toApiFormat(), 3000).stream().filter(u -> u.getMbid() != null && !u.getMbid().isEmpty())
                    .collect(Collectors.toList());
            if (albumInfos.isEmpty()) {
                sendMessageQueue(e, "Was not able to find any genre in " + usableString + "'s top 3000 artists" + returned.getTime().getDisplayString() + " on Musicbrainz");
                return;
            }
            map = musicBrainz.genreCountByartist(albumInfos);
        } else {
            List<AlbumInfo> albumInfos = lastFM.getTopAlbums(username, timeframe.toApiFormat(), 3000).stream().filter(u -> u.getMbid() != null && !u.getMbid().isEmpty())
                    .collect(Collectors.toList());
            if (albumInfos.isEmpty()) {
                sendMessageQueue(e, "Was not able to find any genre in " + usableString + "'s top 3000 albums" + returned.getTime().getDisplayString() + " on Musicbrainz");
                return;
            }
            map = musicBrainz.genreCount(albumInfos);
        }
        if (map.isEmpty()) {
            sendMessageQueue(e, "Was not able to find any genre in " + usableString + "'s top 3000 " + (doArtits ? "artists" : "albums") + returned.getTime().getDisplayString() + " on Musicbrainz");
            return;
        }

        if (parse.hasOptional("--list")) {
            List<String> collect = map.entrySet()
                    .stream().sorted(((o1, o2) -> o2.getValue().compareTo(o1.getValue()))).map(x -> ". **" + WordUtils.capitalizeFully(CommandUtil.cleanMarkdownCharacter(x.getKey().getGenreName())) + "** - " + x.getValue() + "\n").collect(Collectors.toList());
            if (collect.isEmpty()) {
                sendMessageQueue(e, "Was not able to find any genre in " + usableString + "'s top 3000 " + (doArtits ? "artists" : "albums") + returned.getTime().getDisplayString() + " on Musicbrainz");
                return;
            }

            StringBuilder a = new StringBuilder();
            for (int i = 0; i < 10 && i < collect.size(); i++) {
                a.append(i + 1).append(collect.get(i));
            }
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setDescription(a)
                    .setColor(CommandUtil.randomColor())
                    .setTitle(usableString + "'s genres")
                    .setFooter(usableString + " has " + collect.size() + " found genres" + timeframe.getDisplayString(), null)
                    .setThumbnail(urlImage);
            MessageBuilder mes = new MessageBuilder();
            e.getChannel().sendMessage(mes.setEmbed(embedBuilder.build()).build()).queue(message1 ->
                    new Reactionary<>(collect, message1, embedBuilder));


        } else {
            Long extraParam = parse.getExtraParam();
            PieChart pieChart = pieable.doPie(parse, map);
            pieChart.setTitle("Top " + extraParam + " Genres from " + usableString + timeframe.getDisplayString());
            BufferedImage bufferedImage = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bufferedImage.createGraphics();
            GraphicUtils.setQuality(g);
            pieChart.paint(g, 1000, 750);
            GraphicUtils.inserArtistImage(urlImage, g);
            sendImage(bufferedImage, e);
        }
    }


}
