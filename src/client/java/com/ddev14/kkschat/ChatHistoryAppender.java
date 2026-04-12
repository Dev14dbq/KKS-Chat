package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageType;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Добавление строк в историю чата {@link KksChatHud#messageHistory}.
 * <p>
 * Суммирование (x2, x3) срабатывает только для <b>полностью идентичных</b> сообщений
 * (одинаковый текст + одинаковый тип + одинаковый отправитель).
 * Когда {@link KksChatHud#isAntiSpamEnabled()} == false суммирование отключается полностью.
 */
public final class ChatHistoryAppender {

	private ChatHistoryAppender() {}

	/**
	 * Добавляет сообщение любого типа в историю.
	 */
	public static ChatMessageEntry addLine(KksChatHud hud,
			Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo,
			MessageType type, String senderName) {
		if (message == null) return null;

		if (hud.isAntiSpamEnabled() && !hud.messageHistory.isEmpty()) {
			ChatMessageEntry last = hud.messageHistory.get(hud.messageHistory.size() - 1);
			if (isIdentical(last, message, senderInfo, type)) {
				return mergeInto(last, message, senderInfo, receiverInfo, type, senderName);
			}
		}

		ChatMessageEntry entry = new ChatMessageEntry(message, senderInfo, receiverInfo, type, senderName);
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
					for (int i = 0; i < entry.repeatCount; i++) {
						expanded.add(new ChatMessageEntry(
								entry.message, entry.senderInfo, entry.receiverInfo,
								entry.type, entry.senderName));
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
				if (isIdentical(last, entry.message, entry.senderInfo, entry.type)) {
					mergeInto(last, entry.message, entry.senderInfo, entry.receiverInfo,
							entry.type, entry.senderName);
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

	private static boolean isIdentical(ChatMessageEntry last,
			Component message, PlayerInfo senderInfo, MessageType type) {
		if (last.type != type) return false;
		if (!last.message.getString().equals(message.getString())) return false;
		return sameSender(last.senderInfo, senderInfo);
	}

	private static boolean sameSender(PlayerInfo a, PlayerInfo b) {
		if (a == null && b == null) return true;
		if (a != null && b != null) return a.getProfile().id().equals(b.getProfile().id());
		return false;
	}

	private static ChatMessageEntry mergeInto(ChatMessageEntry last,
			Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo,
			MessageType type, String senderName) {
		last.repeatCount++;
		last.timestamp = System.currentTimeMillis();

		if (last.expandedMessages == null) {
			last.expandedMessages = new ArrayList<>();
			last.expandedMessages.add(new ChatMessageEntry(
					last.message, last.senderInfo, last.receiverInfo,
					last.type, last.senderName));
		}
		last.expandedMessages.add(new ChatMessageEntry(
				message, senderInfo, receiverInfo, type, senderName));

		if (last.senderInfo == null && senderInfo != null) {
			last.updateSenderInfo(senderInfo);
		}
		if (last.senderName == null && senderName != null) {
			last.senderName = senderName;
		}
		return last;
	}
}
