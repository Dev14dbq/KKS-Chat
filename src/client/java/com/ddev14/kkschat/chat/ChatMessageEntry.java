package com.ddev14.kkschat.chat;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Одна запись в истории кастомного чата.
 */
public final class ChatMessageEntry {
	public Component message;
	public PlayerInfo senderInfo;
	public PlayerInfo receiverInfo;
	public boolean systemMessage;
	public boolean isWhisper;
	public long timestamp;
	public long firstMessageTime;
	public int repeatCount = 1;
	public UUID senderUUID;
	public String senderName;
	public boolean isSpam;
	public String messagePattern;
	public boolean isCollapsed;
	public int collapsedCount;
	public boolean isExpanded;
	public List<ChatMessageEntry> expandedMessages;
	public boolean isAchievement;
	public boolean isChallenge;
	public boolean isError;
	public boolean isSleepMessage;
	public boolean isJoinMessage;
	public boolean isScreenshot;

	public ChatMessageEntry(Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo,
			boolean systemMessage, boolean isWhisper, String senderName) {
		this.message = message;
		this.senderInfo = senderInfo;
		this.receiverInfo = receiverInfo;
		this.systemMessage = systemMessage;
		this.isWhisper = isWhisper;
		this.senderName = senderName;
		long now = System.currentTimeMillis();
		this.timestamp = now;
		this.firstMessageTime = now;
		if (senderInfo != null) {
			this.senderUUID = senderInfo.getProfile().id();
		}
		this.messagePattern = ChatSpamPatterns.extractMessagePattern(message.getString());
	}

	public void updateSenderInfo(PlayerInfo newInfo) {
		if (newInfo != null && (senderInfo == null || senderUUID == null || senderUUID.equals(newInfo.getProfile().id()))) {
			this.senderInfo = newInfo;
			if (senderUUID == null) {
				this.senderUUID = newInfo.getProfile().id();
			}
		}
	}

}
