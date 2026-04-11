package com.ddev14.kkschat.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback-загрузка URL скина через Mojang API, если {@link PlayerInfo} ещё нет.
 */
public final class MojangSkinCache {
	private MojangSkinCache() {}

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Map<String, String> SKIN_URL_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, UUID> UUID_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> LOADING_FLAGS = new ConcurrentHashMap<>();

	public static String getCachedSkinUrl(String playerName) {
		return SKIN_URL_CACHE.get(playerName);
	}

	public static void requestSkinLoad(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return;
		}
		if (SKIN_URL_CACHE.containsKey(playerName)) {
			return;
		}
		if (LOADING_FLAGS.putIfAbsent(playerName, true) != null) {
			return;
		}

		Thread.ofVirtual().start(() -> {
			try {
				UUID uuid = UUID_CACHE.get(playerName);
				if (uuid == null) {
					HttpRequest uuidRequest = HttpRequest.newBuilder()
							.uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
							.GET()
							.header("User-Agent", "Minecraft")
							.build();

					HttpResponse<String> uuidResponse = HTTP_CLIENT.send(uuidRequest, HttpResponse.BodyHandlers.ofString());
					if (uuidResponse.statusCode() == 200) {
						JsonObject profile = JsonParser.parseString(uuidResponse.body()).getAsJsonObject();
						String uuidString = profile.get("id").getAsString();
						uuid = UUID.fromString(uuidString.replaceFirst(
								"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
								"$1-$2-$3-$4-$5"
						));
						UUID_CACHE.put(playerName, uuid);
					} else {
						LOADING_FLAGS.remove(playerName);
						return;
					}
				}

				String uuidWithoutDashes = uuid.toString().replace("-", "");
				HttpRequest profileRequest = HttpRequest.newBuilder()
						.uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidWithoutDashes))
						.GET()
						.header("User-Agent", "Minecraft")
						.build();

				HttpResponse<String> profileResponse = HTTP_CLIENT.send(profileRequest, HttpResponse.BodyHandlers.ofString());
				if (profileResponse.statusCode() == 200) {
					JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
					JsonArray properties = profile.getAsJsonArray("properties");

					if (properties != null) {
						for (var prop : properties) {
							JsonObject property = prop.getAsJsonObject();
							if ("textures".equals(property.get("name").getAsString())) {
								String value = property.get("value").getAsString();
								String decoded = new String(Base64.getDecoder().decode(value));
								JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();

								if (textures.has("textures")) {
									JsonObject texturesObj = textures.getAsJsonObject("textures");
									if (texturesObj.has("SKIN")) {
										JsonObject skinObj = texturesObj.getAsJsonObject("SKIN");
										String skinUrl = skinObj.get("url").getAsString();
										SKIN_URL_CACHE.put(playerName, skinUrl);
									}
								}
								break;
							}
						}
					}
				}
			} catch (Exception ignored) {
				// намеренно глушим — сеть/Mojang могут быть недоступны
			} finally {
				LOADING_FLAGS.remove(playerName);
			}
		});
	}
}
