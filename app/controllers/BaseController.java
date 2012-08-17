package controllers;

import code.GenericResult;
import code.MessageException;
import com.googlecode.htmlcompressor.compressor.HtmlCompressor;
import com.googlecode.htmlcompressor.compressor.XmlCompressor;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.zip.GZIPOutputStream;
import models.Account;
import models.User;
import notifiers.Mails;
import org.apache.commons.lang.StringUtils;
import play.Logger;
import play.Play;
import play.Play.Mode;
import play.cache.Cache;
import play.data.validation.Validation;
import play.mvc.Before;
import play.mvc.Catch;
import play.mvc.Controller;
import play.mvc.Finally;
import play.mvc.Http.Header;
import play.mvc.Router;
import play.templates.Template;
import play.templates.TemplateLoader;

public class BaseController extends Controller {
	
	@Before
	protected static void before() {
		//Set logging level
		String level = Play.configuration.getProperty("application.log", "INFO");
		if (!level.equals("INFO")) {
			LogManager.getLogManager().reset();
		}
		
		//check if http request. if it is, make https
		/* Handled by NGINX now
		if (!request.secure) {
			String url = Router.reverse("MainController.index").secure().url;
			if (Play.mode == Mode.DEV) {
				String httpPort = Play.configuration.getProperty("http.port");
				String httpsPort = Play.configuration.getProperty("https.port");
				url = url.replace(httpPort, httpsPort);
			}
			redirect(url);
		}*/
		
		User u = getCurrentUser();
		renderArgs.put("currentUser", u);
		Account a = getActiveAccount();
		renderArgs.put("activeAccount", a);
	}

	protected static User getCurrentUser() {
		String cache_key = getCurrentUserCacheKey();
		User fromCache = Cache.get(cache_key, User.class);
		if (fromCache != null) {
			return fromCache;
		}
		long currentUserId = 0;
		if (session.contains("currentUserId")) {
			String s = session.get("currentUserId");
			currentUserId = Long.parseLong(s);
		}
		if (currentUserId > 0) {
			fromCache = User.getByKey(currentUserId);
			Cache.set(getCurrentUserCacheKey(), fromCache, "10mn");
		}
		return fromCache;
	}

	protected static Account getActiveAccount() {
		//if no user, then there will be no account
		User u = getCurrentUser();
		if (u == null) {
			return null;
		}
		String cache_key = getActiveAccountCacheKey();
		Account fromCache = Cache.get(cache_key, Account.class);
		if (fromCache != null) {
			return fromCache;
		}
		long activeAccountId = 0;
		if (session.contains("activeAccountId")) {
			activeAccountId = Long.parseLong(session.get("activeAccountId"));
		} else {
			//no active account in session, default to the current user
			activeAccountId = getCurrentUser().getPrimaryAccount().id;
		}
		if (activeAccountId > 0) {
			fromCache = Account.getByKey(activeAccountId);
			Cache.set(getActiveAccountCacheKey(), fromCache, "10mn");
		}
		return fromCache;
	}

	protected static String getCurrentUserCacheKey() {
		return session.getId() + "_currentUser";
	}

	protected static String getActiveAccountCacheKey() {
		return session.getId() + "_activeAccount";
	}

	protected static void addGeneralError(Exception ex) {
		Validation.addError("general", ex.getMessage());
	}
	
	protected static void setGeneralErrorMessage(String message) {
		addGeneralError(new MessageException(message));
	}

	protected static void setGeneralMessage(String message) {
		flash.put("message", message);
	}

	protected static String getServerPath() {
		return String.format("http://%s", request.host);
	}

	protected static String renderToString(String template) {
		return renderToString(template, new HashMap<String, Object>());
	}

	protected static String renderToString(String template, Map<String, Object> args) {
		Template t = TemplateLoader.load(template);
		return t.render(args);
	}

	protected static void result(Object o) {
		throw new GenericResult(o);
	}

	protected static void resultTemplate(String name) {
		Template t = TemplateLoader.load(name);
		throw new GenericResult(t.render());
	}

	protected static void resultError(String message) {
		throw new GenericResult(message, true);
	}

	@Catch(Exception.class)
	protected static void onException(Exception ex) {
		if (params.get("ext") != null && params.get("ext").equals("json")) {
			resultError(ex.getMessage());
		}
		if (!(ex instanceof MessageException)) {
			//send error email if the exception wasnt a message
			if (Play.mode == Mode.DEV) {
				
			} else {
				Mails.sendError(ex, request);
			}
		}
	}

	@Finally
	public static void compress() throws IOException {
		//dont compress in dev mode
		if (play.Play.mode == Mode.DEV) {
			return;
		}
		
		String text = response.out.toString();
		
		if (StringUtils.isEmpty(text)) {
			return;
		}

		if (!StringUtils.isEmpty(response.contentType)) {		
			if (response.contentType.equals("text/xml")) {
				text = new XmlCompressor().compress(text);
			} else if (response.contentType.equals("text/html")) {
				text = new HtmlCompressor().compress(text);
			}
		}
		
		//check if gzip is actually supported before compressing;
		Header ae = request.headers.get("accept-encoding");
		if (ae != null && ae.value().contains("gzip")) {
			Logger.info("Gzipping response since user-agent supports it.");
			final ByteArrayOutputStream gzip = gzip(text);
			response.setHeader("Content-Encoding", "gzip");
			response.setHeader("Content-Length", gzip.size() + "");
			response.out = gzip;
		} else {
			Logger.info("User-Agent doesnt support gzip, not gzipping response.");
			response.out = new ByteArrayOutputStream();
			response.writeChunk(text);
		}
	}

	private static ByteArrayOutputStream gzip(final String input) throws IOException {
		final InputStream inputStream = new ByteArrayInputStream(input.getBytes());
		final ByteArrayOutputStream stringOutputStream = new ByteArrayOutputStream((int) (input.length() * 0.75));
		final OutputStream gzipOutputStream = new GZIPOutputStream(stringOutputStream);

		final byte[] buf = new byte[5000];
		int len;
		while ((len = inputStream.read(buf)) > 0) {
			gzipOutputStream.write(buf, 0, len);
		}

		inputStream.close();
		gzipOutputStream.close();

		return stringOutputStream;
	}
}