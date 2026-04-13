package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.LegacyAmpersandFormatting;
import com.ddev14.kkschat.chat.MessageType;
import com.ddev14.kkschat.chat.PlayerNameResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SystemChatHandler {
	private SystemChatHandler() {}

	private static final Map<String, String> MINECRAFT_ID_TO_KEY;
	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("minecraft:overworld",   "dimension.minecraft.overworld");
		m.put("minecraft:the_nether",  "dimension.minecraft.the_nether");
		m.put("minecraft:the_end",     "dimension.minecraft.the_end");
		m.put("minecraft:day",         "kkschat.time.day");
		m.put("minecraft:noon",        "kkschat.time.noon");
		m.put("minecraft:sunset",      "kkschat.time.sunset");
		m.put("minecraft:dusk",        "kkschat.time.dusk");
		m.put("minecraft:night",       "kkschat.time.night");
		m.put("minecraft:midnight",    "kkschat.time.midnight");
		m.put("minecraft:sunrise",     "kkschat.time.sunrise");
		m.put("minecraft:dawn",        "kkschat.time.dawn");
		MINECRAFT_ID_TO_KEY = java.util.Collections.unmodifiableMap(m);
	}

	/** Заменяет сырые идентификаторы типа {@code minecraft:night} на локализованные названия. */
	private static Component prettifyMinecraftIds(Component component) {
		String text = component.getString();
		if (!text.contains("minecraft:")) return component;

		String result = text;
		Language lang = Language.getInstance();
		for (Map.Entry<String, String> entry : MINECRAFT_ID_TO_KEY.entrySet()) {
			if (result.contains(entry.getKey())) {
				String path = entry.getKey().substring(entry.getKey().indexOf(':') + 1);
				String fallback = path.replace('_', ' ');
				fallback = Character.toUpperCase(fallback.charAt(0)) + fallback.substring(1);
				String translated = lang.getOrDefault(entry.getValue(), fallback);
				result = result.replace(entry.getKey(), translated);
			}
		}

		if (result.equals(text)) return component;
		return Component.literal(result).withStyle(component.getStyle());
	}

	/**
	 * Классифицирует системное сообщение — возвращает единственный {@link MessageType}.
	 * Порядок проверок отражает приоритет: чем выше, тем специфичнее тип.
	 */
	static MessageType classify(Component component, String raw, String translationKey) {
		String lower = raw.toLowerCase();

		// --- Достижения / испытания (по translation key — наиболее надёжно) ---
		if (translationKey != null) {
			if (translationKey.equals("chat.type.advancement.challenge")
					|| translationKey.contains("advancements.challenge")) {
				return MessageType.CHALLENGE;
			}
			if (translationKey.startsWith("chat.type.advancement")
					|| translationKey.startsWith("advancements.")) {
				return MessageType.ACHIEVEMENT;
			}
		}
		// Текстовые fallback для ачивок (кастомные серверы могут не использовать ключи)
		if (lower.contains("has completed the challenge") || lower.contains("завершил испытание")
				|| lower.contains("завершила испытание") || lower.contains("completed the challenge")) {
			return MessageType.CHALLENGE;
		}
		if (lower.contains("has made the advancement") || lower.contains("получил достижение")
				|| lower.contains("получила достижение") || lower.contains("получил прогресс")
				|| lower.contains("получила прогресс") || lower.contains("achievement")
				|| lower.contains("advancement")) {
			return MessageType.ACHIEVEMENT;
		}

		// --- Командный блок (chat.type.admin → "[@: output]") ---
		if (translationKey != null && translationKey.equals("chat.type.admin")) {
			return MessageType.COMMAND_BLOCK;
		}
		if (lower.startsWith("[@") || lower.startsWith("[@:") || lower.startsWith("[командный блок")
				|| lower.startsWith("[command block")) {
			return MessageType.COMMAND_BLOCK;
		}

		// --- Шёпот (только команды /msg, /tell, /w) ---
		if (translationKey != null && translationKey.contains("commands.message")) {
			return MessageType.WHISPER;
		}

		// --- Обычный чат через системный канал (chat.type.text = "<%s> %s") ---
		if (translationKey != null && translationKey.equals("chat.type.text")) {
			return MessageType.PLAYER_CHAT;
		}
		// Текстовые паттерны: только характерные фразы с контекстом, не одиночные слова.
		// "tell" намеренно исключён — он слишком часто встречается в обычной речи.
		if (lower.contains("whispers to you") || lower.contains("шепчет вам")
				|| lower.contains("шепнул вам") || lower.contains("шепнула вам")
				|| lower.contains("whispers:") || lower.contains("шепчет:")) {
			return MessageType.WHISPER;
		}

		// --- Вход / выход игрока ---
		if (translationKey != null && (translationKey.contains("multiplayer.player.joined")
				|| translationKey.contains("multiplayer.player.left"))) {
			return MessageType.JOIN_LEAVE;
		}
		if (lower.contains("[+]") || lower.contains("[-]") || lower.contains("joined")
				|| lower.contains("присоединился") || lower.contains("присоединилась")
				|| lower.contains("вошел") || lower.contains("вошла") || lower.contains("left")
				|| lower.contains("покинул") || lower.contains("покинула")
				|| lower.contains("вышел") || lower.contains("вышла")
				|| lower.contains("disconnected") || lower.contains("отключился")
				|| lower.contains("отключилась")) {
			return MessageType.JOIN_LEAVE;
		}

		// --- Ошибки команд ---
		if (translationKey != null && (translationKey.contains("commands.help.failed")
				|| translationKey.contains("argument.entity.unknown")
				|| translationKey.contains("argument.item.id.invalid")
				|| translationKey.contains("argument.nbt.unknown")
				|| translationKey.contains("argument.uuid.invalid")
				|| translationKey.contains("commands.generic.exception")
				|| translationKey.contains("commands.generic.syntax")
				|| translationKey.contains("commands.generic.unknown"))) {
			return MessageType.ERROR;
		}
		if (lower.contains("error") || lower.contains("ошибка") || lower.contains("exception")
				|| lower.contains("исключение") || lower.contains("failed") || lower.contains("не удалось")
				|| lower.contains("cannot") || lower.contains("не может") || lower.contains("unable")
				|| lower.contains("unknown command") || lower.contains("неизвестная команда")
				|| lower.contains("invalid") || lower.contains("does not exist")
				|| lower.contains("не существует")) {
			return MessageType.ERROR;
		}

		// --- Сон / кровать ---
		if (translationKey != null && (translationKey.contains("tile.bed")
				|| translationKey.contains("sleep") || translationKey.contains("bed.noSleep"))) {
			return MessageType.SLEEP;
		}
		if (lower.contains("bed") || lower.contains("кровать") || lower.contains("sleep")
				|| lower.contains("спать") || lower.contains("thunderstorm") || lower.contains("гроза")
				|| lower.contains("too far") || lower.contains("слишком далеко")
				|| lower.contains("occupied") || lower.contains("занята")
				|| lower.contains("not safe") || lower.contains("небезопасно")
				|| lower.contains("monsters nearby") || lower.contains("монстры поблизости")) {
			return MessageType.SLEEP;
		}

		// --- Скриншот ---
		if (translationKey != null && translationKey.contains("screenshot")) {
			return MessageType.SCREENSHOT;
		}
		if (lower.contains("screenshot") || lower.contains("скриншот")) {
			return MessageType.SCREENSHOT;
		}

		// --- Попытка определить сообщение игрока через кастомный серверный формат ---
		// Серверы вроде Hypixel используют: [ранг] Ник: текст  или  [ранг] Ник » текст
		// extractPlayerNameFromText пропускает [...] скобки и берёт первое валидное слово.
		// RESERVED_WORDS и минимум 3 символа в isValidPlayerName защищают от ложных срабатываний.
		String playerName = PlayerNameResolver.extractPlayerNameFromComponent(component);
		if (playerName == null) {
			playerName = PlayerNameResolver.extractPlayerNameFromText(raw);
		}
		if (playerName == null && raw.contains("»")) {
			int arrowIndex = raw.indexOf("»");
			String beforeArrow = raw.substring(0, arrowIndex).trim();
			int lastSpace = beforeArrow.lastIndexOf(' ');
			int lastBracket = beforeArrow.lastIndexOf(']');
			int startIndex = Math.max(lastSpace, lastBracket) + 1;
			if (startIndex < beforeArrow.length()) {
				String potential = beforeArrow.substring(startIndex).trim()
						.replaceAll("[§&][0-9A-FK-ORa-fk-or]", "").trim();
				if (PlayerNameResolver.isValidPlayerName(potential)) {
					playerName = potential;
				}
			}
		}
		if (playerName != null) {
			boolean looksLikeChat = raw.contains("»") || raw.contains(":")
					|| raw.contains(">") || raw.contains("<")
					|| raw.length() > playerName.length() + 5;
			if (looksLikeChat) {
				return MessageType.PLAYER_CHAT;
			}
		}

		return MessageType.SYSTEM;
	}

	/**
	 * Возвращает true если видимый текст компонента пуст после удаления §- и &-форматирующих кодов.
	 * Серверы часто шлют "пустые" строки из одних кодов (§r, &r, &7 и т.д.) для пропусков в чате.
	 */
	static boolean isVisiblyEmpty(Component component) {
		if (component == null) return true;
		String text = component.getString();
		// getString() уже снимает §-коды; убираем оставшиеся &-литералы
		String stripped = text.replaceAll("[&§][0-9A-FK-ORa-fk-or]", "").strip();
		return stripped.isEmpty();
	}

	public static void handle(KksChatHud hud, Component component) {
		if (isVisiblyEmpty(component)) {
			return;
		}

		// Если стилизация выключена — показываем как есть, без classify/routing/applyFormatting
		if (!hud.isModifyMessageText()) {
			hud.messageComponent = component;
			hud.senderInfo = null;
			ChatMessageEntry raw = hud.addToHistory(component, null, MessageType.SYSTEM, null);
			long now = System.currentTimeMillis();
			if (raw != null && raw.repeatCount > 1) {
				hud.lastMessageTime = now;
			} else {
				hud.firstMessageTime = now;
				hud.lastMessageTime = now;
			}
			return;
		}

		component = prettifyMinecraftIds(component);
		String raw = component.getString();

		String translationKey = null;
		if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
			translationKey = tc.getKey();
		}

		MessageType type = classify(component, raw, translationKey);

		// Если определили как сообщение игрока — перенаправляем
		if (type == MessageType.PLAYER_CHAT) {
			hud.onPlayerMessage(component, null);
			return;
		}

		// Если определили как шёпот — перенаправляем
		if (type == MessageType.WHISPER) {
			WhisperChatHandler.handleWhisper(hud, component, raw);
			return;
		}

		// Применяем форматирование
		Component styled = applyFormatting(component, raw, type);

		// Звук при испытании
		if (type == MessageType.CHALLENGE) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null && minecraft.level != null) {
				minecraft.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
			}
		}

		hud.messageComponent = styled;
		hud.senderInfo = null;

		ChatMessageEntry entry = hud.addToHistory(hud.messageComponent, null, type, null);

		boolean isRepeat = entry != null && entry.repeatCount > 1;
		long now = System.currentTimeMillis();
		if (isRepeat) {
			hud.lastMessageTime = now;
		} else {
			hud.firstMessageTime = now;
			hud.lastMessageTime = now;
		}
	}

	private static Component applyFormatting(Component component, String raw, MessageType type) {
		boolean hasLegacy = raw.indexOf('&') != -1;
		if (!hasLegacy) {
			if (type == MessageType.CHALLENGE || type == MessageType.ACHIEVEMENT) {
				ChatFormatting color = type == MessageType.CHALLENGE
						? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GREEN;
				Style newStyle = component.getStyle().withColor(color);
				return component.copy().setStyle(newStyle);
			}
			return component;
		}
		// Есть &-коды — конвертируем
		Component colorized = switch (type) {
			case CHALLENGE   -> LegacyAmpersandFormatting.applyLegacyColorCodesForChallenge(raw);
			case ACHIEVEMENT -> LegacyAmpersandFormatting.applyLegacyColorCodesForAchievement(raw);
			default          -> LegacyAmpersandFormatting.applyLegacyColorCodesForSystem(raw);
		};
		return LegacyAmpersandFormatting.copyClickHandlers(component, colorized);
	}
}
