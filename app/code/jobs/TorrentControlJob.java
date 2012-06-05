/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package code.jobs;

import code.MessageException;
import code.jobs.TorrentControlJob.TorrentControlJobResult;
import code.transmission.Transmission;
import java.util.List;
import models.Torrent;
import models.User;
import play.jobs.Job;

/**
 *
 * @author erin
 */
public class TorrentControlJob extends Job<TorrentControlJobResult> {
	
	private List<String> _hashes;
	private TorrentAction _action;
	private User _user;

	public TorrentControlJob(User user, List<String> hashes, TorrentAction ta) {
		this._hashes = hashes;
		this._action = ta;
		this._user = user;
	}
	
	@Override
	public TorrentControlJobResult doJobWithResult() throws Exception {
		Transmission t = _user.getTransmission();
		TorrentControlJobResult res = new TorrentControlJobResult();
		try {
			if (_action == TorrentAction.START) {
				res.success = t.startTorrent(_hashes);
			} else if (_action == TorrentAction.STOP) {
				res.success = t.pauseTorrent(_hashes);
			} else if (_action == TorrentAction.REMOVE) {
				res.success = t.removeTorrent(_hashes, false);
				if (res.success && _hashes.size() > 0) {
					//delete all the db entries for the torrents
					Torrent.all().filter("hashString IN", _hashes).filter("user", this).delete();				
				}
			}
		} catch (Exception ex) {
			res.error = ex;
		}
		return res;
	}
	
	public enum TorrentAction {
		START, STOP, REMOVE
	}
	
	public class TorrentControlJobResult extends JobResult {
		public boolean success;
	}
	
}
