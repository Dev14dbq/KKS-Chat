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
						if (entry.isAchievement && entry.message != null) {
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
									net.minecraft.client.StringSplitter splitter = font.getSplitter();
									Style style = splitter.componentStyleAtWidth(line, relativeX);

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

	public static void handleClickEvent(ClickEvent clickEvent) {
		if (clickEvent == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClickEvent.Action action = null;
		String value = null;

		try {
			try {
				java.lang.reflect.Method actionMethod = clickEvent.getClass().getMethod("action");
				action = (ClickEvent.Action) actionMethod.invoke(clickEvent);
			} catch (Exception e1) {
				try {
					java.lang.reflect.Method getActionMethod = clickEvent.getClass().getMethod("getAction");
					action = (ClickEvent.Action) getActionMethod.invoke(clickEvent);
				} catch (Exception e2) {
					try {
						java.lang.reflect.Field actionField = clickEvent.getClass().getField("action");
						action = (ClickEvent.Action) actionField.get(clickEvent);
					} catch (Exception ignored) {
					}
				}
			}

			try {
				java.lang.reflect.Method valueMethod = clickEvent.getClass().getMethod("value");
				value = (String) valueMethod.invoke(clickEvent);
			} catch (Exception e1) {
				try {
					java.lang.reflect.Method getValueMethod = clickEvent.getClass().getMethod("getValue");
					value = (String) getValueMethod.invoke(clickEvent);
				} catch (Exception e2) {
					try {
						java.lang.reflect.Field valueField = clickEvent.getClass().getField("value");
						value = (String) valueField.get(clickEvent);
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}

		if (action == null || value == null || value.isEmpty()) {
			return;
		}

		switch (action) {
			case OPEN_URL -> {
				if (minecraft.options.chatLinks().get()) {
					try {
						java.net.URI uri = new java.net.URI(value);
						java.awt.Desktop.getDesktop().browse(uri);
					} catch (Exception e) {
						minecraft.keyboardHandler.setClipboard(value);
					}
				}
			}
			case OPEN_FILE -> {
				try {
					java.io.File file = new java.io.File(value);
					if (file.exists()) {
						java.awt.Desktop.getDesktop().open(file);
					}
				} catch (Exception ignored) {
				}
			}
			case RUN_COMMAND, SUGGEST_COMMAND -> {
				if (minecraft.player != null) {
					if (action == ClickEvent.Action.RUN_COMMAND) {
						minecraft.player.connection.sendCommand(value);
					} else {
						if (minecraft.screen instanceof ChatScreen) {
							((ChatScreen) minecraft.screen).handleChatInput(value, true);
						}
					}
				}
			}
			case COPY_TO_CLIPBOARD -> minecraft.keyboardHandler.setClipboard(value);
			default -> {
			}
		}
	}
}
