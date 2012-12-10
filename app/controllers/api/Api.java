package controllers.api;

import com.openseedbox.mvc.controllers.Base;
import models.User;
import play.cache.Cache;
import play.mvc.Before;

public class Api extends Base {
	
	@Before
	protected static void ApiControllerBefore() {
		String apiKey = getApiKey();
		if (apiKey == null) {
			resultError("Please specify an API key.");
		}
		User apiUser = getApiUser();
		if (apiUser == null) {
			resultError("No such user associated with API key: " + apiKey);
		}
		//set default ext to json if not set
		if (request.params.get("ext") == null) {
			request.params.put("ext", "json");
		}
	}
	
	protected static String getApiKey() {
		return request.params.get("api_key");
	}
	
	protected static User getApiUser() {
		String key = getApiKey();
		if (key != null) {
			User u = Cache.get(getApiUserCacheKey(), User.class);
			if (u == null) {
				u = User.getByApiKey(key);
				Cache.set(getApiUserCacheKey(), u, "10mn");
			}
			return u;
		}
		return null;
	}
	
	private static String getApiUserCacheKey() {
		return session.getId() + getApiKey();
	}

}
