package com.ddev14.kkschat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Парсинг legacy {@code &} кодов и копирование {@link ClickEvent} из исходного компонента.
 */
public final class LegacyAmpersandFormatting {
	private LegacyAmpersandFormatting() {}

	public static Component applyLegacyColorCodesForSystem(String text) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY.withColor(ChatFormatting.GRAY);
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						style = Style.EMPTY.withColor(ChatFormatting.GRAY);
					} else if (fmt.isColor()) {
						// цвет игнорируем — системные серые
					} else {
						switch (fmt) {
							case BOLD -> style = style.withBold(true);
							case ITALIC -> style = style.withItalic(true);
							case UNDERLINE -> style = style.withUnderlined(true);
							case STRIKETHROUGH -> style = style.withStrikethrough(true);
							case OBFUSCATED -> style = style.withObfuscated(true);
							default -> {
							}
						}
					}

					i++;
					continue;
				}
			}

			current.append(c);
		}

		if (current.length() > 0) {
			result.append(Component.literal(current.toString()).setStyle(style));
		}

		return result;
	}

	public static Component copyClickHandlers(Component original, Component styled) {
		Style originalStyle = original.getStyle();
		ClickEvent clickEvent = originalStyle.getClickEvent();
		if (clickEvent != null) {
			MutableComponent result = styled.copy();
			Style styledStyle = result.getStyle();
			Style newStyle = styledStyle.withClickEvent(clickEvent);
			result.setStyle(newStyle);
			return result;
		}
		return styled;
	}

	public static ClickEvent findClickEvent(Component component) {
		if (component == null) {
			return null;
		}
		Style style = component.getStyle();
		ClickEvent clickEvent = style.getClickEvent();
		if (clickEvent != null) {
			return clickEvent;
		}
		for (Component sibling : component.getSiblings()) {
			clickEvent = findClickEvent(sibling);
			if (clickEvent != null) {
				return clickEvent;
			}
		}
		return null;
	}

	public static Component applyLegacyColorCodesForChallenge(String text) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						style = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
					} else if (fmt.isColor()) {
						// ignore
					} else {
						switch (fmt) {
							case BOLD -> style = style.withBold(true);
							case ITALIC -> style = style.withItalic(true);
							case UNDERLINE -> style = style.withUnderlined(true);
							case STRIKETHROUGH -> style = style.withStrikethrough(true);
							case OBFUSCATED -> style = style.withObfuscated(true);
							default -> {
							}
						}
					}

					i++;
					continue;
				}
			}

			current.append(c);
		}

		if (current.length() > 0) {
			result.append(Component.literal(current.toString()).setStyle(style));
		}

		return result;
	}

	public static Component applyLegacyColorCodesForAchievement(String text) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY.withColor(ChatFormatting.GREEN);
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						style = Style.EMPTY.withColor(ChatFormatting.GREEN);
					} else if (fmt.isColor()) {
						// ignore
					} else {
						switch (fmt) {
							case BOLD -> style = style.withBold(true);
							case ITALIC -> style = style.withItalic(true);
							case UNDERLINE -> style = style.withUnderlined(true);
							case STRIKETHROUGH -> style = style.withStrikethrough(true);
							case OBFUSCATED -> style = style.withObfuscated(true);
							default -> {
							}
						}
					}

					i++;
					continue;
				}
			}

			current.append(c);
		}

		if (current.length() > 0) {
			result.append(Component.literal(current.toString()).setStyle(style));
		}

		return result;
	}

	public static Component applyLegacyColorCodes(String text) {
		return applyLegacyColorCodes(text, null);
	}

	public static Component applyLegacyColorCodes(String text, ChatFormatting defaultColor) {
		MutableComponent result = Component.empty();
		Style style = defaultColor != null
				? Style.EMPTY.withColor(defaultColor)
				: Style.EMPTY;
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						style = defaultColor != null
								? Style.EMPTY.withColor(defaultColor)
								: Style.EMPTY;
					} else if (fmt.isColor()) {
						style = style.withColor(fmt);
					} else {
						switch (fmt) {
							case BOLD -> style = style.withBold(true);
							case ITALIC -> style = style.withItalic(true);
							case UNDERLINE -> style = style.withUnderlined(true);
							case STRIKETHROUGH -> style = style.withStrikethrough(true);
							case OBFUSCATED -> style = style.withObfuscated(true);
							default -> {
							}
						}
					}

					i++;
					continue;
				}
			}

			current.append(c);
		}

		if (current.length() > 0) {
			result.append(Component.literal(current.toString()).setStyle(style));
		}

		return result;
	}
}
