package com.ddev14.kkschat.chat;

import static com.ddev14.kkschat.chat.ChatLayout.FADE_IN_TIME_MS;
import static com.ddev14.kkschat.chat.ChatLayout.FADE_OUT_TIME_MS;

/**
 * Расчёт видимости и анимации строки по настройке «время отображения».
 */
public final class ChatDisplayTiming {
	private ChatDisplayTiming() {}

	// ── Result of animation computation ──────────────────────────────────────

	/** Transform to apply when rendering a message in the closed overlay. */
	public static final class AnimTransform {
		/** When true the message must be skipped entirely (and rendering loop broken). */
		public boolean hidden;
		/** Opacity multiplier (0.0 – 1.0). */
		public float alpha = 1.0f;
		/** Additional X offset in pixels (positive = right). */
		public int offsetX;
		/** Additional Y offset in pixels (positive = down). */
		public int offsetY;
	}

	// ── Constants ─────────────────────────────────────────────────────────────

	/** Horizontal distance (px) used for left/right slide animations. */
	private static final int SLIDE_H = ChatLayout.MAX_BOX_WIDTH + ChatLayout.SIDE_MARGIN * 2;
	/** Vertical distance (px) used for up/down/bounce slide animations. */
	private static final int SLIDE_V = 50;

	// ── Public API ────────────────────────────────────────────────────────────

	/**
	 * Computes the full animation transform for a message in the closed overlay.
	 *
	 * @param animIn  animation style for the enter phase
	 * @param animOut animation style for the leave phase
	 */
	public static AnimTransform computeTransform(ChatMessageEntry entry, long now,
			int displayTimeSeconds, ChatAnimationType animIn, ChatAnimationType animOut) {

		AnimTransform t = new AnimTransform();

		long displayTimeMs = displayTimeSeconds * 1000L;
		long entryAge      = now - entry.timestamp;

		if (entryAge > displayTimeMs) {
			t.hidden = true;
			return t;
		}

		long timeSinceFirst = now - entry.firstMessageTime;
		long timeUntilHide  = displayTimeMs - entryAge;

		boolean entering = timeSinceFirst < FADE_IN_TIME_MS;
		boolean leaving  = timeUntilHide  < FADE_OUT_TIME_MS;

		float enterProg = entering ? (float) timeSinceFirst / FADE_IN_TIME_MS : 1f;
		float leaveProg = leaving  ? 1f - (float) timeUntilHide / FADE_OUT_TIME_MS : 0f;

		// ── Alpha ──────────────────────────────────────────────────────────────
		if (entering) {
			t.alpha = (animIn  == ChatAnimationType.NONE) ? 1.0f : enterProg;
		} else if (leaving) {
			t.alpha = (animOut == ChatAnimationType.NONE) ? 1.0f : 1f - leaveProg;
		} else {
			t.alpha = 1.0f;
		}

		// ── Enter offsets ──────────────────────────────────────────────────────
		if (entering) {
			switch (animIn) {
				case SLIDE_LEFT  -> t.offsetX = (int) (-SLIDE_H * (1f - enterProg));
				case SLIDE_RIGHT -> t.offsetX = (int) ( SLIDE_H * (1f - enterProg));
				case SLIDE_UP    -> t.offsetY = (int) (-SLIDE_V * (1f - enterProg));
				case SLIDE_DOWN  -> t.offsetY = (int) ( SLIDE_V * (1f - enterProg));
				case BOUNCE -> {
					float elastic = easeOutElastic(enterProg);
					t.offsetY = (int) (SLIDE_V * (1f - elastic));
				}
				default -> {} // FADE, NONE — no offset
			}
		}

		// ── Leave offsets ──────────────────────────────────────────────────────
		if (leaving) {
			switch (animOut) {
				case SLIDE_LEFT  -> t.offsetX = (int) (-SLIDE_H * leaveProg);
				case SLIDE_RIGHT -> t.offsetX = (int) ( SLIDE_H * leaveProg);
				case SLIDE_UP    -> t.offsetY = (int) (-SLIDE_V * leaveProg);
				case SLIDE_DOWN  -> t.offsetY = (int) ( SLIDE_V * leaveProg);
				case BOUNCE      -> {} // bounce only on enter; leave = fade (alpha already set)
				default -> {} // FADE, NONE — no offset
			}
		}

		return t;
	}

	// ── Easing helpers ────────────────────────────────────────────────────────

	/**
	 * Elastic ease-out: starts fast, overshoots the target, bounces back.
	 * Maps t ∈ [0, 1] → output ∈ [0, 1] with temporary overshoot > 1.
	 */
	private static float easeOutElastic(float t) {
		if (t == 0f || t == 1f) return t;
		return (float) (Math.pow(2, -10 * t)
				* Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
	}
}
