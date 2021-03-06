package core.commands;

import core.apis.last.TopEntity;
import core.apis.last.chartentities.AlbumChart;
import core.apis.last.chartentities.ChartUtil;
import core.apis.last.chartentities.TrackDurationAlbumArtistChart;
import core.exceptions.LastFmException;
import core.parsers.ChartDecadeParser;
import core.parsers.ChartableParser;
import core.parsers.params.ChartYearRangeParameters;
import dao.ChuuService;
import dao.entities.*;
import dao.musicbrainz.MusicBrainzService;
import dao.musicbrainz.MusicBrainzServiceSingleton;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;
import org.knowm.xchart.PieChart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class AOTDCommand extends ChartableCommand<ChartYearRangeParameters> {
    private final MusicBrainzService mb;
    private final int searchSpace = 1500;

    public AOTDCommand(ChuuService dao) {
        super(dao);
        mb = MusicBrainzServiceSingleton.getInstance();

    }

    @Override
    public ChartableParser<ChartYearRangeParameters> getParser() {
        return new ChartDecadeParser(getService(), searchSpace);
    }

    @Override
    public String getDescription() {
        return "Like AOTY but for multiple years at the same time";
    }

    @Override
    public List<String> getAliases() {
        return List.of("aotd", "range");
    }

    @Override
    public String getName() {
        return "Album Of The Decade";
    }

    @Override
    public CountWrapper<BlockingQueue<UrlCapsule>> processQueue(ChartYearRangeParameters param) throws LastFmException {
        BlockingQueue<UrlCapsule> queue = new LinkedBlockingQueue<>();
        boolean isByTime = param.isByTime();

        BiFunction<JSONObject, Integer, UrlCapsule> parser;
        if (param.getTimeFrameEnum().equals(TimeFrameEnum.DAY)) {
            if (isByTime)
                parser = TrackDurationAlbumArtistChart.getDailyArtistAlbumDurationParser(param, lastFM.getTrackDurations(param.getLastfmID(), TimeFrameEnum.WEEK));
            else {
                parser = AlbumChart.getDailyAlbumParser(param);
            }
        } else {
            if (isByTime)
                parser = TrackDurationAlbumArtistChart.getTimedParser(param);
            else {
                parser = ChartUtil.getParser(param.getTimeFrameEnum(), TopEntity.ALBUM, param, lastFM, param.getLastfmID());
            }
        }

        lastFM.getChart(param.getLastfmID(), param.getTimeFrameEnum().toApiFormat(), this.searchSpace, 1, TopEntity.ALBUM, parser, queue);
        int baseYear = param.getBaseYear().getValue();
        //List of obtained elements
        Map<Boolean, List<AlbumInfo>> results =
                queue.stream()
                        .map(capsule ->
                                new AlbumInfo(capsule.getMbid(), capsule.getAlbumName(), capsule.getArtistName()))
                        .collect(Collectors.partitioningBy(albumInfo -> albumInfo.getMbid().isEmpty()));

        List<AlbumInfo> nonEmptyMbid = results.get(false);
        List<AlbumInfo> emptyMbid = results.get(true);


        List<AlbumInfo> albumsMbizMatchingYear;
        if (isByTime) {
            return handleTimedChart(param, nonEmptyMbid, emptyMbid, queue);
        }
        albumsMbizMatchingYear = mb.listOfYearRangeReleases(nonEmptyMbid, baseYear, param.getNumberOfYears());
        List<AlbumInfo> mbFoundBYName = mb.findArtistByReleaseRangeYear(emptyMbid, baseYear, param.getNumberOfYears());
        emptyMbid.removeAll(mbFoundBYName);


        //Keep the order of the original queue so the final chart is ordered by plays
        AtomicInteger counter2 = new AtomicInteger(0);
        queue.removeIf(urlCapsule -> {
            for (AlbumInfo albumInfo : albumsMbizMatchingYear) {
                if ((!albumInfo.getMbid().isEmpty() && albumInfo.getMbid().equals(urlCapsule.getMbid())) || urlCapsule
                        .getAlbumName().equalsIgnoreCase(albumInfo.getName()) && urlCapsule.getArtistName()
                        .equalsIgnoreCase(albumInfo.getArtist())) {
                    urlCapsule.setPos(counter2.getAndAdd(1));
                    return false;
                }
            }
            return true;
        });
        getService().updateMetrics(0, mbFoundBYName.size(), albumsMbizMatchingYear
                .size(), ((long) param.getX()) * param.getX());
        return new CountWrapper<>(albumsMbizMatchingYear.size(), queue);
    }


    @Override
    public EmbedBuilder configEmbed(EmbedBuilder embedBuilder, ChartYearRangeParameters params, int count) {
        return params.initEmbed("s top albums from " + params.getDisplayString(), embedBuilder, " has " + count + " albums from " + params.getDisplayString() + " in their top " + searchSpace + " albums", params.getLastfmID());
    }

    @Override
    public String configPieChart(PieChart pieChart, ChartYearRangeParameters params, int count, String initTitle) {
        String time = params.getTimeFrameEnum().getDisplayString();
        pieChart.setTitle(String.format("%ss top albums from the %s%s", initTitle, params.getDisplayString(), time));
        return String.format("%s has %d albums from %s in their top %d albums%s (showing top %d)", initTitle, count, params.getDisplayString(), searchSpace, time, params.getX() * params.getY());
    }

    @Override
    public void noElementsMessage(ChartYearRangeParameters parameters) {
        MessageReceivedEvent e = parameters.getE();
        DiscordUserDisplay ingo = CommandUtil.getUserInfoConsideringGuildOrNot(e, parameters.getDiscordId());
        sendMessageQueue(e, String.format("Couldn't find any %s album in %s top %d albums%s!", parameters.getDisplayString(), ingo.getUsername(), searchSpace, parameters.getTimeFrameEnum().getDisplayString()));
    }

    @Override
    public void doImage(BlockingQueue<UrlCapsule> queue, int x, int y, ChartYearRangeParameters parameters, int size) {
        if (!parameters.isCareAboutSized()) {
            int imageSize = Math.max((int) Math.ceil(Math.sqrt(queue.size())), 1);
            super.doImage(queue, imageSize, imageSize, parameters, size);
        } else {
            BlockingQueue<UrlCapsule> tempQueuenew = new LinkedBlockingDeque<>();
            queue.drainTo(tempQueuenew, x * y);
            super.doImage(tempQueuenew, x, y, parameters, size);
        }
    }


    private CountWrapper<BlockingQueue<UrlCapsule>> handleTimedChart(ChartYearRangeParameters parameters, List<AlbumInfo> nonEmptyMbid, List<AlbumInfo> emptyMbid, BlockingQueue<UrlCapsule> queue) {
        List<AlbumInfo> albumsMbizMatchingYear;
        int baseYear = parameters.getBaseYear().getValue();

        List<CountWrapper<AlbumInfo>> accum = mb.listOfRangeYearReleasesWithAverage(nonEmptyMbid, baseYear, parameters.getNumberOfYears());
        List<CountWrapper<AlbumInfo>> mbFoundBYName = mb.findArtistByReleaseWithAverageRangeYears(emptyMbid, baseYear, parameters.getNumberOfYears());
        emptyMbid.removeAll(mbFoundBYName.stream().map(CountWrapper::getResult).collect(Collectors.toList()));


        albumsMbizMatchingYear = accum.stream().map(CountWrapper::getResult).collect(Collectors.toList());
        accum.addAll(mbFoundBYName);
        albumsMbizMatchingYear.addAll(mbFoundBYName.stream().map(CountWrapper::getResult).collect(Collectors.toList()));

        List<UrlCapsule> b = new ArrayList<>();
        queue.drainTo(b);

        b.removeIf(urlCapsule -> {
            for (CountWrapper<AlbumInfo> t : accum) {
                AlbumInfo albumInfo = t.getResult();

                if ((!albumInfo.getMbid().isEmpty() && albumInfo.getMbid().equals(urlCapsule.getMbid())) || urlCapsule.getAlbumName().equalsIgnoreCase(albumInfo.getName()) && urlCapsule.getArtistName()
                        .equalsIgnoreCase(albumInfo.getArtist())) {
                    TrackDurationAlbumArtistChart urlCapsule1 = (TrackDurationAlbumArtistChart) urlCapsule;
                    urlCapsule1.setSeconds((t.getRows() / 1000) * urlCapsule.getPlays());
                    return false;
                }
            }
            return true;
        });
        AtomicInteger asdasdasd = new AtomicInteger(0);

        LinkedBlockingDeque<UrlCapsule> collect = b.stream().sorted(Comparator.comparing(x -> (
                ((TrackDurationAlbumArtistChart) x).getSeconds()
        )).reversed()).peek(x -> x.setPos(asdasdasd.getAndIncrement()))
                .collect(Collectors.toCollection(LinkedBlockingDeque::new));
        return new CountWrapper<>(albumsMbizMatchingYear.size(), collect);
    }
}
