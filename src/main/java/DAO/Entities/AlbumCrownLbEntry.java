package DAO.Entities;

import main.Commands.CommandUtil;

public class AlbumCrownLbEntry extends LbEntry {

	public AlbumCrownLbEntry(String user, long discordId, int entryCount) {
		super(user, discordId, entryCount);
	}

	@Override
	public String toString() {
		return ". [" +
				getDiscordName() +
				"](" + CommandUtil.getLastFmUser(this.getLastFmId()) +
				") - " + getEntryCount() +
				" album crowns\n";
	}

}