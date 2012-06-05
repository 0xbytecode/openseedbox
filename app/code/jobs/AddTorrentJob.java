package code.jobs;

import code.MessageException;
import code.jobs.AddTorrentJob.AddTorrentJobResult;
import java.io.File;
import models.Torrent;
import models.User;
import play.jobs.Job;

public class AddTorrentJob extends Job<AddTorrentJobResult> {
	
	private User _user;
	private String _url;
	private File _file;
	
	public AddTorrentJob(User user, String urlOrMagnet, File torrentFile) {
		_user = user;
		_url = urlOrMagnet;
		_file = torrentFile;
	}

	@Override
	public AddTorrentJobResult doJobWithResult() {
		AddTorrentJobResult res = new AddTorrentJobResult();
		try {
			if (_file != null) {
				res.torrent = _user.addTorrent(_file);
			} else {
				res.torrent = _user.addTorrent(_url);
			}
		} catch (MessageException ex) {
			res.error = ex;
		}
		return res;
	}
	
	public class AddTorrentJobResult extends JobResult {
		
		public Torrent torrent;
		
	}
	
	
}
