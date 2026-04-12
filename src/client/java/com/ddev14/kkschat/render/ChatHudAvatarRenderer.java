package com.ddev14.kkschat.render;

import com.ddev14.kkschat.chat.ChatLayout;
import com.ddev14.kkschat.skin.MojangSkinCache;
import com.ddev14.kkschat.skin.PlayerInfoLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/**
 * Головы игроков и декоративные иконки слева от строки чата.
 */
public final class ChatHudAvatarRenderer {
	private ChatHudAvatarRenderer() {}

	private static final ItemStack BARRIER_STACK = new ItemStack(Items.BARRIER);
	private static final ItemStack EMERALD_STACK = new ItemStack(Items.EMERALD);
	private static final ItemStack NETHERITE_STACK = new ItemStack(Items.NETHERITE_INGOT);
	private static final ItemStack CLOCK_STACK = new ItemStack(Items.CLOCK);
	private static final ItemStack STICK_STACK = new ItemStack(Items.STICK);
	private static final ItemStack BED_STACK = new ItemStack(Items.RED_BED);

	public static void renderPlayerHead(GuiGraphicsExtractor guiGraphics, int x, int y, PlayerInfo owner, float opacity) {
		if (owner == null) {
			return;
		}

		try {
			Identifier skinLocation = owner.getSkin().body().texturePath();
			int color = ARGB.white(opacity);

			ClientLevel level = Minecraft.getInstance().level;
			Player player = level != null ? level.getPlayerByUUID(owner.getProfile().id()) : null;
			boolean upsideDown = player != null && AvatarRenderer.isPlayerUpsideDown(player);

			boolean showHat = owner.showHat();

			int yOffset = (upsideDown ? 8 : 0);
			int yDirection = (upsideDown ? -1 : 1);

			int av = ChatLayout.AVATAR_SIZE;
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinLocation, x, y,
					8.0f, 8 + yOffset,
					av, av,
					8, yDirection * 8,
					64, 64, color);

			if (showHat) {
				guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinLocation, x, y,
						40.0f, 8 + yOffset,
						av, av,
						8, yDirection * 8,
						64, 64, color);
			}
		} catch (Exception ignored) {
		}
	}

	public static void renderPlayerHeadByName(GuiGraphicsExtractor guiGraphics, int x, int y, String playerName, float opacity) {
		if (playerName == null || playerName.isEmpty()) {
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
			return;
		}

		PlayerInfo info = PlayerInfoLookup.getPlayerInfoByName(playerName);
		if (info != null) {
			renderPlayerHead(guiGraphics, x, y, info, opacity);
			return;
		}

		if (MojangSkinCache.getCachedSkinUrl(playerName) != null) {
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
		} else {
			MojangSkinCache.requestSkinLoad(playerName);
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
		}
	}

	public static void renderDefaultSteveHead(GuiGraphicsExtractor guiGraphics, int x, int y, float opacity) {
		Identifier steveSkin = Identifier.fromNamespaceAndPath("kks-chat", "steve.png");
		int color = ARGB.white(opacity);
		int av = ChatLayout.AVATAR_SIZE;

		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, steveSkin, x, y,
				8.0f, 8.0f,
				av, av,
				8, 8,
				64, 64, color);

		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, steveSkin, x, y,
				40.0f, 8.0f,
				av, av,
				8, 8,
				64, 64, color);
	}

	public static void renderBarrierIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = ARGB.white(alpha);
		guiGraphics.item(BARRIER_STACK, x, iconY, color);
	}

	public static void renderEmeraldIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = ARGB.white(alpha);
		guiGraphics.item(EMERALD_STACK, x, iconY, color);
	}

	public static void renderNetheriteIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = ARGB.white(alpha);
		guiGraphics.item(NETHERITE_STACK, x, iconY, color);
	}

	public static void renderClockIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = ARGB.white(alpha);
		guiGraphics.item(CLOCK_STACK, x, iconY, color);
	}

	public static void renderStickIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = ARGB.white(alpha);
		guiGraphics.item(STICK_STACK, x, iconY, color);
	}

	public static void renderBedIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		int iconY = y - 1;
		int color = ARGB.white(alpha);
		guiGraphics.item(BED_STACK, x, iconY, color);
	}

	public static void renderCameraIcon(GuiGraphicsExtractor guiGraphics, int x, int y, float alpha) {
		Identifier cameraTexture = Identifier.fromNamespaceAndPath("kks-chat", "camera.png");
		int color = ARGB.white(alpha);
		int av = ChatLayout.AVATAR_SIZE;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, cameraTexture, x, y,
				0.0f, 0.0f,
				av, av,
				av, av,
				av, av, color);
	}
}
