package com.ddev14.kkschat.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

/**
 * Поиск {@link PlayerInfo} по имени (как в Chat Heads / ванильный таб).
 */
public final class PlayerInfoLookup {
	private PlayerInfoLookup() {}

	public static PlayerInfo getPlayerInfoByName(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return null;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null) {
			return null;
		}

		String cleanName = playerName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim();

		PlayerInfo info = connection.getPlayerInfo(playerName);
		if (info != null) {
			return info;
		}

		if (!cleanName.equals(playerName)) {
			info = connection.getPlayerInfo(cleanName);
			if (info != null) {
				return info;
			}
		}

		for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
			String profileName = playerInfo.getProfile().name();
			if (profileName != null) {
				String cleanProfileName = profileName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim();
				if (profileName.equalsIgnoreCase(playerName) ||
						profileName.equalsIgnoreCase(cleanName) ||
						cleanProfileName.equalsIgnoreCase(cleanName)) {
					return playerInfo;
				}
			}

			if (playerInfo.getTabListDisplayName() != null) {
				String displayName = playerInfo.getTabListDisplayName().getString();
				String cleanDisplayName = displayName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim();
				if (displayName.equalsIgnoreCase(playerName) ||
						displayName.equalsIgnoreCase(cleanName) ||
						cleanDisplayName.equalsIgnoreCase(cleanName)) {
					return playerInfo;
				}
			}
		}

		ClientLevel level = minecraft.level;
		if (level != null) {
			for (Player player : level.players()) {
				String playerDisplayName = player.getDisplayName().getString();
				String playerNameStr = player.getName().getString();

				if (playerNameStr.equalsIgnoreCase(playerName) ||
						playerNameStr.equalsIgnoreCase(cleanName) ||
						playerDisplayName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim().equalsIgnoreCase(cleanName)) {
					return connection.getPlayerInfo(player.getUUID());
				}
			}
		}

		return null;
	}
}
