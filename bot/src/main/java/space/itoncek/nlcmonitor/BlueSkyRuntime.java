package space.itoncek.nlcmonitor;

import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class BlueSkyRuntime implements Closeable {
	private static final Logger log = LoggerFactory.getLogger("BlueSky");
	private String accessJWT;
	private String refreshJWT;
	private final OkHttpClient client = new OkHttpClient();

	public BlueSkyRuntime() {
		try {
			File creds = new File("./bluesky.login");
			if (!creds.exists()) createCredsFile(creds);
			JSONObject auth = readCredsFile(creds);

			MediaType mediaType = MediaType.parse("application/json");
			RequestBody body = RequestBody.create(new JSONObject().put("identifier", auth.getString("user")).put("password",auth.getString("password")).toString(4), mediaType);
			Request request = new Request.Builder()
					.url("https://bsky.social/xrpc/com.atproto.server.createSession")
					.post(body)
					.addHeader("Content-Type", "application/json")
					.build();

			Response response = client.newCall(request).execute();

			assert response.body() != null;
			JSONObject res = new JSONObject(response.body().string());

			accessJWT = res.getString("accessJwt");
			refreshJWT = res.getString("refreshJwt");

			log.info("Logged into BlueSky as @{}", res.getString("handle"));

			response.close();
		} catch (IOException e) {
			log.error("BlueSkyRuntime()", e);
		}
	}

	private JSONObject readCredsFile(File creds) throws IOException {
		String data = Files.readString(creds.toPath());
		if (data == null) {
			createCredsFile(creds);
			return readCredsFile(creds);
		} else {
			try {
				return new JSONObject(data);
			} catch (JSONException e) {
				creds.delete();
				createCredsFile(creds);
				return readCredsFile(creds);
			}
		}
	}

	private void createCredsFile(File creds) throws IOException {
		try (FileWriter fos = new FileWriter(creds)) {
			fos.write(new JSONObject().put("user", "empty").put("password", "password").toString(4));
		}
	}


	private void refreshSession() {
		try {
			Request request = new Request.Builder()
					.url("https://bsky.social/xrpc/com.atproto.server.refreshSession")
					.post(RequestBody.create("".getBytes(StandardCharsets.UTF_8)))
					.addHeader("Authorization", "Bearer " + refreshJWT)
					.build();

			Response response = client.newCall(request).execute();
			assert response.body() != null;
			JSONObject res = new JSONObject(response.body().string());

			accessJWT = res.getString("accessJwt");
			refreshJWT = res.getString("refreshJwt");

			response.close();
		} catch (IOException e) {
			log.error("refreshSession()", e);
		}
	}


	public void postBluesky(String message) throws IOException {
		OkHttpClient client = new OkHttpClient();

		MediaType mediaType = MediaType.parse("application/json");
		RequestBody body = RequestBody.create(new JSONObject()
				.put("repo", "itoncek.space")
				.put("collection", "app.bsky.feed.post")
				.put("record", new JSONObject().put("text", message).put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"))
				.toString(4), mediaType);
		Request request = new Request.Builder()
				.url("https://bsky.social/xrpc/com.atproto.repo.createRecord")
				.post(body)
				.addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer " + accessJWT)
				.build();

		Response response = client.newCall(request).execute();
		if (response.isSuccessful()) return;

		response.close();
		if (response.body() == null) throw new IOException("BSky error!");
		throw new IOException("BSky error!" + response.body().string());
	}

	@Override
	public void close() throws IOException {
		Request request = new Request.Builder()
				.url("https://bsky.social/xrpc/com.atproto.server.deleteSession")
				.post(RequestBody.create("".getBytes(StandardCharsets.UTF_8)))
				.addHeader("Authorization", "Bearer " + refreshJWT)
				.build();

		client.newCall(request).execute().close();
	}
}
