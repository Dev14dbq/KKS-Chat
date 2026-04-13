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
	public MessageType type;
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

	// ── Rule overrides (set by RuleEngine) ───────────────────────────────────
	/** Non-null when a rule overrides the icon for this specific message. */
	public String iconOverride;
	/** True when a rule disables stylization for this specific message. */
	public boolean noStyle;
	/** Positive when a rule overrides the display time (seconds) for this message. */
	public int displayTimeOverride = -1;

	public ChatMessageEntry(Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo,
			MessageType type, String senderName) {
		this.message = message;
		this.senderInfo = senderInfo;
		this.receiverInfo = receiverInfo;
		this.type = type != null ? type : MessageType.SYSTEM;
		this.senderName = senderName;
		long now = System.currentTimeMillis();
		this.timestamp = now;
		this.firstMessageTime = now;
		if (senderInfo != null) {
			this.senderUUID = senderInfo.getProfile().id();
		}
		this.messagePattern = ChatSpamPatterns.extractMessagePattern(message.getString());
	}

	// ----- convenience accessors (обратная совместимость с рендером) -----

	public boolean isWhisper()      { return type == MessageType.WHISPER; }
	public boolean isAchievement()  { return type == MessageType.ACHIEVEMENT; }
	public boolean isChallenge()    { return type == MessageType.CHALLENGE; }
	public boolean isError()        { return type == MessageType.ERROR; }
	public boolean isSleepMessage() { return type == MessageType.SLEEP; }
	public boolean isJoinMessage()  { return type == MessageType.JOIN_LEAVE; }
	public boolean isScreenshot()   { return type == MessageType.SCREENSHOT; }
	public boolean isSystemMessage(){ return type.isSystem(); }

	public void updateSenderInfo(PlayerInfo newInfo) {
		if (newInfo != null && (senderInfo == null || senderUUID == null || senderUUID.equals(newInfo.getProfile().id()))) {
			this.senderInfo = newInfo;
			if (senderUUID == null) {
				this.senderUUID = newInfo.getProfile().id();
			}
		}
	}

}
