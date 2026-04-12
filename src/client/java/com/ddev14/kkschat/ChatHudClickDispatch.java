package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.MessageBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;

/**
 * Клики по ссылкам в чате и разворачивание повторов.
 */
public final class ChatHudClickDispatch {
	private ChatHudClickDispatch() {}

	public static boolean handleMessageClick(KksChatHud hud, double mouseX, double mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof ChatScreen)) {
			return false;
		}

		int mx = (int) mouseX;
		int my = (int) mouseY;

		MessageBounds collapsedBounds = hud.messageBounds.get(-1);
		if (collapsedBounds != null && collapsedBounds.contains(mx, my)) {
			hud.collapsedMessagesExpanded = !hud.collapsedMessagesExpanded;
			hud.messageBounds.clear();
			return true;
		}

		for (Map.Entry<Integer, MessageBounds> boundsEntry : hud.messageBounds.entrySet()) {
			int index = boundsEntry.getKey();
			MessageBounds bounds = boundsEntry.getValue();

			if (index == -1) {
				continue;
			}

			if (index < 1000 && bounds.contains(mx, my)) {
				int historyIndex = bounds.historyIndex;
				if (historyIndex >= 0 && historyIndex < hud.messageHistory.size()) {
					ChatMessageEntry entry = hud.messageHistory.get(historyIndex);
					if (entry != null) {
						if (entry.isAchievement() && entry.message != null) {
							hud.shouldOpenAdvancements = true;
							return true;
						}
						if (entry.repeatCount > 1) {
							entry.isExpanded = !entry.isExpanded;
							hud.messageBounds.clear();
							return true;
						}

						if (bounds.component != null && bounds.textX > 0 && bounds.textY > 0) {
							if (mx >= bounds.textX && mx < bounds.textX + bounds.maxTextWidth
									&& my >= bounds.textY && my < bounds.textY + bounds.height) {
								Font font = minecraft.font;
								int relativeX = mx - bounds.textX;
								int relativeY = my - bounds.textY;

							List<FormattedCharSequence> lines = font.split(bounds.component, bounds.maxTextWidth);
							int lineIndex = relativeY / font.lineHeight;
							if (lineIndex >= 0 && lineIndex < lines.size()) {
								FormattedCharSequence line = lines.get(lineIndex);
								Style style = styleAtWidth(font, line, relativeX);

								if (style != null) {
									ClickEvent clickEvent = style.getClickEvent();
									if (clickEvent != null) {
										handleClickEvent(clickEvent);
										return true;
									}
								}
							}
							}
						}
					}
				}
			}
		}

		return false;
	}

	private static Style styleAtWidth(Font font, FormattedCharSequence line, int targetX) {
		final Style[] result = {null};
		final int[] px = {0};
		line.accept((index, style, codePoint) -> {
			int w = font.width(String.valueOf(Character.toChars(codePoint)));
			px[0] += w;
			if (px[0] > targetX) {
				result[0] = style;
				return false;
			}
			return true;
		});
		return result[0];
	}

	/**
	 * В MC 26.1 ClickEvent — интерфейс с конкретными record-реализациями.
	 * Каждый тип несёт свои данные через типизированные accessor-методы.
	 */
	public static void handleClickEvent(ClickEvent clickEvent) {
		if (clickEvent == null) return;

		Minecraft minecraft = Minecraft.getInstance();

		if (clickEvent instanceof ClickEvent.RunCommand run) {
			if (minecraft.player != null) {
				String cmd = run.command();
				if (cmd.startsWith("/")) cmd = cmd.substring(1);
				minecraft.player.connection.sendCommand(cmd);
			}
		} else if (clickEvent instanceof ClickEvent.SuggestCommand suggest) {
			if (minecraft.screen instanceof ChatScreen chatScreen) {
				String cmd = suggest.command();
				if (!cmd.startsWith("/")) cmd = "/" + cmd;
				chatScreen.handleChatInput(cmd, true);
			}
		} else if (clickEvent instanceof ClickEvent.OpenUrl openUrl) {
			if (minecraft.options.chatLinks().get()) {
				try {
					java.awt.Desktop.getDesktop().browse(openUrl.uri());
				} catch (Exception e) {
					minecraft.keyboardHandler.setClipboard(openUrl.uri().toString());
				}
			}
		} else if (clickEvent instanceof ClickEvent.OpenFile openFile) {
			try {
				java.io.File file = openFile.file();
				if (file.exists()) {
					java.awt.Desktop.getDesktop().open(file);
				}
			} catch (Exception ignored) {
			}
		} else if (clickEvent instanceof ClickEvent.CopyToClipboard copy) {
			minecraft.keyboardHandler.setClipboard(copy.value());
		}
	}
}
