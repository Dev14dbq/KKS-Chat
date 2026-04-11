package com.ddev14.kkschat.render;

import com.ddev14.kkschat.chat.ChatLayout;
import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageBounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;

/**
 * Размеры блока сообщения, фон и текст.
 */
public final class ChatMessageBoxPainter {
	private ChatMessageBoxPainter() {}

	/**
	 * Вычисляет X-координату блока сообщения в зависимости от позиции чата.
	 * 0=центр-низ, 1=центр-верх, 2=лево-верх, 3=лево-низ, 4=право-верх, 5=право-низ
	 */
	public static int calculateX(int screenWidth, int boxWidth, int chatPosition) {
		return switch (chatPosition) {
			case 2, 3 -> ChatLayout.SIDE_MARGIN;
			case 4, 5 -> screenWidth - boxWidth - ChatLayout.SIDE_MARGIN;
			default -> (screenWidth - boxWidth) / 2;
		};
	}

	public static int calculateMessageWidth(Font font, Component component) {
		int fullTextWidth = font.width(component);
		int avatarAreaWidth = ChatLayout.AVATAR_SIZE;
		int leftNonText = ChatLayout.HORIZONTAL_PADDING + avatarAreaWidth + 7;
		int rightNonText = ChatLayout.HORIZONTAL_PADDING;
		int totalNonTextWidth = leftNonText + rightNonText;
		int logicalTextWidth = Math.min(ChatLayout.MAX_BOX_WIDTH - totalNonTextWidth, fullTextWidth);
		return logicalTextWidth + totalNonTextWidth;
	}

	/**
	 * @param startY  при topAnchor=false — нижняя граница блока; при topAnchor=true — верхняя
	 * @param topAnchor  true для позиций «сверху», false для позиций «снизу»
	 */
	public static int renderSingleMessage(GuiGraphics guiGraphics, Font font, int screenWidth, int startY,
			ChatMessageEntry entry, float alpha, float backgroundOpacity,
			Map<Integer, MessageBounds> messageBounds, int chatPosition, boolean topAnchor) {
		Component component = entry.message;
		if (component == null || component.getString().isEmpty()) {
			return 0;
		}

		if (entry.repeatCount > 1 && !entry.isExpanded) {
			MutableComponent withSuffix = component.copy();
			MutableComponent suffix = Component.literal(" x" + entry.repeatCount)
					.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
			withSuffix.append(suffix);
			component = withSuffix;
		}

		int fullTextWidth = font.width(component);

		boolean isWhisper = entry.isWhisper;

		boolean isShortWhisper = false;
		if (isWhisper) {
			int shortMessageThreshold = 150;
			isShortWhisper = fullTextWidth < shortMessageThreshold && component.getString().split("\n").length == 1;
		}

		int avatarAreaWidth;
		if (entry.isJoinMessage) {
			avatarAreaWidth = 0;
		} else if (isWhisper) {
			if (isShortWhisper) {
				avatarAreaWidth = ChatLayout.AVATAR_SIZE + 3 + 3 + 3 + ChatLayout.AVATAR_SIZE;
			} else {
				avatarAreaWidth = ChatLayout.AVATAR_SIZE;
			}
		} else {
			avatarAreaWidth = ChatLayout.AVATAR_SIZE;
		}

		int leftNonText = entry.isJoinMessage ? ChatLayout.HORIZONTAL_PADDING : (ChatLayout.HORIZONTAL_PADDING + avatarAreaWidth + 7);
		int rightNonText = ChatLayout.HORIZONTAL_PADDING;
		int totalNonTextWidth = leftNonText + rightNonText;

		int logicalTextWidth = Math.min(ChatLayout.MAX_BOX_WIDTH - totalNonTextWidth, fullTextWidth);

		int boxWidth = logicalTextWidth + totalNonTextWidth;
		int x = calculateX(screenWidth, boxWidth, chatPosition);

		int textAreaStartX = x + leftNonText;
		int maxTextWidth = logicalTextWidth;

		List<FormattedCharSequence> lines = font.split(component, maxTextWidth);
		if (lines.isEmpty()) {
			return 0;
		}

		int textHeight = font.lineHeight * lines.size();
		int contentHeight = entry.isJoinMessage ? textHeight : Math.max(ChatLayout.AVATAR_SIZE, textHeight);
		int boxHeight = contentHeight + ChatLayout.VERTICAL_PADDING * 2;
		int y = topAnchor ? startY : startY - boxHeight;

		int baseAlpha = (int) (backgroundOpacity * 255.0f);
		int animatedAlpha = (int) (baseAlpha * alpha);
		int backgroundColor = (animatedAlpha << 24) | 0x000000;
		guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, backgroundColor);

		int avatarX = x + ChatLayout.HORIZONTAL_PADDING;

		if (entry.isCollapsed) {
			int avatarY = y + (boxHeight - ChatLayout.AVATAR_SIZE) / 2;
			ChatHudAvatarRenderer.renderClockIcon(guiGraphics, avatarX, avatarY, alpha);
		} else {
			int avatarY = y + (boxHeight - ChatLayout.AVATAR_SIZE) / 2;
			if (entry.isJoinMessage) {
				// только текст
			} else if (entry.senderInfo != null) {
				ChatHudAvatarRenderer.renderPlayerHead(guiGraphics, avatarX, avatarY, entry.senderInfo, alpha);
			} else if (entry.senderName != null) {
				ChatHudAvatarRenderer.renderPlayerHeadByName(guiGraphics, avatarX, avatarY, entry.senderName, alpha);
			} else {
				if (entry.isChallenge) {
					ChatHudAvatarRenderer.renderNetheriteIcon(guiGraphics, avatarX, avatarY, alpha);
				} else if (entry.isAchievement) {
					ChatHudAvatarRenderer.renderEmeraldIcon(guiGraphics, avatarX, avatarY, alpha);
				} else if (entry.isError) {
					ChatHudAvatarRenderer.renderBarrierIcon(guiGraphics, avatarX, avatarY, alpha);
				} else if (entry.isSleepMessage) {
					ChatHudAvatarRenderer.renderBedIcon(guiGraphics, avatarX, avatarY, alpha);
				} else if (entry.isScreenshot) {
					ChatHudAvatarRenderer.renderCameraIcon(guiGraphics, avatarX, avatarY, alpha);
				} else if (entry.systemMessage) {
					ChatHudAvatarRenderer.renderStickIcon(guiGraphics, avatarX, avatarY, alpha);
				} else {
					ChatHudAvatarRenderer.renderDefaultSteveHead(guiGraphics, avatarX, avatarY, alpha);
				}
			}
		}

		int textX = textAreaStartX;
		int totalTextHeight = font.lineHeight * lines.size();
		int avatarCenterY = y + (boxHeight - ChatLayout.AVATAR_SIZE) / 2 + ChatLayout.AVATAR_SIZE / 2;
		int textY = avatarCenterY - totalTextHeight / 2;

		int textAlpha = (int) (255 * alpha);
		int textColorWithAlpha = (textAlpha << 24) | 0xFFFFFF;

		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence line = lines.get(i);
			int lineY = textY + i * font.lineHeight;
			guiGraphics.drawString(font, line, textX, lineY, textColorWithAlpha, false);
		}

		if (alpha >= 0.99f) {
			for (Map.Entry<Integer, MessageBounds> boundsEntry : messageBounds.entrySet()) {
				MessageBounds bounds = boundsEntry.getValue();
				if (Math.abs(bounds.x - x) < 5 && Math.abs(bounds.y - y) < 5 &&
						Math.abs(bounds.width - boxWidth) < 5 && Math.abs(bounds.height - boxHeight) < 5) {
					bounds.textX = textX;
					bounds.textY = textY;
					bounds.maxTextWidth = maxTextWidth;
					bounds.component = entry.message;
					break;
				}
			}
		}

		return boxHeight;
	}
}
