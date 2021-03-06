package core.commands;

import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.otherlisteners.Reactionary;
import core.parsers.NumberParser;
import core.parsers.OnlyUsernameParser;
import core.parsers.Parser;
import core.parsers.params.ChuuDataParams;
import core.parsers.params.NumberParameters;
import dao.ChuuService;
import dao.entities.Affinity;
import dao.entities.DiscordUserDisplay;
import dao.entities.LastFMData;
import dao.entities.PrivacyMode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static core.parsers.ExtraParser.LIMIT_ERROR;

public class GlobalAffinity extends ConcurrentCommand<NumberParameters<ChuuDataParams>> {

    public GlobalAffinity(ChuuService dao) {
        super(dao);
        this.respondInPrivate = false;
    }

    @Override
    protected CommandCategory getCategory() {
        return CommandCategory.DISCOVERY;
    }

    @Override
    public Parser<NumberParameters<ChuuDataParams>> getParser() {

        Map<Integer, String> map = new HashMap<>(2);
        map.put(LIMIT_ERROR, "The number introduced must be positive and not very big");
        String s = "You can also introduce a number to vary the number of plays needed to award a match, " +
                "defaults to 30";
        return new NumberParser<>(new OnlyUsernameParser(getService()),
                30L,
                Integer.MAX_VALUE,
                map, s, false, true);
    }

    @Override
    public String getDescription() {
        return "Gets your affinity with the rest of the bot users that have opened up their privacy settings";
    }

    @Override
    public List<String> getAliases() {
        return List.of("globalaffinity", "gaff", "globalsoulmate");
    }

    @Override
    public String getName() {
        return "Global Affinity";
    }

    @Override
    void onCommand(MessageReceivedEvent e) throws LastFmException, InstanceNotFoundException {
        NumberParameters<ChuuDataParams> ap = parser.parse(e);

        LastFMData ogData = getService().findLastFMData(e.getAuthor().getIdLong());
        int threshold = Math.toIntExact(ap.getExtraParam());
        List<dao.entities.GlobalAffinity> serverAffinity = getService().getGlobalAffinity(ogData.getName(), e.isFromGuild() ? e.getGuild().getIdLong() : null, threshold);
        List<dao.entities.GlobalAffinity> collect = serverAffinity.stream().sorted(Comparator.comparing(Affinity::getAffinity).reversed()).collect(Collectors.toList());

        StringBuilder stringBuilder = new StringBuilder();
        List<String> string = collect.stream().map(x -> {
                    String name;
                    if (x.getPrivacyMode() == PrivacyMode.TAG) {
                        name = e.getJDA().retrieveUserById(x.getDiscordId()).complete().getAsTag();
                    } else if (x.getPrivacyMode() == PrivacyMode.LAST_NAME) {
                        name = x.getReceivingLastFmId();
                    } else {
                        name = getUserString(e, x.getDiscordId());
                    }

                    return String.format(". [%s](%s) - %.2f%%%s matching%n", name,
                            CommandUtil.getLastFmUser(x.getReceivingLastFmId()),
                            (x.getAffinity() > 1 ? 1 : x.getAffinity()) * 100, x.getAffinity() > 1 ? "+" : "");
                }
        ).collect(Collectors.toList());
        for (
                int i = 0, size = collect.size();
                i < 10 && i < size; i++) {
            String text = string.get(i);
            stringBuilder.append(i + 1).append(text);
        }

        DiscordUserDisplay uinfo = CommandUtil.getUserInfoConsideringGuildOrNot(e, e.getAuthor().getIdLong());
        String name = e.getJDA().getSelfUser().getName();
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setDescription(stringBuilder)
                .setTitle(uinfo.getUsername() + "'s soulmates in " + CommandUtil.cleanMarkdownCharacter(name))
                .setColor(CommandUtil.randomColor())
                .setFooter(String.format("%s's global affinity using a threshold of %d plays!%n", CommandUtil.markdownLessString(uinfo.getUsername()), threshold), null)
                .setThumbnail(e.getJDA().getSelfUser().getAvatarUrl());
        MessageBuilder mes = new MessageBuilder();
        e.getChannel().

                sendMessage(mes.setEmbed(embedBuilder.build()).

                        build()).

                queue(message1 ->
                        new Reactionary<>(string, message1, embedBuilder));
    }

}
