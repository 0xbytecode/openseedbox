package models;

import code.WebRequest;
import code.WebResponse;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.Node.UserStats;
import play.Logger;
import play.data.validation.Required;
import play.modules.siena.EnhancedModel;
import siena.Column;
import siena.Generator;
import siena.Id;
import siena.Table;

@Table("node")
public class Node extends EnhancedModel {
	
	@Id(Generator.AUTO_INCREMENT)
	public Long id;

	@Required
	@Column("name")
	public String name;
	
	@Required
	@Column("ip_address")
	public String ipAddress;
	
	@Required
	@Column("username")
	public String username;
	
	@Required
	@Column("password")
	public String password;
	
	@Column("active")
	public boolean active;
	
	private transient UserStats _userStats;
	private UserStats getUserStats() {
		if (_userStats == null) {
			try {
				WebResponse res = new WebRequest(this).getResponse("status");
				_userStats = new Gson().fromJson(res.getResultJsonObject().getAsJsonObject("stats"), UserStats.class);
			} catch (WebRequest.WebRequestFailedException ex) {
				Logger.error("WebRequest failed: %s", ex);
				_userStats = null;
			}
		}
		return _userStats;
	}

	public String getUptime() {
		UserStats stats = getUserStats();
		if (stats != null) {
			String uptime = stats.uptime;
			return uptime.replace("users,", "users,<br />");
		}
		return null;
	}
	
	public String getFreeSpace() {
		UserStats stats = getUserStats();
		if (stats != null) {
			return "" + stats.freeSpaceGb + "gb";
		}
		return null;
	}
	
	public String getUsedSpace() {
		UserStats s = getUserStats();
		if (s != null) {
			return "" + (s.totalSpaceGb - s.freeSpaceGb) + "gb";
		}
		return null;
	}
	
	public Map<Plan, Integer> getFreeSlots() {
		Map<Plan, Integer> ret = new HashMap<Plan, Integer>();
		List<FreeSlot> fs = FreeSlot.all().filter("node", this).fetch();
		for (FreeSlot f : fs) {
			ret.put(f.plan, f.freeSlots);
		}
		return ret;
	}
	
	public int getUserCount() {
		UserStats s = getUserStats();
		if (s != null) {
			return s.numberOfUsers;
		}
		return -1;
	}
	
	public boolean isReachable() {
		UserStats s = getUserStats();
		return (s != null);
	}
	
	public class UserStats {
		
		@SerializedName("free-space-gb")
		public double freeSpaceGb;
		
		@SerializedName("total-space-gb")
		public double totalSpaceGb;
		
		@SerializedName("storage-directory-is-writable")
		public boolean storageDirectoryIsWritable;
		
		@SerializedName("apache-user")
		public String apacheUser;
		
		@SerializedName("uptime")
		public String uptime;
		
		@SerializedName("number-of-users")
		public int numberOfUsers;
		
	}

}
