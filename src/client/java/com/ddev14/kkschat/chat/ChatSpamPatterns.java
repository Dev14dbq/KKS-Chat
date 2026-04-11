package com.ddev14.kkschat.chat;

/**
 * Утилиты для извлечения паттерна текста (используется как метаданные в {@link ChatMessageEntry}).
 */
public final class ChatSpamPatterns {
	private ChatSpamPatterns() {}

	/** Заменяет все числа в тексте на "N" — используется как паттерн для метаданных. */
	public static String extractMessagePattern(String messageText) {
		if (messageText == null || messageText.isEmpty()) {
			return "";
		}
		return messageText.replaceAll("\\d+", "N");
	}
}
