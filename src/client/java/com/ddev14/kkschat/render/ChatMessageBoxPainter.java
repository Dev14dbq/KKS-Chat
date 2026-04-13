package com.ddev14.kkschat.render;

import com.ddev14.kkschat.KksChatHud;
import com.ddev14.kkschat.KksChatModClient;
import com.ddev14.kkschat.chat.ChatLayout;
import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageBounds;
import com.ddev14.kkschat.chat.MessageType;
import com.ddev14.kkschat.chat.RuleEngine;
import net.minecraft.world.item.Items;
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
	 * @param offsetX   дополнительный сдвиг по X (анимация)
	 * @param offsetY   дополнительный сдвиг по Y (анимация)
	 */
	public static int renderSingleMessage(GuiGraphicsExtractor guiGraphics, Font font, int screenWidth, int startY,
			ChatMessageEntry entry, float alpha, float backgroundOpacity,
			Map<Integer, MessageBounds> messageBounds, int chatPosition, boolean topAnchor,
			boolean chatOpen, int boundsKey, int offsetX, int offsetY) {
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
		int x = calculateX(screenWidth, boxWidth, chatPosition) + offsetX;

		int textAreaStartX = x + leftNonText;
		int maxTextWidth = logicalTextWidth;

		List<FormattedCharSequence> lines = font.split(component, maxTextWidth);
		if (lines.isEmpty()) {
			return 0;
		}

		int textHeight = font.lineHeight * lines.size();
		int contentHeight = isJoinMessage ? textHeight : Math.max(ChatLayout.AVATAR_SIZE, textHeight);
		int boxHeight = contentHeight + ChatLayout.VERTICAL_PADDING * 2;
		int y = (topAnchor ? startY : startY - boxHeight) + offsetY;

		int baseAlpha = (int) (backgroundOpacity * 255.0f);
		int animatedAlpha = (int) (baseAlpha * alpha);

		// Background color: config map → type default
		KksChatHud hudForBg = KksChatModClient.getHud();
		int bgBase = resolveBgColor(entry.type, hudForBg);
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
				// Icon: rule override takes priority over type-default config icon
				KksChatHud hud = KksChatModClient.getHud();
				if (entry.iconOverride != null) {
					ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
							entry.iconOverride, new net.minecraft.world.item.ItemStack(Items.STICK));
				} else switch (entry.type) {
				case CHALLENGE     -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconChallenge()    : null, new net.minecraft.world.item.ItemStack(Items.NETHERITE_INGOT));
				case ACHIEVEMENT   -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconAchievement()  : null, new net.minecraft.world.item.ItemStack(Items.EMERALD));
				case ERROR         -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconError()        : null, new net.minecraft.world.item.ItemStack(Items.BARRIER));
				case SLEEP         -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconSleep()        : null, new net.minecraft.world.item.ItemStack(Items.RED_BED));
				case SCREENSHOT    -> ChatHudAvatarRenderer.renderCameraIcon(guiGraphics, avatarX, avatarY, alpha);
				case COMMAND_BLOCK -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconCommandBlock() : null, new net.minecraft.world.item.ItemStack(Items.COMMAND_BLOCK));
				case WHISPER       -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconWhisper()      : null, new net.minecraft.world.item.ItemStack(Items.PAPER));
				case JOIN_LEAVE    -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
						hud != null ? hud.getIconJoinLeave()    : null, new net.minecraft.world.item.ItemStack(Items.OAK_DOOR));
			default            -> ChatHudAvatarRenderer.renderIconById(guiGraphics, avatarX, avatarY, alpha,
					hud != null ? hud.getIconSystem()       : null, new net.minecraft.world.item.ItemStack(Items.STICK));
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

	/**
	 * Resolves the background RGB color for a message type.
	 * Checks the config map first; falls back to built-in defaults.
	 */
	private static int resolveBgColor(MessageType type, KksChatHud hud) {
		if (hud != null && hud.bgColors != null) {
			String hex = hud.bgColors.get(type.name());
			if (hex != null) {
				Integer parsed = RuleEngine.parseHexColor(hex);
				if (parsed != null) return parsed;
			}
		}
		return switch (type) {
			case CHALLENGE   -> 0x1A0028;
			case ACHIEVEMENT -> 0x001A04;
			case ERROR       -> 0x1A0000;
			default          -> 0x000000;
		};
	}
}
