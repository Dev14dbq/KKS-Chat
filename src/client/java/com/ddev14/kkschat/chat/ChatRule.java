package com.ddev14.kkschat.chat;

import com.google.gson.annotations.SerializedName;

/**
 * A single chat rule loaded from the config file.
 *
 * <p>Example:
 * <pre>
 * {
 *   "if":   { "type": "SYSTEM", "contains": "[restart]" },
 *   "then": { "color": "#FF6666", "displayTime": 30 }
 * }
 * </pre>
 */
public final class ChatRule {

    /** Conditions block. Serialized as "if" in JSON. */
    @SerializedName("if")
    public Condition condition = new Condition();

    /** Actions block. Serialized as "then" in JSON. */
    @SerializedName("then")
    public Action action = new Action();

    // ── Condition ─────────────────────────────────────────────────────────────

    public static final class Condition {

        /**
         * Match by MessageType name.
         * Valid values: PLAYER_CHAT, WHISPER, SYSTEM, ERROR, SLEEP,
         *               ACHIEVEMENT, CHALLENGE, COMMAND_BLOCK, JOIN_LEAVE, SCREENSHOT
         */
        public String type;

        /** Message text contains this string (case-sensitive). */
        public String contains;

        /** Message text starts with this string (case-sensitive). */
        public String startsWith;

        /** Message text ends with this string (case-sensitive). */
        public String endsWith;

        /**
         * Full Java regex pattern matched against the message text.
         * The first capture group (if any) becomes the "matched fragment"
         * used by replaceMatch / colorMatch.
         */
        public String regex;

        /**
         * Logic for combining multiple specified conditions.
         * "ALL" — all must match (default, AND logic).
         * "ANY" — at least one must match (OR logic).
         */
        public String matchType = "ALL";
    }

    // ── Action ────────────────────────────────────────────────────────────────

    public static final class Action {

        /**
         * Override the icon for this message.
         * Use any valid Minecraft item ID, e.g. "minecraft:diamond".
         */
        public String icon;

        /**
         * Hide the message entirely — it will not be added to history.
         * When true, all other actions are ignored and rule processing stops.
         */
        public boolean hide;

        /**
         * Disable stylization for this specific message.
         * Reserved for future use.
         */
        public boolean noStyle;

        /**
         * Override the display time (seconds) for this specific message.
         * -1 = use the global setting from config.
         */
        public float displayTime = -1f;

        /**
         * Apply a color to the entire message text.
         * Hex string with or without #, e.g. "#FFD700" or "FFD700".
         */
        public String color;

        /**
         * Replace the entire message text with this string.
         * Takes priority over replaceMatch / colorMatch.
         */
        public String replaceText;

        /**
         * Replace the matched fragment (from "contains" or "regex") with this string.
         * Has no effect if neither "contains" nor "regex" is specified.
         */
        public String replaceMatch;

        /**
         * Apply a color to the matched fragment only.
         * Hex string, e.g. "#FFD700".
         * Has no effect if neither "contains" nor "regex" is specified.
         */
        public String colorMatch;

        /**
         * Stop processing further rules after this one is applied.
         * Default: false (rules continue to be evaluated).
         */
        public boolean stopAfter;
    }
}
