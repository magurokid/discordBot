package core.commands;

import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.otherlisteners.Reactionary;
import core.parsers.OnlyUsernameParser;
import core.parsers.Parser;
import core.parsers.params.ChuuDataParams;
import dao.ChuuService;
import dao.entities.AlbumPlays;
import dao.entities.AlbumUserPlays;
import dao.entities.DiscordUserDisplay;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class UnratedAlbums extends ListCommand<AlbumPlays, ChuuDataParams> {
    public UnratedAlbums(ChuuService dao) {
        super(dao);
    }

    @Override
    protected CommandCategory getCategory() {
        return CommandCategory.RYM_BETA;
    }

    @Override
    public Parser<ChuuDataParams> getParser() {
        return new OnlyUsernameParser(getService());
    }

    @Override
    public String getDescription() {
        return "List your top unrated rym albums";
    }

    @Override
    public List<String> getAliases() {
        return List.of("unrated");
    }

    @Override
    public String getName() {
        return "Unrated Albums";
    }


    @Override
    public List<AlbumPlays> getList(ChuuDataParams params) {
        return getService().getUnratedAlbums(params.getLastFMData().getDiscordId());
    }

    @Override
    public void printList(List<AlbumPlays> list, ChuuDataParams params) {
        Long discordId = params.getLastFMData().getDiscordId();
        MessageReceivedEvent e = params.getE();
        DiscordUserDisplay dp = CommandUtil.getUserInfoConsideringGuildOrNot(e, discordId);
        if (list.isEmpty()) {
            sendMessageQueue(e, "Couldn't find any unrated album in " + dp.getUsername() + " albums");
            return;
        }

        StringBuilder a = new StringBuilder();
        for (int i = 0; i < 10 && i < list.size(); i++) {
            a.append(i + 1).append(list.get(i));
        }

        char prefix = CommandUtil.getMessagePrefix(e);

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setDescription(a);
        embedBuilder.setColor(CommandUtil.randomColor());
        embedBuilder.setTitle(dp.getUsername() + "'s Unrated Albums");
        embedBuilder.setFooter("You can link your rym account using " + prefix + "rymimport\n You have "  + list.size() + " unrated albums", null);
        embedBuilder.setThumbnail(dp.getUrlImage());
        MessageBuilder mes = new MessageBuilder();
        e.getChannel().sendMessage(mes.setEmbed(embedBuilder.build()).build()).queue(message1 ->
                new Reactionary<>(list, message1, embedBuilder));
    }
}
