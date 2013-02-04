package controllers;

import com.openseedbox.Config;
import com.openseedbox.backend.ITorrentBackend;
import com.openseedbox.code.MessageException;
import com.openseedbox.code.Util;
import com.openseedbox.jobs.GenericJob;
import com.openseedbox.jobs.GenericJobResult;
import com.openseedbox.jobs.torrent.AddTorrentJob;
import com.openseedbox.jobs.torrent.RemoveTorrentJob;
import com.openseedbox.jobs.torrent.StartStopTorrentJob;
import com.openseedbox.plugins.OpenseedboxPlugin;
import com.openseedbox.plugins.OpenseedboxPlugin.PluginSearchResult;
import com.openseedbox.plugins.PluginManager;
import java.io.File;
import java.io.IOException;
import java.util.*;
import com.openseedbox.models.Torrent;
import com.openseedbox.models.TorrentEvent;
import com.openseedbox.models.TorrentEvent.TorrentEventType;
import com.openseedbox.models.User;
import com.openseedbox.models.UserTorrent;
import org.apache.commons.lang.StringUtils;
import play.Logger;
import play.cache.Cache;
import play.data.binding.As;
import play.libs.F.Promise;
import play.libs.WS;
import play.mvc.Before;

public class Client extends Base {
	
	@Before(unless={"newUser"})
	public static void checkPlan() {
		User u = getCurrentUser();
		//check that a plan has been purchased		
		if (u.getPlan() == null) {
			newUser();
		}
	}
	
	@Before(unless={"login","auth"})
	public static void before() {	
		User u = getCurrentUser();
		if (u == null) {
			Auth.login();
		}

		//check that limits have not been exceeded. if they have, pause all the torrents and notify user
		/*
		try {
			if (u.hasExceededLimits()) {
				List<Torrent> running = u.getRunningTorrents();
				List<String> hashes = new ArrayList<String>();
				for (Torrent t : running) {
					hashes.add(t.getHashString());
				}
				//Note: cant use async/continuations in @Before method, potential bottleneck
				TorrentControlJobResult res = 
						new TorrentControlJob(getActiveAccount(), hashes, TorrentAction.STOP).doJobWithResult();
				if (res.hasError()) {
					addGeneralError(res.error);
				}
				u.notifyLimitsExceeded();
			} else {
				u.removeLimitsExceeded();
			}
		} catch (Exception ex) {
			addGeneralError(ex);
		}	*/	
	}
	
	public static void index(String group) {		
		if (StringUtils.isEmpty(group)) {
			group = "Ungrouped";
		}
		renderArgs.put("currentGroup", group);		
		renderArgs.put("users", Util.toSelectItems(User.all().fetch(), "id", "emailAddress"));
		User user = getCurrentUser();
		List<UserTorrent> torrents = user.getTorrentsInGroup(group);
		List<String> groups = user.getGroups();		
		String torrentList = renderTorrentList(group);
		List<OpenseedboxPlugin> searchPlugins = PluginManager.getSearchPlugins();				
		render("client/index.html", torrentList, groups, torrents, searchPlugins);
	}
	
	public static void update(String group) {
		//this is intended to be invoked via ajax		
		result(renderTorrentList(group));
	}
	
	private static String renderTorrentList(String group) {	
		if (StringUtils.isEmpty(group)) {
			group = "Ungrouped";
		}		
		List<UserTorrent> torrents = getCurrentUser().getTorrentsInGroup(group);
		List<TorrentEvent> torrentAddEvents = TorrentEvent.getIncompleteForUser(getCurrentUser(), TorrentEventType.ADDING);		
		List<TorrentEvent> torrentRemoveEvents = TorrentEvent.getIncompleteForUser(getCurrentUser(), TorrentEventType.REMOVING);
		return renderToString("client/torrent-list.html", Util.convertToMap(
				  new Object[] { "torrents", torrents, "torrentAddEvents", torrentAddEvents, "torrentRemoveEvents", torrentRemoveEvents }));
	}
	
	public static void addTorrent(@As("\n") final String[] urlOrMagnet, final File[] fileFromComputer) throws IOException {					
		if (urlOrMagnet == null && fileFromComputer == null) {
			setGeneralErrorMessage("Please enter a valid URL or magent link, or choose a valid file to upload.");
		} else {					
			int count = 0;
			User user = getCurrentUser();
			if (urlOrMagnet != null) {
				for (String s : urlOrMagnet) {
					new AddTorrentJob(s, null, user).now();
					count++;
				}
			}
			if (fileFromComputer != null) {
				for (File f : fileFromComputer) {
					new AddTorrentJob(null, f, user).now();
					count++;
				}
			}	
			if (count > 0) {
				if (count > 1) {
					setGeneralMessage(count + " torrents have been scheduled for downloading! They will begin shortly.");
				} else {
					setGeneralMessage("Your torrent has been scheduled for downloading! It will begin shortly.");
				}
			}
		}				
		index(null);
	}
	
	public static void search(String query, String providerClass) {
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		try {
			OpenseedboxPlugin provider = (OpenseedboxPlugin) Class.forName(providerClass).newInstance();
			if (provider.isSearchPlugin()) {
				List<PluginSearchResult> res = provider.doSearch(query);
				for (PluginSearchResult psr : res) {
					ret.add(Util.convertToMap(new Object[] {
						"label", String.format("%s", psr.getTorrentName()),
						"url", psr.getTorrentUrl()
					}));
				}
			}
		} catch (ClassNotFoundException ex) {
			resultError("Unable to find class: " + providerClass);
		} catch (InstantiationException ex) {
			Logger.error(ex, "Unable to instantiate class: %s", providerClass);
		} catch (IllegalAccessException ex) {
			Logger.error(ex, "Unable to instantiate class: %s", providerClass);
		}
		renderJSON(ret);
	}	
	
	public static void torrentInfo(String hash) {		
		//torrent info is seeders, peers, files, tracker stats
		final UserTorrent fromDb = UserTorrent.getByUser(getCurrentUser(), hash);
		if (fromDb == null) {
			resultError("No such torrent for user: " + hash);
		}
		Promise<GenericJobResult> p = new GenericJob() {

			@Override
			public Object doGenericJob() {
				//trigger the caching of these objects, inside a job because the WS
				//calls could take ages
				fromDb.getTorrent().getPeers();
				fromDb.getTorrent().getTrackers();
				fromDb.getTorrent().getFiles();
				return fromDb;
			}
			
		}.now();
		GenericJobResult res = await(p);
		if (res.hasError()) {
			resultError(res.getError().getMessage());
		}
		UserTorrent torrent = (UserTorrent) res.getResult();
		renderTemplate("client/torrent-info.html", torrent);
	}	
	
	public static void torrentDownload(String hash) {
		//torrent download is just for files
		final UserTorrent fromDb = UserTorrent.getByUser(getCurrentUser(), hash);
		if (fromDb == null) {
			resultError("No such torrent for user: " + hash);
		}
		Promise<GenericJobResult> p = new GenericJob() {
			@Override
			public Object doGenericJob() {
				fromDb.getTorrent().getFiles();
				return fromDb;
			}	
		}.now();
		GenericJobResult res = await(p);
		if (res.hasError()) {
			resultError(res.getError().getMessage());
		}
		UserTorrent torrent = (UserTorrent) res.getResult();
		renderTemplate("client/torrent-download.html", torrent);		
	}
	
	public static void addGroup(String group) {
		if (!StringUtils.isEmpty(group)) {
			User user = getCurrentUser();
			List<String> groups = user.getGroups();
			if (group.length() > 12) {
				group = group.substring(0, 12);
			}
			groups.add(group);
			user.setGroups(groups);
			user.save();
		} else {
			setGeneralErrorMessage("Please enter a group name.");
		}
		index(null);
	}
	
	public static void removeGroup(String group) {
		if (!StringUtils.isEmpty(group)) {
			User user = getCurrentUser();
			user.getGroups().remove(group);
			user.save();
			UserTorrent.blankOutGroup(user, group);
		}
		index(null);
	}
	
	public static void addToGroup(@As(",") List<String> hashes, String group, String new_group) {
		User user = getCurrentUser();
		List<UserTorrent> uts = UserTorrent.getByUser(getCurrentUser(), hashes);
		if (!StringUtils.isEmpty(new_group)) {
			if (new_group.length() > 12) {
				new_group = new_group.substring(0, 12);
			}
			if (!user.getGroups().contains(new_group)) {
				user.getGroups().add(new_group);
				user.save();
			}
		}
		String groupName = (!StringUtils.isEmpty(new_group)) ? new_group : group;
		for (UserTorrent ut : uts) {
			if (groupName.equals("Ungrouped")) {
				ut.setGroupName(null);
			} else {
				ut.setGroupName(groupName);
			}
		}
		UserTorrent.batch().update(uts);
		index(groupName);
	}
	
	public static void removeFromGroup(String group) {
		UserTorrent.blankOutGroup(getCurrentUser(), group);
		index(null);
	}
	
	public static void action(String what, String hash, @As(",") List<String> hashes, String group) {
		if (!StringUtils.isEmpty(hash)) { hashes = new ArrayList<String>(); }
		if (hashes.isEmpty()) {
			if (StringUtils.isEmpty(hash)) {
				setGeneralErrorMessage("Please specify a 'hash' or 'hashes'");
			}
			hashes.add(hash);		
		}		
		if (!hashes.isEmpty() && !StringUtils.isEmpty(what)) {
			if (what.equals("start")) {
				doTorrentAction(hashes, TorrentAction.START);
			} else if (what.equals("stop")) {
				doTorrentAction(hashes, TorrentAction.STOP);
			} else if (what.equals("remove")) {
				doTorrentAction(hashes, TorrentAction.REMOVE);
			}
		} else {
			setGeneralErrorMessage("Please specify an 'action'");
		}			
		index(group);
	}
	
	public enum TorrentAction { START, STOP, REMOVE }
	private static void doTorrentAction(List<String> hashes, TorrentAction action) {
		User user = getCurrentUser();						
		if (action == TorrentAction.REMOVE) {
			for (String h : hashes) {
				if (StringUtils.isEmpty(h)) {
					continue;
				}
				new RemoveTorrentJob(h, user).now();
			}
			if (hashes.size() > 1) {
				setGeneralMessage(hashes.size() + " torrents are now scheduled for deletion.");
			} else {
				setGeneralMessage("This torrent is now scheduled for deletion.");
			}			
		} else {
			successOrError(new StartStopTorrentJob(hashes, action, user).now());						
		}		
	}
	
	protected static Object successOrError(Promise<GenericJobResult> p) {
		if (p == null) { throw new IllegalArgumentException("You cant give me a null promise!"); }
		GenericJobResult res = await(p);
		if (res == null) { return null; }
		if (res.hasError()) {
			if (res.getError() instanceof MessageException) {
				setGeneralErrorMessage(res.getError().getMessage());
				return null;
			}
			if (StringUtils.contains(res.getError().getMessage(), "Connection refused")) {
				setGeneralErrorMessage("Unable to connect to backend! The administrators have been notified.");
				//TODO: send error email
				return null;
			}
			Logger.info(res.getError(), "Error occured in job.");
			throw new RuntimeException(res.getError());
		}
		return res.getResult();
	}
	
	public static void newUser() {
		render("client/new-user.html");
	}
	
	public static void switchUser(long user_id) {
		User u = getCurrentUser();
		if (!u.isAdmin()) {
			resultError("You have to be admin to do this!");
		}
		session.put("currentUserId", user_id);
		Cache.clear();
		index(null);
	}
	
	public static void downloadMultiple(@As(",") List<String> hashes) {
		if (!Config.isZipEnabled()) {
			notFound("Zip has been disabled.");
		}
		//Since the torrents are spread out over multiple nodes, we cant zip up multiple torrents into a single zip at the node level
		//Basically, we send HEAD requests to get the Content-Length, and then point nginx's mod_zip to an internal route that makes nginx fetch the torrent zip files from the upstream server
		if (hashes == null || hashes.isEmpty()) {
			notFound("Please specify some hashes.");
		}
		String all = "";
		for (Torrent t : Torrent.getByHash(hashes)) {
			String link = t.getZipDownloadLink();			
			String len = WS.url(link).head().getHeader("Content-Length");
			//strip scheme and replace localhost with 127.0.0.1 so nginx resolver doesnt time out
			link = link.replace("http://", "").replace("https://", "").replace("localhost", "127.0.0.1");
			//no CRC32 because theres no way of knowing the CRC32 of the upstream zipfiles without downloading them first
			all += String.format("- %s %s/%s /%s\n", len, Config.getZipPath(), link, t.getName() + ".zip");
		}
		if (!Config.isZipManifestOnly()) {
			response.setHeader("X-Archive-Files", "zip"); //tell NGINX to create us a zip
			response.setHeader("Content-Disposition", "attachment; filename=\"" + UUID.randomUUID().toString() + ".zip" + "\"");
		}
		response.setHeader("Last-Modified", Util.getLastModifiedHeader(System.currentTimeMillis()));
		renderText(all);
	}
}
