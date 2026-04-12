package com.ddev14.kkschat.render;

import com.ddev14.kkschat.chat.ChatLayout;
import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageBounds;
import com.ddev14.kkschat.chat.MessageType;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
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
	 * @param startY    при topAnchor=false — нижняя граница блока; при topAnchor=true — верхняя
	 * @param topAnchor true для позиций «сверху», false для позиций «снизу»
	 * @param chatOpen  true — чат открыт (включаем hover-tooltip через ActiveTextCollector)
	 * @param boundsKey ключ для записи MessageBounds в карту; Integer.MIN_VALUE — не записывать
	 */
	public static int renderSingleMessage(GuiGraphicsExtractor guiGraphics, Font font, int screenWidth, int startY,
			ChatMessageEntry entry, float alpha, float backgroundOpacity,
			Map<Integer, MessageBounds> messageBounds, int chatPosition, boolean topAnchor,
			boolean chatOpen, int boundsKey) {
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

		boolean isWhisper = entry.isWhisper();
		boolean isJoinMessage = entry.isJoinMessage();

		boolean isShortWhisper = isWhisper
				&& fullTextWidth < 150
				&& component.getString().split("\n").length == 1;

		int avatarAreaWidth;
		if (isJoinMessage) {
			avatarAreaWidth = 0;
		} else if (isShortWhisper) {
			avatarAreaWidth = ChatLayout.AVATAR_SIZE + 3 + 3 + 3 + ChatLayout.AVATAR_SIZE;
		} else {
			avatarAreaWidth = ChatLayout.AVATAR_SIZE;
		}

		int leftNonText = isJoinMessage ? ChatLayout.HORIZONTAL_PADDING : (ChatLayout.HORIZONTAL_PADDING + avatarAreaWidth + 7);
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
		int contentHeight = isJoinMessage ? textHeight : Math.max(ChatLayout.AVATAR_SIZE, textHeight);
		int boxHeight = contentHeight + ChatLayout.VERTICAL_PADDING * 2;
		int y = topAnchor ? startY : startY - boxHeight;

		int baseAlpha = (int) (backgroundOpacity * 255.0f);
		int animatedAlpha = (int) (baseAlpha * alpha);

		// Фон зависит от типа сообщения
		int bgBase = switch (entry.type) {
			case CHALLENGE   -> 0x1A0028;
			case ACHIEVEMENT -> 0x001A04;
			default          -> 0x000000;
		};
		guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, (animatedAlpha << 24) | bgBase);

		// Цветная вертикальная полоска-акцент для достижений/испытаний
		if (entry.type == MessageType.CHALLENGE || entry.type == MessageType.ACHIEVEMENT) {
			int accentRgb = entry.type == MessageType.CHALLENGE ? 0xAA00CC : 0x00AA22;
			guiGraphics.fill(x, y, x + 3, y + boxHeight, (animatedAlpha << 24) | accentRgb);
		}

		int avatarX = x + ChatLayout.HORIZONTAL_PADDING;
		int avatarY = y + (boxHeight - ChatLayout.AVATAR_SIZE) / 2;

		if (entry.isCollapsed) {
			ChatHudAvatarRenderer.renderClockIcon(guiGraphics, avatarX, avatarY, alpha);
		} else if (!isJoinMessage) {
			if (entry.senderInfo != null) {
				ChatHudAvatarRenderer.renderPlayerHead(guiGraphics, avatarX, avatarY, entry.senderInfo, alpha);
			} else if (entry.senderName != null) {
				ChatHudAvatarRenderer.renderPlayerHeadByName(guiGraphics, avatarX, avatarY, entry.senderName, alpha);
			} else {
				// Иконка по типу сообщения
				switch (entry.type) {
					case CHALLENGE   -> ChatHudAvatarRenderer.renderNetheriteIcon(guiGraphics, avatarX, avatarY, alpha);
					case ACHIEVEMENT -> ChatHudAvatarRenderer.renderEmeraldIcon(guiGraphics, avatarX, avatarY, alpha);
					case ERROR       -> ChatHudAvatarRenderer.renderBarrierIcon(guiGraphics, avatarX, avatarY, alpha);
					case SLEEP       -> ChatHudAvatarRenderer.renderBedIcon(guiGraphics, avatarX, avatarY, alpha);
					case SCREENSHOT  -> ChatHudAvatarRenderer.renderCameraIcon(guiGraphics, avatarX, avatarY, alpha);
					case SYSTEM      -> ChatHudAvatarRenderer.renderStickIcon(guiGraphics, avatarX, avatarY, alpha);
					default          -> ChatHudAvatarRenderer.renderDefaultSteveHead(guiGraphics, avatarX, avatarY, alpha);
				}
			}
		}

		int textX = textAreaStartX;
		int totalTextHeight = font.lineHeight * lines.size();
		int avatarCenterY = avatarY + ChatLayout.AVATAR_SIZE / 2;
		int textY = avatarCenterY - totalTextHeight / 2;

		if (chatOpen) {
			// Открытый чат: используем ActiveTextCollector с TOOLTIP_AND_CURSOR,
			// чтобы движок автоматически показывал hover-эффекты (SHOW_TEXT, SHOW_ITEM,
			// SHOW_ENTITY) и менял курсор над кликабельным текстом.
			ActiveTextCollector renderer = guiGraphics.textRenderer(
					GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR);
			ActiveTextCollector.Parameters params = renderer.defaultParameters().withOpacity(alpha);
			for (int i = 0; i < lines.size(); i++) {
				renderer.accept(TextAlignment.LEFT, textX, textY + i * font.lineHeight,
						params, lines.get(i));
			}
		} else {
			// Закрытый HUD-оверлей: просто рисуем текст с alpha-fade, hover не нужен.
			int textAlpha = (int) (255 * alpha);
			int textBaseColor = switch (entry.type) {
				case CHALLENGE   -> 0xFF55FF;
				case ACHIEVEMENT -> 0x55FF55;
				default          -> 0xFFFFFF;
			};
			int textColorWithAlpha = (textAlpha << 24) | textBaseColor;
			for (int i = 0; i < lines.size(); i++) {
				guiGraphics.text(font, lines.get(i), textX, textY + i * font.lineHeight,
						textColorWithAlpha, false);
			}
		}

		if (boundsKey != Integer.MIN_VALUE) {
			messageBounds.put(boundsKey, new MessageBounds(
					x, y, boxWidth, boxHeight, boundsKey,
					textX, textY, maxTextWidth, entry.message));
		}

		return boxHeight;
	}
}
