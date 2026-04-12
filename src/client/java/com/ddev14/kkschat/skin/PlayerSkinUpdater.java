package com.ddev14.kkschat.skin;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

/**
 * Догрузка {@link PlayerInfo} / скинов для недавних сообщений в истории.
 */
public final class PlayerSkinUpdater {
	private PlayerSkinUpdater() {}

	public static void updateMissingPlayerInfos(Minecraft minecraft, List<ChatMessageEntry> messageHistory) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || messageHistory.isEmpty()) {
			return;
		}

		int startIndex = Math.max(0, messageHistory.size() - 30);
		for (int i = startIndex; i < messageHistory.size(); i++) {
			ChatMessageEntry entry = messageHistory.get(i);
			if (entry == null) {
				continue;
			}

			if (entry.senderInfo == null) {
				if (entry.senderUUID != null) {
					PlayerInfo info = connection.getPlayerInfo(entry.senderUUID);
					if (info != null) {
						entry.updateSenderInfo(info);
						continue;
					}
				}

				if (entry.senderName != null && !entry.senderName.isEmpty()) {
					PlayerInfo info = PlayerInfoLookup.getPlayerInfoByName(entry.senderName);
					if (info != null) {
						entry.updateSenderInfo(info);
						if (entry.senderUUID == null && info.getProfile().id() != null) {
							entry.senderUUID = info.getProfile().id();
						}
					} else {
						MojangSkinCache.requestSkinLoad(entry.senderName);
					}
				}
			} else {
				if (entry.senderUUID != null) {
					PlayerInfo updatedInfo = connection.getPlayerInfo(entry.senderUUID);
					if (updatedInfo != null && updatedInfo != entry.senderInfo) {
						entry.updateSenderInfo(updatedInfo);
					}
				}
			}

			if (entry.isWhisper() && entry.receiverInfo == null) {
				if (minecraft.player != null) {
					PlayerInfo receiverInfo = connection.getPlayerInfo(minecraft.player.getUUID());
					if (receiverInfo != null) {
						entry.receiverInfo = receiverInfo;
					}
				}
			}
		}
	}
}
