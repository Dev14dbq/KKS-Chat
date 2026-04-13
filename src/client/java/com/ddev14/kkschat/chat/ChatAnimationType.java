package com.ddev14.kkschat.chat;

/**
 * Animation style for incoming / expiring messages in the closed HUD overlay.
 * Used in the "animationType" config field.
 */
public enum ChatAnimationType {

    /** Alpha fade — default. Message fades in and fades out smoothly. */
    FADE,

    /** Slide in from the left, slide out to the left. */
    SLIDE_LEFT,

    /** Slide in from the right, slide out to the right. */
    SLIDE_RIGHT,

    /** Slide in from above, slide out upward. */
    SLIDE_UP,

    /** Slide in from below, slide out downward. */
    SLIDE_DOWN,

    /**
     * Elastic bounce — message shoots in from below with a spring overshoot,
     * fades out on leave.
     */
    BOUNCE,

    /** No animation — message appears and disappears instantly. */
    NONE
}
