package com.ddev14.kkschat.chat;

import net.minecraft.network.chat.Component;

/**
 * Извлечение ника игрока из текста и {@link Component}.
 */
public final class PlayerNameResolver {
	private PlayerNameResolver() {}

	public static String extractPlayerNameFromText(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}

		text = text.trim();
		if (text.isEmpty()) {
			return null;
		}

		String formatCodes = "0123456789abcdefklmnor";

		int start = 0;
		int end = 0;

		while (start < text.length()) {
			char c = text.charAt(start);

			if (c == ' ') {
				start++;
				continue;
			}

			if (c == '&' || c == '§') {
				if (start + 1 < text.length()) {
					char codeChar = text.charAt(start + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						start += 2;
						continue;
					}
				}
			}

			if (c == '[') {
				int bracketEnd = text.indexOf(']', start);
				if (bracketEnd != -1) {
					start = bracketEnd + 1;
					while (start < text.length() && text.charAt(start) == ' ') {
						start++;
					}
					continue;
				} else {
					break;
				}
			}

			break;
		}

		if (start >= text.length()) {
			return null;
		}

		while (start < text.length()) {
			char c = text.charAt(start);
			if (c == '&' || c == '§') {
				if (start + 1 < text.length()) {
					char codeChar = text.charAt(start + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						start += 2;
						continue;
					}
				}
			}
			break;
		}

		if (start >= text.length()) {
			return null;
		}

		char firstChar = text.charAt(start);

		if (firstChar >= 0x1F300 && firstChar <= 0x1F9FF) {
			return null;
		}
		if (firstChar >= 0x2600 && firstChar <= 0x26FF) {
			return null;
		}
		if (firstChar >= 0x2700 && firstChar <= 0x27BF) {
			return null;
		}

		if (!((firstChar >= 'a' && firstChar <= 'z') || (firstChar >= 'A' && firstChar <= 'Z') || firstChar == '_')) {
			if (firstChar > 127) {
				return null;
			}
			return null;
		}

		end = start + 1;
		int nameCharCount = 1;

		while (end < text.length() && nameCharCount < 16) {
			char c = text.charAt(end);

			if (c == '&' || c == '§') {
				if (end + 1 < text.length()) {
					char codeChar = text.charAt(end + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						end += 2;
						continue;
					}
				}
			}

			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				end++;
				nameCharCount++;
			} else {
				break;
			}
		}

		StringBuilder nameBuilder = new StringBuilder();
		for (int i = start; i < end; i++) {
			char c = text.charAt(i);
			if (c == '&' || c == '§') {
				if (i + 1 < text.length()) {
					char codeChar = text.charAt(i + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						i++;
						continue;
					}
				}
			}
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				nameBuilder.append(c);
			}
		}

		String name = nameBuilder.toString();

		if (!isValidPlayerName(name)) {
			return null;
		}

		// If the name is immediately followed by ':' it's a Minecraft namespace
		// (e.g. "minecraft:overworld", "minecraft:day") — not a player name
		if (end < text.length() && text.charAt(end) == ':') {
			return null;
		}

		return name;
	}

	/**
	 * Слова, которые никогда не являются никами: измерения, время суток,
	 * команды, служебные слова Minecraft и распространённые английские слова.
	 */
	private static final java.util.Set<String> RESERVED_WORDS = java.util.Set.of(
		// Измерения
		"overworld", "nether", "theend", "the_end",
		// Время суток
		"day", "night", "noon", "dusk", "dawn", "sunset", "sunrise", "midnight",
		// Игровые команды
		"kill", "tp", "give", "set", "run", "say", "me", "tell", "msg",
		"ban", "kick", "op", "deop", "stop", "save", "time", "weather",
		"gamemode", "difficulty", "spawnpoint", "clear", "fill", "clone",
		"execute", "summon", "setblock", "gamerule", "scoreboard", "team",
		"title", "bossbar", "trigger", "function", "schedule", "help",
		// Служебные слова системных сообщений
		"unknown", "command", "error", "failed", "invalid", "exception",
		"server", "console", "system", "minecraft", "fabric",
		// Распространённые слова, которые могут быть в начале сообщения
		"you", "your", "the", "this", "that", "has", "have", "was", "were",
		"not", "for", "and", "but", "with", "from", "into", "onto",
		"click", "here", "hover", "over", "open", "close", "view",
		"joined", "left", "disconnected", "connected",
		// Слова из сообщений командного блока / сервера
		"команда", "задана", "выполнена", "установлена"
	);

	public static boolean isValidPlayerName(String name) {
		if (name == null || name.length() < 3 || name.length() > 16) {
			return false;
		}

		if (!name.matches("^[a-zA-Z0-9_]+$") || name.matches("^_+$")) {
			return false;
		}

		if (RESERVED_WORDS.contains(name.toLowerCase())) {
			return false;
		}

		boolean hasLetter = false;
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
				hasLetter = true;
				break;
			}
		}

		if (!hasLetter) {
			return false;
		}

		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if (ch >= 0x1F300 && ch <= 0x1F9FF) {
				return false;
			}
			if (ch >= 0x2600 && ch <= 0x26FF) {
				return false;
			}
			if (ch >= 0x2700 && ch <= 0x27BF) {
				return false;
			}
			if (ch > 127 && !((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
					(ch >= '0' && ch <= '9') || ch == '_')) {
				return false;
			}
		}

		return true;
	}

	public static String extractPlayerNameFromComponent(Component component) {
		if (component == null) {
			return null;
		}

		String fullText = component.getString();
		String nameFromText = extractPlayerNameFromText(fullText);
		if (nameFromText != null) {
			return nameFromText;
		}

		if (component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plainText) {
			String text = plainText.text();
			if (text.startsWith("<") && text.length() > 2) {
				int end = text.indexOf('>');
				if (end > 1 && end < text.length()) {
					String name = text.substring(1, end);
					if (name.length() >= 2 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$")) {
						return name;
					}
				}
			}
			String name = extractPlayerNameFromText(text);
			if (name != null) {
				return name;
			}
		}

		for (Component sibling : component.getSiblings()) {
			String name = extractPlayerNameFromComponent(sibling);
			if (name != null) {
				return name;
			}
		}

		if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
			Object[] args = translatable.getArgs();
			if (args != null && args.length > 0) {
				for (Object arg : args) {
					if (arg instanceof Component argComponent) {
						String name = extractPlayerNameFromComponent(argComponent);
						if (name != null) {
							return name;
						}
						String argText = argComponent.getString();
						if (!argText.isEmpty() && argText.length() <= 16 && !argText.contains(" ") &&
								!argText.contains("<") && !argText.contains(">")) {
							if (argText.matches("^[a-zA-Z0-9_]+$")) {
								return argText;
							}
						}
					} else if (arg instanceof String argStr) {
						if (!argStr.isEmpty() && argStr.length() <= 16 && !argStr.contains(" ") &&
								!argStr.contains("<") && !argStr.contains(">")) {
							if (argStr.matches("^[a-zA-Z0-9_]+$")) {
								return argStr;
							}
						}
					}
				}
			}
		}

		return null;
	}
}
