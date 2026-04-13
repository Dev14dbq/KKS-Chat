package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatAnimationType;
import com.ddev14.kkschat.chat.ChatRule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сохранение и загрузка настроек KKS Chat в/из JSON-файла.
 */
final class KksChatConfig {
	private KksChatConfig() {}

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.setLenient()
			.create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
			.getConfigDir().resolve("kks-chat.json");

	private static final Type RULES_TYPE = new TypeToken<List<ChatRule>>(){}.getType();

	private static class Data {
		float backgroundOpacity;
		int displayTimeSeconds;
		int maxHistorySize;
		int maxVisibleMessages;
		int chatPosition;
		boolean antiSpamEnabled;
		boolean modifyMessageText;
		boolean enabled = true;
		// Иконки (item ID)
		String iconSystem       = "minecraft:stick";
		String iconError        = "minecraft:barrier";
		String iconSleep        = "minecraft:red_bed";
		String iconAchievement  = "minecraft:emerald";
		String iconChallenge    = "minecraft:netherite_ingot";
		String iconCommandBlock = "minecraft:command_block";
		String iconWhisper      = "minecraft:paper";
		String iconJoinLeave    = "minecraft:oak_door";
		// Rules
		List<ChatRule> rules    = new ArrayList<>();
		// Animation
		String animationIn  = "FADE";
		String animationOut = "FADE";
		// Background colors per message type (type name → hex string)
		Map<String, String> bgColors = null; // null = use defaults
	}

	private static String nonNull(String value, String def) {
		return value != null && !value.isBlank() ? value : def;
	}

	static void save(KksChatHud hud) {
		try {
			String cfg = "{\n"

				+ "  // ── General ─────────────────────────────────────────────────────────\n"
				+ "  // Enable or disable KKS Chat entirely.\n"
				+ "  // When disabled, the vanilla Minecraft chat is used instead.\n"
				+ "  \"enabled\": " + hud.isEnabled() + ",\n"

				+ "\n"
				+ "  // Modify message text (stylization, reformatting).\n"
				+ "  // Reserved for future use — has no effect yet.\n"
				+ "  \"modifyMessageText\": " + hud.isModifyMessageText() + ",\n"

				+ "\n"
				+ "  // Suppress duplicate messages and merge them into one entry with a repeat counter.\n"
				+ "  \"antiSpamEnabled\": " + hud.isAntiSpamEnabled() + ",\n"

				+ "\n"
				+ "  // ── Appearance ───────────────────────────────────────────────────────\n"
				+ "  // Background opacity of message boxes (0.0 = fully transparent, 1.0 = fully opaque).\n"
				+ "  \"backgroundOpacity\": " + hud.backgroundOpacity + ",\n"

				+ "\n"
				+ "  // Chat position on screen.\n"
				+ "  // 0 = center-bottom, 1 = center-top, 2 = left-top,\n"
				+ "  // 3 = left-bottom,   4 = right-top,  5 = right-bottom\n"
				+ "  \"chatPosition\": " + hud.getChatPosition() + ",\n"

				+ "\n"
				+ "  // ── History ──────────────────────────────────────────────────────────\n"
				+ "  // How long (in seconds) a message stays visible in the closed overlay.\n"
				+ "  \"displayTimeSeconds\": " + hud.displayTimeSeconds + ",\n"

				+ "\n"
				+ "  // Maximum number of messages stored in history.\n"
				+ "  \"maxHistorySize\": " + hud.maxHistorySize + ",\n"

				+ "\n"
				+ "  // Maximum number of messages visible at once when the chat is open.\n"
				+ "  \"maxVisibleMessages\": " + hud.maxVisibleMessages + ",\n"

				+ "\n"
				+ "  // ── Icons ────────────────────────────────────────────────────────────\n"
				+ "  // Item IDs for icons displayed next to each message type.\n"
				+ "  // Use any valid Minecraft item ID, e.g. \"minecraft:diamond\".\n"
				+ "  \"iconSystem\":       \"" + hud.iconSystem       + "\",\n"
				+ "  \"iconError\":        \"" + hud.iconError        + "\",\n"
				+ "  \"iconSleep\":        \"" + hud.iconSleep        + "\",\n"
				+ "  \"iconAchievement\":  \"" + hud.iconAchievement  + "\",\n"
				+ "  \"iconChallenge\":    \"" + hud.iconChallenge    + "\",\n"
				+ "  \"iconCommandBlock\": \"" + hud.iconCommandBlock + "\",\n"
				+ "  \"iconWhisper\":      \"" + hud.iconWhisper      + "\",\n"
				+ "  \"iconJoinLeave\":    \"" + hud.iconJoinLeave    + "\",\n"

				+ "\n"
				+ "  // ── Animation ────────────────────────────────────────────────────────\n"
				+ "  // Animation when a message appears (enter) and disappears (leave).\n"
				+ "  // Values: FADE, SLIDE_LEFT, SLIDE_RIGHT, SLIDE_UP, SLIDE_DOWN, BOUNCE, NONE\n"
				+ "  \"animationIn\":  \"" + hud.animationIn.name()  + "\",\n"
				+ "  \"animationOut\": \"" + hud.animationOut.name() + "\",\n"

				+ "\n"
				+ "  // ── Background colors ─────────────────────────────────────────────────\n"
				+ "  // Background color per message type. Hex string, e.g. \"#1A0028\".\n"
				+ "  // Use \"#000000\" for the default black background.\n"
				+ "  \"bgColors\": " + GSON.toJson(hud.bgColors) + ",\n"

				+ "\n"
				+ "  // ── Rules ────────────────────────────────────────────────────────────\n"
				+ "  // Rules are evaluated in order for every incoming message.\n"
				+ "  // Each rule has an \"if\" block (conditions) and a \"then\" block (actions).\n"
				+ "  //\n"
				+ "  // Conditions (\"if\"):\n"
				+ "  //   type        — MessageType name: PLAYER_CHAT, WHISPER, SYSTEM, ERROR, SLEEP,\n"
				+ "  //                  ACHIEVEMENT, CHALLENGE, COMMAND_BLOCK, JOIN_LEAVE, SCREENSHOT\n"
				+ "  //   contains    — text contains this string (case-sensitive)\n"
				+ "  //   startsWith  — text starts with this string\n"
				+ "  //   endsWith    — text ends with this string\n"
				+ "  //   regex       — full Java regex; first capture group becomes the matched fragment\n"
				+ "  //   matchType   — \"ALL\" (default, AND) or \"ANY\" (OR)\n"
				+ "  //\n"
				+ "  // Actions (\"then\"):\n"
				+ "  //   icon         — override icon (Minecraft item ID, e.g. \"minecraft:diamond\")\n"
				+ "  //   hide         — hide the message entirely (true/false)\n"
				+ "  //   noStyle      — disable stylization for this message (true/false)\n"
				+ "  //   displayTime  — override display time in seconds for this message\n"
				+ "  //   color        — tint the whole message (hex, e.g. \"#FFD700\")\n"
				+ "  //   replaceText  — replace the entire message text with this string\n"
				+ "  //   replaceMatch — replace the matched fragment (from contains/regex)\n"
				+ "  //   colorMatch   — color the matched fragment only (hex)\n"
				+ "  //   stopAfter    — stop processing further rules after this one (true/false)\n"
				+ "  //\n"
				+ "  // Example — hide command-block output:\n"
				+ "  //   { \"if\": { \"type\": \"COMMAND_BLOCK\" }, \"then\": { \"hide\": true } }\n"
				+ "  //\n"
				+ "  // Example — gold icon + color for VIP players:\n"
				+ "  //   { \"if\": { \"contains\": \"[VIP]\" }, \"then\": { \"icon\": \"minecraft:gold_ingot\", \"colorMatch\": \"#FFD700\" } }\n"
				+ "  \"rules\": " + GSON.toJson(hud.rules) + "\n"

				+ "}\n";

			Files.writeString(CONFIG_PATH, cfg, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LoggerFactory.getLogger("KksChatConfig").warn("Failed to save config", e);
		}
	}

	static void load(KksChatHud hud) {
		if (!Files.exists(CONFIG_PATH)) return;
		try {
			String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
			JsonReader reader = new JsonReader(new StringReader(json));
			reader.setLenient(true);
			Data d = GSON.fromJson(reader, Data.class);
			if (d == null) return;
			hud.backgroundOpacity = Math.max(0f, Math.min(1f, d.backgroundOpacity));
			hud.displayTimeSeconds = Math.max(1, Math.min(60, d.displayTimeSeconds));
			hud.maxHistorySize = Math.max(50, Math.min(500, d.maxHistorySize));
			hud.maxVisibleMessages = Math.max(1, Math.min(50, d.maxVisibleMessages));
			hud.chatPosition = Math.max(0, Math.min(5, d.chatPosition));
			hud.antiSpamEnabled = d.antiSpamEnabled;
			hud.modifyMessageText = d.modifyMessageText;
			hud.enabled = d.enabled;
			hud.iconSystem       = nonNull(d.iconSystem,       "minecraft:stick");
			hud.iconError        = nonNull(d.iconError,        "minecraft:barrier");
			hud.iconSleep        = nonNull(d.iconSleep,        "minecraft:red_bed");
			hud.iconAchievement  = nonNull(d.iconAchievement,  "minecraft:emerald");
			hud.iconChallenge    = nonNull(d.iconChallenge,    "minecraft:netherite_ingot");
			hud.iconCommandBlock = nonNull(d.iconCommandBlock, "minecraft:command_block");
			hud.iconWhisper      = nonNull(d.iconWhisper,      "minecraft:paper");
			hud.iconJoinLeave    = nonNull(d.iconJoinLeave,    "minecraft:oak_door");
			hud.rules.clear();
			if (d.rules != null) hud.rules.addAll(d.rules);
			if (d.animationIn != null) {
				try { hud.animationIn = ChatAnimationType.valueOf(d.animationIn.toUpperCase()); }
				catch (IllegalArgumentException ignored) { hud.animationIn = ChatAnimationType.FADE; }
			}
			if (d.animationOut != null) {
				try { hud.animationOut = ChatAnimationType.valueOf(d.animationOut.toUpperCase()); }
				catch (IllegalArgumentException ignored) { hud.animationOut = ChatAnimationType.FADE; }
			}
			if (d.bgColors != null && !d.bgColors.isEmpty()) {
				// Start from defaults, then override with saved values
				hud.bgColors = KksChatHud.defaultBgColors();
				hud.bgColors.putAll(d.bgColors);
			}
		} catch (IOException e) {
			LoggerFactory.getLogger("KksChatConfig").warn("Failed to load config", e);
		}
	}
}
