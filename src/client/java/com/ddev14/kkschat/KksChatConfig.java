package com.ddev14.kkschat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сохранение и загрузка настроек KKS Chat в/из JSON-файла.
 */
final class KksChatConfig {
	private KksChatConfig() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("kks-chat.json");

	private static class Data {
		float backgroundOpacity;
		int displayTimeSeconds;
		int maxHistorySize;
		int maxVisibleMessages;
		int chatPosition;
		boolean antiSpamEnabled;
		boolean modifyMessageText;
		boolean enabled = true;
	}

	static void save(KksChatHud hud) {
		try {
			Data d = new Data();
			d.backgroundOpacity = hud.backgroundOpacity;
			d.displayTimeSeconds = hud.displayTimeSeconds;
			d.maxHistorySize = hud.maxHistorySize;
			d.maxVisibleMessages = hud.maxVisibleMessages;
			d.chatPosition = hud.getChatPosition();
			d.antiSpamEnabled = hud.isAntiSpamEnabled();
			d.modifyMessageText = hud.isModifyMessageText();
			d.enabled = hud.isEnabled();
			Files.writeString(CONFIG_PATH, GSON.toJson(d), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LoggerFactory.getLogger("KksChatConfig").warn("Failed to save config", e);
		}
	}

	static void load(KksChatHud hud) {
		if (!Files.exists(CONFIG_PATH)) return;
		try {
			String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
			Data d = GSON.fromJson(json, Data.class);
			if (d == null) return;
			hud.backgroundOpacity = Math.max(0f, Math.min(1f, d.backgroundOpacity));
			hud.displayTimeSeconds = Math.max(1, Math.min(60, d.displayTimeSeconds));
			hud.maxHistorySize = Math.max(50, Math.min(500, d.maxHistorySize));
			hud.maxVisibleMessages = Math.max(1, Math.min(50, d.maxVisibleMessages));
			hud.chatPosition = Math.max(0, Math.min(5, d.chatPosition));
		hud.antiSpamEnabled = d.antiSpamEnabled;
		hud.modifyMessageText = d.modifyMessageText;
		hud.enabled = d.enabled;
		} catch (IOException e) {
			LoggerFactory.getLogger("KksChatConfig").warn("Failed to load config", e);
		}
	}
}
