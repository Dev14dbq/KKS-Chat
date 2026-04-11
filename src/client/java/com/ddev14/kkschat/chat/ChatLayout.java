package com.ddev14.kkschat.chat;

/**
 * Размеры и отступы HUD чата (единая точка правды).
 */
public final class ChatLayout {
	private ChatLayout() {}

	public static final int MAX_BOX_WIDTH = 260;
	public static final int AVATAR_SIZE = 14;
	public static final int HORIZONTAL_PADDING = 5;
	public static final int VERTICAL_PADDING = 6;
	public static final int BOTTOM_OFFSET = 40;
	/** Отступ от верхнего края экрана для позиций «сверху». */
	public static final int TOP_OFFSET = 10;
	/** Отступ от левого/правого края экрана. */
	public static final int SIDE_MARGIN = 8;

	/**
	 * Позиции чата:
	 * 0 = По центру (снизу)
	 * 1 = По центру сверху
	 * 2 = Слева сверху
	 * 3 = Слева снизу
	 * 4 = Справа сверху
	 * 5 = Справа снизу
	 */
	public static final int POSITION_COUNT = 6;

	public static boolean isTopPosition(int chatPosition) {
		return chatPosition == 1 || chatPosition == 2 || chatPosition == 4;
	}
	public static final long FADE_IN_TIME_MS = 200L;
	public static final long FADE_OUT_TIME_MS = 300L;
}
