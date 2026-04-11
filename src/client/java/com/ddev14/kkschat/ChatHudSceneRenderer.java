package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatDisplayTiming;
import com.ddev14.kkschat.chat.ChatLayout;
import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageBounds;
import com.ddev14.kkschat.render.ChatMessageBoxPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Отрисовка истории в открытом чате и оверлея при закрытом.
 */
public final class ChatHudSceneRenderer {
	private ChatHudSceneRenderer() {}

	public static void renderClosedOverlay(KksChatHud hud, GuiGraphics guiGraphics, Minecraft minecraft) {
		if (hud.messageHistory.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		Font font = minecraft.font;

		int currentY = screenHeight - ChatLayout.BOTTOM_OFFSET;
		int spacing = 2;
		int minY = 20;

		long displayTimeMs = hud.displayTimeSeconds * 1000L;

		for (int i = hud.messageHistory.size() - 1; i >= 0 && currentY > minY; i--) {
			ChatMessageEntry entry = hud.messageHistory.get(i);
			if (entry == null || entry.message == null) {
				continue;
			}

			float alpha = ChatDisplayTiming.alphaForDisplayDuration(entry, now, hud.displayTimeSeconds);
			if (alpha < 0f) {
				break;
			}

			int messageHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY, entry, alpha,
					hud.backgroundOpacity, hud.messageBounds);

			currentY -= messageHeight + spacing;

			if (currentY <= minY) {
				break;
			}
		}

		if (hud.messageHistory.isEmpty()
				|| (now - hud.messageHistory.get(hud.messageHistory.size() - 1).timestamp > displayTimeMs)) {
			hud.messageComponent = null;
			hud.senderInfo = null;
			hud.systemMessage = false;
		}
	}

	public static void renderOpenHistory(KksChatHud hud, GuiGraphics guiGraphics, Minecraft minecraft) {
		if (hud.messageHistory.isEmpty()) {
			return;
		}

		hud.messageBounds.clear();

		Font font = minecraft.font;
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int startIndex = Math.max(0, hud.messageHistory.size() - hud.scrollOffset - hud.maxVisibleMessages);
		int endIndex = hud.messageHistory.size() - hud.scrollOffset;

		if (startIndex >= endIndex || startIndex < 0) {
			return;
		}

		int currentY = screenHeight - ChatLayout.BOTTOM_OFFSET;
		int spacing = 2;

		if (startIndex > 0 && !hud.collapsedMessagesExpanded) {
			ChatMessageEntry collapsedEntry = createCollapsedEntry(startIndex);
			if (collapsedEntry != null) {
				int messageHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY, collapsedEntry, 1.0f,
						hud.backgroundOpacity, hud.messageBounds);

				int boxWidth = ChatMessageBoxPainter.calculateMessageWidth(font, collapsedEntry.message);
				int x = (screenWidth - boxWidth) / 2;
				hud.messageBounds.put(-1, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, -1));

				currentY -= messageHeight + spacing;
			}
		} else if (startIndex > 0 && hud.collapsedMessagesExpanded) {
			for (int j = startIndex - 1; j >= 0; j--) {
				ChatMessageEntry collapsedMsg = hud.messageHistory.get(j);
				if (collapsedMsg == null || collapsedMsg.message == null) {
					continue;
				}
				if (currentY <= 0) {
					break;
				}
				int msgHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY, collapsedMsg, 1.0f,
						hud.backgroundOpacity, hud.messageBounds);
				int msgWidth = ChatMessageBoxPainter.calculateMessageWidth(font, collapsedMsg.message);
				int msgX = (screenWidth - msgWidth) / 2;
				hud.messageBounds.put(j, new MessageBounds(msgX, currentY - msgHeight, msgWidth, msgHeight, j));
				currentY -= msgHeight + spacing;
			}
		}

		for (int i = endIndex - 1; i >= startIndex && currentY > 0; i--) {
			ChatMessageEntry entry = hud.messageHistory.get(i);
			if (entry == null || entry.message == null) {
				continue;
			}

			if (entry.isExpanded && entry.repeatCount > 1) {
				if (entry.expandedMessages != null && !entry.expandedMessages.isEmpty()) {
					for (int repeat = 0; repeat < entry.expandedMessages.size() && currentY > 0; repeat++) {
						ChatMessageEntry expandedEntry = entry.expandedMessages.get(repeat);
						if (expandedEntry != null && expandedEntry.message != null) {
							float lineAlpha = 1.0f;
							int messageHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY, expandedEntry, lineAlpha,
									hud.backgroundOpacity, hud.messageBounds);
							int boxWidth = ChatMessageBoxPainter.calculateMessageWidth(font, expandedEntry.message);
							int x = (screenWidth - boxWidth) / 2;
							hud.messageBounds.put(i * 1000 + repeat, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, i));
							currentY -= messageHeight + spacing;
						}
					}
				} else {
				for (int repeat = 0; repeat < entry.repeatCount && currentY > 0; repeat++) {
						int messageHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY, entry, 1.0f,
								hud.backgroundOpacity, hud.messageBounds);
						int boxWidth = ChatMessageBoxPainter.calculateMessageWidth(font, entry.message);
						int x = (screenWidth - boxWidth) / 2;
						hud.messageBounds.put(i * 1000 + repeat, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, i));
						currentY -= messageHeight + spacing;
					}
				}
			} else {
				int messageHeight = ChatMessageBoxPainter.renderSingleMessage(guiGraphics, font, screenWidth, currentY, entry, 1.0f,
						hud.backgroundOpacity, hud.messageBounds);
				int boxWidth = ChatMessageBoxPainter.calculateMessageWidth(font, entry.message);
				int x = (screenWidth - boxWidth) / 2;
				hud.messageBounds.put(i, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, i));
				currentY -= messageHeight + spacing;
			}
		}
	}

	private static ChatMessageEntry createCollapsedEntry(int collapsedCount) {
		if (collapsedCount <= 0) {
			return null;
		}
		String collapsedText = collapsedCount + " more messages";
		if (collapsedCount == 1) {
			collapsedText = "1 more message";
		}
		MutableComponent collapsedMessage = Component.literal(collapsedText)
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));

		ChatMessageEntry entry = new ChatMessageEntry(collapsedMessage, null, null, true, false, null);
		entry.isCollapsed = true;
		entry.collapsedCount = collapsedCount;
		return entry;
	}
}
