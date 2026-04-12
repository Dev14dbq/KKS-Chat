package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatDisplayTiming;
import com.ddev14.kkschat.chat.ChatLayout;
import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageBounds;
import com.ddev14.kkschat.render.ChatMessageBoxPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Отрисовка истории в открытом чате и оверлея при закрытом.
 * Поддерживает 6 позиций чата (центр/лево/право × верх/низ).
 */
public final class ChatHudSceneRenderer {
	private ChatHudSceneRenderer() {}

	public static void renderClosedOverlay(KksChatHud hud, GuiGraphicsExtractor guiGraphics, Minecraft minecraft) {
		if (hud.messageHistory.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		Font font = minecraft.font;
		int chatPosition = hud.getChatPosition();
		boolean topAnchor = ChatLayout.isTopPosition(chatPosition);
		long displayTimeMs = hud.displayTimeSeconds * 1000L;

		int currentY = topAnchor ? ChatLayout.TOP_OFFSET : screenHeight - ChatLayout.BOTTOM_OFFSET;
		int spacing = 2;

		for (int i = hud.messageHistory.size() - 1; i >= 0; i--) {
			ChatMessageEntry entry = hud.messageHistory.get(i);
			if (entry == null || entry.message == null) continue;

			float alpha = ChatDisplayTiming.alphaForDisplayDuration(entry, now, hud.displayTimeSeconds);
			if (alpha < 0f) break;

		int messageHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY,
				entry, alpha, hud.backgroundOpacity, hud.messageBounds, chatPosition, topAnchor, false, Integer.MIN_VALUE);

			currentY = topAnchor
					? currentY + messageHeight + spacing
					: currentY - messageHeight - spacing;

			boolean outOfBounds = topAnchor
					? currentY > screenHeight - 20
					: currentY < 20;
			if (outOfBounds) break;
		}

		if (hud.messageHistory.isEmpty()
				|| (now - hud.messageHistory.get(hud.messageHistory.size() - 1).timestamp > displayTimeMs)) {
		hud.messageComponent = null;
		hud.senderInfo = null;
		}
	}

	public static void renderOpenHistory(KksChatHud hud, GuiGraphicsExtractor guiGraphics, Minecraft minecraft) {
		if (hud.messageHistory.isEmpty()) {
			return;
		}

		hud.messageBounds.clear();

		Font font = minecraft.font;
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int chatPosition = hud.getChatPosition();
		boolean topAnchor = ChatLayout.isTopPosition(chatPosition);

		int startIndex = Math.max(0, hud.messageHistory.size() - hud.scrollOffset - hud.maxVisibleMessages);
		int endIndex = hud.messageHistory.size() - hud.scrollOffset;

		if (startIndex >= endIndex || startIndex < 0) {
			return;
		}

		int currentY = topAnchor ? ChatLayout.TOP_OFFSET : screenHeight - ChatLayout.BOTTOM_OFFSET;
		int spacing = 2;

		// Заголовок "ещё N сообщений"
		if (startIndex > 0 && !hud.collapsedMessagesExpanded) {
			ChatMessageEntry collapsedEntry = createCollapsedEntry(startIndex);
			if (collapsedEntry != null) {
				int messageHeight = renderEntry(guiGraphics, font, screenWidth, currentY,
						collapsedEntry, 1.0f, hud, chatPosition, topAnchor, -1);
				currentY = advance(currentY, messageHeight, spacing, topAnchor);
			}
		} else if (startIndex > 0 && hud.collapsedMessagesExpanded) {
			for (int j = startIndex - 1; j >= 0; j--) {
				ChatMessageEntry msg = hud.messageHistory.get(j);
				if (msg == null || msg.message == null) continue;
				if (isOutOfBounds(currentY, screenHeight, topAnchor)) break;
				int msgHeight = renderEntry(guiGraphics, font, screenWidth, currentY,
						msg, 1.0f, hud, chatPosition, topAnchor, j);
				currentY = advance(currentY, msgHeight, spacing, topAnchor);
			}
		}

		for (int i = endIndex - 1; i >= startIndex; i--) {
			if (isOutOfBounds(currentY, screenHeight, topAnchor)) break;
			ChatMessageEntry entry = hud.messageHistory.get(i);
			if (entry == null || entry.message == null) continue;

			if (entry.isExpanded && entry.repeatCount > 1
					&& entry.expandedMessages != null && !entry.expandedMessages.isEmpty()) {
				for (int repeat = 0; repeat < entry.expandedMessages.size(); repeat++) {
					if (isOutOfBounds(currentY, screenHeight, topAnchor)) break;
					ChatMessageEntry sub = entry.expandedMessages.get(repeat);
					if (sub == null || sub.message == null) continue;
					int h = renderEntry(guiGraphics, font, screenWidth, currentY,
							sub, 1.0f, hud, chatPosition, topAnchor, i * 1000 + repeat);
					currentY = advance(currentY, h, spacing, topAnchor);
				}
			} else if (entry.isExpanded && entry.repeatCount > 1) {
				for (int repeat = 0; repeat < entry.repeatCount; repeat++) {
					if (isOutOfBounds(currentY, screenHeight, topAnchor)) break;
					int h = renderEntry(guiGraphics, font, screenWidth, currentY,
							entry, 1.0f, hud, chatPosition, topAnchor, i * 1000 + repeat);
					currentY = advance(currentY, h, spacing, topAnchor);
				}
			} else {
				int h = renderEntry(guiGraphics, font, screenWidth, currentY,
						entry, 1.0f, hud, chatPosition, topAnchor, i);
				currentY = advance(currentY, h, spacing, topAnchor);
			}
		}
	}

	private static int renderEntry(GuiGraphicsExtractor g, Font font, int screenWidth, int currentY,
			ChatMessageEntry entry, float alpha, KksChatHud hud, int chatPosition, boolean topAnchor,
			int boundsKey) {
		// chatOpen=true: чат открыт, включаем hover-tooltips через ActiveTextCollector
		return ChatMessageBoxPainter.renderSingleMessage(g, font, screenWidth, currentY,
				entry, alpha, hud.backgroundOpacity, hud.messageBounds, chatPosition, topAnchor, true, boundsKey);
	}

	private static int advance(int currentY, int height, int spacing, boolean topAnchor) {
		return topAnchor ? currentY + height + spacing : currentY - height - spacing;
	}

	private static boolean isOutOfBounds(int currentY, int screenHeight, boolean topAnchor) {
		return topAnchor ? currentY > screenHeight - 20 : currentY < 0;
	}

	private static ChatMessageEntry createCollapsedEntry(int collapsedCount) {
		if (collapsedCount <= 0) return null;
		MutableComponent msg = Component.translatable("kkschat.chat.more_messages", collapsedCount)
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
		ChatMessageEntry entry = new ChatMessageEntry(msg, null, null, com.ddev14.kkschat.chat.MessageType.SYSTEM, null);
		entry.isCollapsed = true;
		entry.collapsedCount = collapsedCount;
		return entry;
	}
}
