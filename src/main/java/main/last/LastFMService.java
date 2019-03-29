package main.last;


import DAO.ArtistData;

import java.util.LinkedList;
import java.util.List;

public interface LastFMService {

	List<UserInfo> getUserInfo(List<String> lastFmNames);

	LinkedList<ArtistData> getSimiliraties(String User);

	byte[] getUserList(String username, String time, int x, int y);
}
