package com.ddev14.kkschat.chat;

import net.minecraft.network.chat.Component;

/**
 * Прямоугольник сообщения на экране (хиты для кликов и tooltip).
 */
public final class MessageBounds {
	public int x;
	public int y;
	public int width;
	public int height;
	public int historyIndex;
	public int textX;
	public int textY;
	public int maxTextWidth;
	public Component component;

	public MessageBounds(int x, int y, int width, int height, int historyIndex) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.historyIndex = historyIndex;
	}

	public MessageBounds(int x, int y, int width, int height, int historyIndex,
			int textX, int textY, int maxTextWidth, Component component) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.historyIndex = historyIndex;
		this.textX = textX;
		this.textY = textY;
		this.maxTextWidth = maxTextWidth;
		this.component = component;
	}

	public boolean contains(int mouseX, int mouseY) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}
}
