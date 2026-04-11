package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Добавление строк в историю чата {@link KksChatHud#messageHistory}.
 * <p>
 * Суммирование (x2, x3) срабатывает только для <b>полностью идентичных</b> сообщений
 * (одинаковый текст + одинаковый отправитель + одинаковый тип системное/шёпот).
 * Когда {@link KksChatHud#isAntiSpamEnabled()} == false суммирование отключается полностью.
 */
public final class ChatHistoryAppender {

	private ChatHistoryAppender() {}

	/** Добавляет обычное или системное сообщение. */
	public static ChatMessageEntry addStandardLine(KksChatHud hud,
			Component message, PlayerInfo senderInfo, boolean systemMessage, String senderName) {
		if (message == null) return null;

		if (hud.isAntiSpamEnabled() && !hud.messageHistory.isEmpty()) {
			ChatMessageEntry last = hud.messageHistory.get(hud.messageHistory.size() - 1);
			if (isIdenticalStandard(last, message, senderInfo, systemMessage)) {
				return mergeInto(last, message, senderInfo, null, systemMessage, false, senderName);
			}
		}

		ChatMessageEntry entry = new ChatMessageEntry(message, senderInfo, null, systemMessage, false, senderName);
		hud.messageHistory.add(entry);
		trimToMaxHistory(hud);
		hud.scrollOffset = 0;
		return entry;
	}

	/** Добавляет whisper-сообщение. */
	public static ChatMessageEntry addWhisperLine(KksChatHud hud,
			Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo, String senderName) {
		if (message == null) return null;

		if (hud.isAntiSpamEnabled() && !hud.messageHistory.isEmpty()) {
			ChatMessageEntry last = hud.messageHistory.get(hud.messageHistory.size() - 1);
			if (isIdenticalWhisper(last, message, senderInfo)) {
				return mergeInto(last, message, senderInfo, receiverInfo, false, true, senderName);
			}
		}

		ChatMessageEntry entry = new ChatMessageEntry(message, senderInfo, receiverInfo, false, true, senderName);
		hud.messageHistory.add(entry);
		trimToMaxHistory(hud);
		hud.scrollOffset = 0;
		return entry;
	}

	/**
	 * Разворачивает все сгруппированные записи (repeatCount &gt; 1) в отдельные сообщения.
	 * Вызывается при отключении антиспама.
	 */
	public static void explodeGroups(KksChatHud hud) {
		List<ChatMessageEntry> expanded = new ArrayList<>(hud.messageHistory.size() * 2);
		for (ChatMessageEntry entry : hud.messageHistory) {
			if (entry.repeatCount > 1) {
				if (entry.expandedMessages != null && !entry.expandedMessages.isEmpty()) {
					for (ChatMessageEntry sub : entry.expandedMessages) {
						sub.repeatCount = 1;
						sub.expandedMessages = null;
						sub.isExpanded = false;
						expanded.add(sub);
					}
				} else {
					// expandedMessages не были сохранены — создаём копии
					for (int i = 0; i < entry.repeatCount; i++) {
						expanded.add(new ChatMessageEntry(
								entry.message, entry.senderInfo, entry.receiverInfo,
								entry.systemMessage, entry.isWhisper, entry.senderName));
					}
				}
			} else {
				entry.expandedMessages = null;
				entry.isExpanded = false;
				expanded.add(entry);
			}
		}
		hud.messageHistory.clear();
		hud.messageHistory.addAll(expanded);
		trimToMaxHistory(hud);
	}

	/**
	 * Перегруппировывает соседние идентичные сообщения обратно.
	 * Вызывается при включении антиспама.
	 */
	public static void regroupIdentical(KksChatHud hud) {
		List<ChatMessageEntry> source = new ArrayList<>(hud.messageHistory);
		hud.messageHistory.clear();
		for (ChatMessageEntry entry : source) {
			if (!hud.messageHistory.isEmpty()) {
				ChatMessageEntry last = hud.messageHistory.get(hud.messageHistory.size() - 1);
				boolean canMerge = entry.isWhisper
						? isIdenticalWhisper(last, entry.message, entry.senderInfo)
						: isIdenticalStandard(last, entry.message, entry.senderInfo, entry.systemMessage);
				if (canMerge) {
					mergeInto(last, entry.message, entry.senderInfo, entry.receiverInfo,
							entry.systemMessage, entry.isWhisper, entry.senderName);
					continue;
				}
			}
			hud.messageHistory.add(entry);
		}
	}

	/** Обрезка истории до {@link KksChatHud#maxHistorySize}. */
	public static void trimToMaxHistory(KksChatHud hud) {
		while (hud.messageHistory.size() > hud.maxHistorySize) {
			hud.messageHistory.remove(0);
		}
	}

	// ── internal helpers ──────────────────────────────────────────────────────

	/**
	 * Возвращает true только если текст, тип (system) и отправитель полностью совпадают.
	 */
	private static boolean isIdenticalStandard(ChatMessageEntry last,
			Component message, PlayerInfo senderInfo, boolean systemMessage) {
		if (last.systemMessage != systemMessage) return false;
		if (!last.message.getString().equals(message.getString())) return false;
		return sameSender(last.senderInfo, senderInfo);
	}

	/**
	 * Возвращает true только если текст и отправитель whisper-сообщения совпадают.
	 */
	private static boolean isIdenticalWhisper(ChatMessageEntry last,
			Component message, PlayerInfo senderInfo) {
		if (!last.isWhisper) return false;
		if (!last.message.getString().equals(message.getString())) return false;
		return sameSender(last.senderInfo, senderInfo);
	}

	private static boolean sameSender(PlayerInfo a, PlayerInfo b) {
		if (a == null && b == null) return true;
		if (a != null && b != null) return a.getProfile().id().equals(b.getProfile().id());
		return false;
	}

	/**
	 * Увеличивает счётчик у существующей записи и сохраняет дубликат для разворачивания.
	 */
	private static ChatMessageEntry mergeInto(ChatMessageEntry last,
			Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo,
			boolean systemMessage, boolean isWhisper, String senderName) {
		last.repeatCount++;
		last.timestamp = System.currentTimeMillis();

		if (last.expandedMessages == null) {
			last.expandedMessages = new ArrayList<>();
			last.expandedMessages.add(new ChatMessageEntry(
					last.message, last.senderInfo, last.receiverInfo,
					last.systemMessage, last.isWhisper, last.senderName));
		}
		last.expandedMessages.add(new ChatMessageEntry(
				message, senderInfo, receiverInfo, systemMessage, isWhisper, senderName));

		if (last.senderInfo == null && senderInfo != null) {
			last.updateSenderInfo(senderInfo);
		}
		if (last.senderName == null && senderName != null) {
			last.senderName = senderName;
		}
		return last;
	}
}
