package com.ddev14.kkschat.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a list of {@link ChatRule}s to an incoming {@link ChatMessageEntry}.
 *
 * <p>Rules are evaluated in order. Text/color transformations modify
 * {@code entry.message} directly. Metadata overrides (icon, displayTime,
 * noStyle) are returned in the {@link Result}.
 */
public final class RuleEngine {
    private RuleEngine() {}

    /** Result returned by {@link #apply}. */
    public static final class Result {
        /** If true, the message must not be added to history. */
        public boolean hide;
        /** Non-null when a rule overrides the icon. */
        public String iconOverride;
        /** True when a rule disables stylization for this message. */
        public boolean noStyle;
        /** Positive when a rule overrides the display time (seconds). */
        public float displayTimeOverride = -1f;
    }

    /**
     * Applies all matching rules to {@code entry}.
     * Modifies {@code entry.message} for text/color transforms.
     *
     * @return a {@link Result} with metadata overrides
     */
    public static Result apply(List<ChatRule> rules, ChatMessageEntry entry) {
        Result result = new Result();
        if (rules == null || rules.isEmpty()) return result;

        Component component = entry.message;

        for (ChatRule rule : rules) {
            if (rule == null || rule.condition == null || rule.action == null) continue;

            String text = component.getString();
            MatchContext ctx = evaluate(rule.condition, text, entry.type);
            if (!ctx.matched) continue;

            ChatRule.Action a = rule.action;

            // hide stops everything
            if (a.hide) {
                result.hide = true;
                return result;
            }

            // metadata overrides
            if (a.icon != null && !a.icon.isBlank())  result.iconOverride = a.icon;
            if (a.noStyle)                             result.noStyle = true;
            if (a.displayTime > 0)                     result.displayTimeOverride = a.displayTime;

            // text transforms (replaceText takes priority)
            String originalText = text; // snapshot before transforms for {text} variable
            if (a.replaceText != null) {
                component = Component.literal(expandVars(a.replaceText, entry, ctx.matchedText, originalText));
            } else {
                if (a.replaceMatch != null && ctx.matchedText != null) {
                    String expanded = expandVars(a.replaceMatch, entry, ctx.matchedText, originalText);
                    String replaced = text.replace(ctx.matchedText, expanded);
                    component = Component.literal(replaced);
                }
                if (a.colorMatch != null && ctx.matchedText != null) {
                    Integer rgb = parseHexColor(a.colorMatch);
                    if (rgb != null) {
                        component = colorMatchInText(component, ctx.matchedText, rgb);
                    }
                }
            }

            // whole-message color (applied after text transforms)
            if (a.color != null) {
                Integer rgb = parseHexColor(a.color);
                if (rgb != null) {
                    MutableComponent colored = Component.empty()
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
                    colored.append(component);
                    component = colored;
                }
            }

            if (a.stopAfter) break;
        }

        entry.message = component;
        return result;
    }

    // ── condition evaluation ──────────────────────────────────────────────────

    private static final class MatchContext {
        boolean matched;
        /** Text fragment that was matched — used by replaceMatch / colorMatch. */
        String matchedText;
    }

    private static MatchContext evaluate(ChatRule.Condition cond, String text, MessageType type) {
        MatchContext ctx = new MatchContext();
        boolean any = "ANY".equalsIgnoreCase(cond.matchType);

        // Evaluate each condition; start as "not applicable" (null → doesn't count)
        Boolean typeOk    = null;
        Boolean containsOk = null;
        Boolean startsOk  = null;
        Boolean endsOk    = null;
        Boolean regexOk   = null;

        String matchedText = null;

        if (cond.type != null) {
            try {
                typeOk = (type == MessageType.valueOf(cond.type.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                typeOk = false;
            }
        }

        if (cond.contains != null) {
            containsOk = text.contains(cond.contains);
            if (Boolean.TRUE.equals(containsOk) && matchedText == null) {
                matchedText = cond.contains;
            }
        }

        if (cond.startsWith != null) {
            startsOk = text.startsWith(cond.startsWith);
            if (Boolean.TRUE.equals(startsOk) && matchedText == null) {
                matchedText = cond.startsWith;
            }
        }

        if (cond.endsWith != null) {
            endsOk = text.endsWith(cond.endsWith);
            if (Boolean.TRUE.equals(endsOk) && matchedText == null) {
                matchedText = cond.endsWith;
            }
        }

        if (cond.regex != null) {
            try {
                Matcher m = Pattern.compile(cond.regex).matcher(text);
                regexOk = m.find();
                if (Boolean.TRUE.equals(regexOk) && matchedText == null) {
                    matchedText = m.groupCount() > 0 ? m.group(1) : m.group(0);
                }
            } catch (Exception ignored) {
                regexOk = false;
            }
        }

        // Combine: only count conditions that were actually specified (non-null)
        if (any) {
            ctx.matched = isTrue(typeOk) || isTrue(containsOk)
                    || isTrue(startsOk) || isTrue(endsOk) || isTrue(regexOk);
        } else { // ALL: every specified condition must be true
            ctx.matched = allTrue(typeOk, containsOk, startsOk, endsOk, regexOk);
        }

        if (ctx.matched) ctx.matchedText = matchedText;
        return ctx;
    }

    /** Returns true only if the value is non-null AND true. */
    private static boolean isTrue(Boolean b) { return Boolean.TRUE.equals(b); }

    /**
     * Returns true if every non-null value is true.
     * Null means "condition not specified" → treated as satisfied.
     */
    private static boolean allTrue(Boolean... values) {
        for (Boolean b : values) {
            if (b != null && !b) return false;
        }
        return true;
    }

    // ── variable expansion ────────────────────────────────────────────────────

    /**
     * Expands {@code {variable}} placeholders in a template string.
     *
     * <p>Available variables:
     * <ul>
     *   <li>{@code {my_name}}  — local player's in-game name</li>
     *   <li>{@code {sender}}   — sender's name (from the message entry)</li>
     *   <li>{@code {text}}     — full original message text</li>
     *   <li>{@code {matched}}  — fragment matched by "contains" or "regex"</li>
     *   <li>{@code {type}}     — MessageType name (e.g. SYSTEM, PLAYER_CHAT)</li>
     *   <li>{@code {time}}     — current time in HH:mm format</li>
     *   <li>{@code {time_s}}   — current time in HH:mm:ss format</li>
     *   <li>{@code {repeat}}   — repeat counter (1 if not a duplicate)</li>
     * </ul>
     */
    private static String expandVars(String template, ChatMessageEntry entry,
            String matchedText, String originalText) {
        if (template == null || !template.contains("{")) return template;

        String s = template;

        // {my_name}
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                s = s.replace("{my_name}", mc.player.getName().getString());
            }
        } catch (Exception ignored) {}

        // {sender}
        s = s.replace("{sender}",  entry.senderName != null ? entry.senderName : "");

        // {text} — full original message
        s = s.replace("{text}",    originalText != null ? originalText : "");

        // {matched} — fragment matched by contains/regex
        s = s.replace("{matched}", matchedText  != null ? matchedText  : "");

        // {type}
        s = s.replace("{type}",    entry.type != null ? entry.type.name() : "");

        // {repeat}
        s = s.replace("{repeat}",  String.valueOf(entry.repeatCount));

        // {time} — HH:mm
        s = s.replace("{time}",    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        // {time_s} — HH:mm:ss
        s = s.replace("{time_s}",  LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return s;
    }

    // ── component helpers ─────────────────────────────────────────────────────

    /**
     * Rebuilds the component text, coloring every occurrence of {@code match}
     * with the given RGB color. Non-matching parts keep the original plain text.
     */
    private static Component colorMatchInText(Component comp, String match, int rgb) {
        String full = comp.getString();
        if (!full.contains(match)) return comp;

        MutableComponent result = Component.empty();
        Style matchStyle = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
        int start = 0;
        int idx;
        while ((idx = full.indexOf(match, start)) >= 0) {
            if (idx > start) {
                result.append(Component.literal(full.substring(start, idx)));
            }
            result.append(Component.literal(match).withStyle(matchStyle));
            start = idx + match.length();
        }
        if (start < full.length()) {
            result.append(Component.literal(full.substring(start)));
        }
        return result;
    }

	/** Parses a hex color string like "#FFD700" or "FFD700" to an RGB int. */
	public static Integer parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) return null;
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return (int) Long.parseLong(h, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
