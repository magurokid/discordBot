package core.commands;

import core.exceptions.InstanceNotFoundException;
import core.exceptions.LastFmException;
import core.parsers.NoOpParser;
import core.parsers.Parser;
import core.parsers.params.CommandParameters;
import dao.ChuuService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class InviteCommand extends ConcurrentCommand<CommandParameters> {
    private static final long PERMISSIONS = 387136;

    @Override
    protected CommandCategory getCategory() {
        return CommandCategory.BOT_INFO;
    }

    public InviteCommand(ChuuService dao) {
        super(dao);
    }

    @Override
    public Parser<CommandParameters> getParser() {
        return new NoOpParser();
    }

    @Override
    public String getDescription() {
        return "Invite the bot to other servers!";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("invite");
    }

    @Override
    public String getName() {
        return "Invite";
    }

    @Override
    void onCommand(MessageReceivedEvent e) throws LastFmException, InstanceNotFoundException {
        EnumSet<Permission> permissions = Permission.getPermissions(PERMISSIONS);
        String inviteUrl = e.getJDA().getInviteUrl(permissions);
        sendMessageQueue(e, "Using the following link you can invite me to your server:\n" + inviteUrl);
    }
}
