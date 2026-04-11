package com.ddev14.kkschat.chat;

import static com.ddev14.kkschat.chat.ChatLayout.FADE_IN_TIME_MS;
import static com.ddev14.kkschat.chat.ChatLayout.FADE_OUT_TIME_MS;

/**
 * Расчёт видимости строки по настройке «время отображения».
 */
public final class ChatDisplayTiming {
	private ChatDisplayTiming() {}

	public static float alphaForDisplayDuration(ChatMessageEntry entry, long now, int displayTimeSeconds) {
		long displayTimeMs = displayTimeSeconds * 1000L;
		long entryAge = now - entry.timestamp;
		if (entryAge > displayTimeMs) {
			return -1f;
		}
		long timeSinceFirst = now - entry.firstMessageTime;
		long timeUntilHide = displayTimeMs - entryAge;
		if (timeSinceFirst < FADE_IN_TIME_MS) {
			return (float) timeSinceFirst / FADE_IN_TIME_MS;
		}
		if (timeUntilHide < FADE_OUT_TIME_MS) {
			return (float) timeUntilHide / FADE_OUT_TIME_MS;
		}
		return 1.0f;
	}
}
