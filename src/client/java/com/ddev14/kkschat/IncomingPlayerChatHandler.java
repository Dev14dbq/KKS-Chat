package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.LegacyAmpersandFormatting;
import com.ddev14.kkschat.chat.PlayerNameResolver;
import com.ddev14.kkschat.skin.PlayerInfoLookup;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Обработка входящих сообщений игрока (не whisper).
 */
public final class IncomingPlayerChatHandler {
	private IncomingPlayerChatHandler() {}

	public static void handle(KksChatHud hud, Component component, GameProfile sender) {
				// Всегда сохраняем сообщения, даже когда чат выключен
				// Они будут показаны когда чат включится
				if (component == null || component.getString().isEmpty()) {
					return;
				}
		
				String raw = component.getString().toLowerCase();
				String rawOriginal = component.getString();
		
				// Проверяем, является ли это whisper сообщением
				boolean isWhisper = raw.contains("whispers") || raw.contains("шепчет") || 
				                    raw.contains("whisper") || raw.contains("шепнул") ||
				                    raw.contains("шепнула") || raw.contains("tell");
		
				if (isWhisper) {
					WhisperChatHandler.handleWhisper(hud, component, rawOriginal);
					return;
				}
		
				// Heuristic: "plain" vanilla chat line (no siblings, no style, no §/json formatting)
				boolean hasLegacy = rawOriginal.indexOf('&') != -1;
				boolean looksPlain =
						!hasLegacy &&
						component.getSiblings().isEmpty() &&
						component.getStyle().isEmpty();
		
				// ПРИОРИТЕТ 1: Используем имя из GameProfile, если доступно (самый надежный способ)
				// Если GameProfile есть, используем его напрямую и НЕ парсим текст
				String senderName = null;
				boolean useGameProfile = false;
				if (sender != null && sender.name() != null && !sender.name().isEmpty()) {
					senderName = sender.name();
					useGameProfile = true; // Используем GameProfile, не парсим текст
				}
				
				// ПРИОРИТЕТ 2: Пытаемся извлечь имя из Component структуры (siblings, translatables)
				// Только если GameProfile недоступен
				if (senderName == null) {
					senderName = PlayerNameResolver.extractPlayerNameFromComponent(component);
				}
				
				// ПРИОРИТЕТ 3: Пытаемся извлечь имя из текста (для серверов с кастомным форматированием)
				// Только если GameProfile и Component не дали результата
				if (senderName == null) {
					senderName = PlayerNameResolver.extractPlayerNameFromText(rawOriginal);
				}
				
				// Fallback: try to detect vanilla "<Name> msg" pattern from plain text
				String bodyText = rawOriginal;
				if (senderName == null && rawOriginal.startsWith("<")) {
					int end = rawOriginal.indexOf('>');
					if (end > 1) {
						String potentialName = rawOriginal.substring(1, end);
						// Проверяем, что это похоже на ник (используем общий метод проверки)
						if (PlayerNameResolver.isValidPlayerName(potentialName)) {
							senderName = potentialName;
		
							int msgStart = end + 1;
							if (msgStart < rawOriginal.length() && rawOriginal.charAt(msgStart) == ' ') {
								msgStart++;
							}
							if (msgStart < rawOriginal.length()) {
								bodyText = rawOriginal.substring(msgStart);
							} else {
								bodyText = "";
							}
						}
					}
				} else if (senderName != null && !useGameProfile) {
					// Парсим текст только если НЕ использовали GameProfile
					// If we extracted name from Component or text, try to get body text
					// Remove the <name> prefix from rawOriginal if it exists
					if (rawOriginal.startsWith("<" + senderName + ">")) {
						int prefixEnd = senderName.length() + 2; // <name>
						if (prefixEnd < rawOriginal.length() && rawOriginal.charAt(prefixEnd) == ' ') {
							prefixEnd++; // Skip space after >
						}
						if (prefixEnd < rawOriginal.length()) {
							bodyText = rawOriginal.substring(prefixEnd);
						} else {
							bodyText = "";
						}
					} else {
						// Удаляем ник из начала текста (может быть с префиксами типа [L], [ИГРОК] и т.д.)
						String textWithoutPrefixes = rawOriginal.trim();
						// Пропускаем префиксы в квадратных скобках
						while (textWithoutPrefixes.startsWith("[")) {
							int bracketEnd = textWithoutPrefixes.indexOf(']');
							if (bracketEnd != -1) {
								textWithoutPrefixes = textWithoutPrefixes.substring(bracketEnd + 1).trim();
							} else {
								break;
							}
						}
						
						// Удаляем ник из начала
						if (textWithoutPrefixes.startsWith(senderName)) {
							int nameEnd = senderName.length();
							// Пропускаем возможные разделители (пробелы, стрелки, двоеточия и т.д.)
							while (nameEnd < textWithoutPrefixes.length() && 
							       (textWithoutPrefixes.charAt(nameEnd) == ' ' || 
							        textWithoutPrefixes.charAt(nameEnd) == '>' || 
							        textWithoutPrefixes.charAt(nameEnd) == ':' ||
							        textWithoutPrefixes.charAt(nameEnd) == '-')) {
								nameEnd++;
							}
							if (nameEnd < textWithoutPrefixes.length()) {
								bodyText = textWithoutPrefixes.substring(nameEnd).trim();
							} else {
								bodyText = "";
							}
						} else {
							bodyText = rawOriginal;
						}
					}
				} else if (useGameProfile && senderName != null) {
					// Если использовали GameProfile, пытаемся извлечь bodyText из Component
					// Но не перезаписываем senderName - используем имя из GameProfile
					String fullText = component.getString();
					// Пытаемся найти имя в тексте и удалить его для получения bodyText
					if (fullText.contains(senderName)) {
						int nameIndex = fullText.indexOf(senderName);
						int textStart = nameIndex + senderName.length();
						// Пропускаем разделители после имени
						while (textStart < fullText.length() && 
						       (fullText.charAt(textStart) == ' ' || 
						        fullText.charAt(textStart) == '>' || 
						        fullText.charAt(textStart) == ':' ||
						        fullText.charAt(textStart) == '-' ||
						        fullText.charAt(textStart) == '»')) {
							textStart++;
						}
						if (textStart < fullText.length()) {
							bodyText = fullText.substring(textStart).trim();
						} else {
							bodyText = "";
						}
					} else {
						// Если имя не найдено в тексте, используем весь текст как bodyText
						bodyText = fullText;
					}
				}
		
				if (senderName != null) {
					// Проверяем, есть ли ник в начале оригинального сообщения
					// Если да - используем оригинальный формат ника и убираем дублирование
					String originalNickPart = null;
					int originalNickEnd = -1;
					
					// Ищем ник в начале оригинального сообщения
					String rawLower = rawOriginal.toLowerCase();
					String senderNameLower = senderName.toLowerCase();
					
					// Проверяем различные паттерны: [L] PlayerName », PlayerName: и т.д.
					// Ищем имя в начале сообщения (первые 40 символов, чтобы учесть префиксы)
					String searchArea = rawOriginal.length() > 40 ? rawOriginal.substring(0, 40) : rawOriginal;
					String searchAreaLower = searchArea.toLowerCase();
					
					if (searchAreaLower.contains(senderNameLower)) {
						// Находим позицию имени в области поиска (без учета регистра)
						int nameIndexInSearch = searchAreaLower.indexOf(senderNameLower);
						if (nameIndexInSearch >= 0) {
							// Находим точную позицию в оригинальном тексте (с учетом регистра)
							// Ищем имя в оригинальном тексте, начиная с начала
							int originalNameIndex = -1;
							for (int i = 0; i <= Math.min(searchArea.length() - senderName.length(), nameIndexInSearch + 10); i++) {
								if (i + senderName.length() <= rawOriginal.length()) {
									String candidate = rawOriginal.substring(i, i + senderName.length());
									if (candidate.equalsIgnoreCase(senderName)) {
										originalNameIndex = i;
										break;
									}
								}
							}
							
							if (originalNameIndex >= 0) {
								// Ищем конец ника (до разделителей: », :, >, пробел и т.д.)
								int nameEnd = originalNameIndex + senderName.length();
								int textStart = nameEnd;
								
								// Пропускаем разделители после имени
								while (textStart < rawOriginal.length() && 
								       (rawOriginal.charAt(textStart) == ' ' || 
								        rawOriginal.charAt(textStart) == '»' ||
								        rawOriginal.charAt(textStart) == ':' ||
								        rawOriginal.charAt(textStart) == '>' ||
								        rawOriginal.charAt(textStart) == '-')) {
									textStart++;
								}
								
								// Если после ника есть текст, значит это действительно ник в начале
								if (textStart < rawOriginal.length() && textStart > senderName.length()) {
									originalNickPart = rawOriginal.substring(0, textStart).trim();
									originalNickEnd = textStart;
								}
							}
						}
					}
					
					// Если нашли оригинальный формат ника в начале сообщения - используем его
					if (originalNickPart != null && originalNickEnd > 0) {
						// Используем оригинальный Component, но извлекаем только часть с ником и текстом
						// Берем оригинальный Component и применяем стили
						MutableComponent originalNickComponent = null;
						Component originalBodyComponent = null;
						
						// Пытаемся извлечь компоненты из оригинального Component
						if (component.getSiblings().isEmpty()) {
							// Простой случай - один компонент
							String originalText = component.getString();
							if (originalText.length() >= originalNickEnd) {
								// Создаем компонент для ника из оригинального текста
								String nickText = originalText.substring(0, originalNickEnd).trim();
								String bodyTextFromOriginal = originalText.substring(originalNickEnd).trim();
								
								// Используем оригинальный формат ника (может содержать префиксы типа [L])
								originalNickComponent = Component.literal(nickText)
										.withStyle(component.getStyle());
								
								// Текст сообщения
								if (bodyTextFromOriginal.indexOf('&') != -1) {
									originalBodyComponent = LegacyAmpersandFormatting.applyLegacyColorCodes(bodyTextFromOriginal, ChatFormatting.WHITE);
								} else {
									originalBodyComponent = Component.literal(bodyTextFromOriginal)
											.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
								}
							}
						}
						
						// Если не удалось извлечь из Component, используем строковый формат
						if (originalNickComponent == null) {
							originalNickComponent = Component.literal(originalNickPart)
									.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
							
							String bodyTextFromOriginal = rawOriginal.substring(originalNickEnd).trim();
							if (bodyTextFromOriginal.indexOf('&') != -1) {
								originalBodyComponent = LegacyAmpersandFormatting.applyLegacyColorCodes(bodyTextFromOriginal, ChatFormatting.WHITE);
							} else {
								originalBodyComponent = Component.literal(bodyTextFromOriginal)
										.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
							}
						}
						
						// Добавляем пробел между ником и текстом
						MutableComponent separator = Component.literal(" ")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
						hud.messageComponent = originalNickComponent.append(separator).append(originalBodyComponent);
					} else {
						// Ник не найден в начале - используем наш форматированный ник
						// Формат: [Name]: (двоеточие того же цвета что и ник, без пробела после ])
						MutableComponent nick = Component.literal("[" + senderName + "]:")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
						
						// Если в тексте сообщения есть &-коды, применяем их только к тексту
						Component body;
						if (bodyText.indexOf('&') != -1) {
							// Применяем &-коды только к bodyText, не к нику
							// Важно: если текст не начинается с &-кода цвета, первый фрагмент должен быть белым
							body = LegacyAmpersandFormatting.applyLegacyColorCodes(bodyText, ChatFormatting.WHITE);
						} else if (looksPlain) {
							// Простой текст без форматирования — белый цвет
							body = Component.literal(bodyText)
									.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
						} else {
							// Сервер уже отформатировал текст — используем его как есть
							// Но нужно извлечь только часть после ника из оригинального Component
							body = Component.literal(bodyText)
									.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
						}
						
						// Добавляем один пробел между : и текстом (белый цвет)
						MutableComponent separator = Component.literal(" ")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
						hud.messageComponent = nick.append(separator).append(body);
					}
				} else if (hasLegacy) {
					// Нет ника, но есть &-коды — конвертим всё сообщение в стили
					hud.messageComponent = LegacyAmpersandFormatting.applyLegacyColorCodes(rawOriginal);
				} else {
					// Уже отформатированный сервером Component — оставляем как есть.
					hud.messageComponent = component;
				}
		
				hud.systemMessage = false;
		
				// Resolve PlayerInfo by name or UUID (same idea as in Chat Heads suggestions mixin)
				hud.senderInfo = null;
				if (sender != null && sender.id() != null) {
					// ПРИОРИТЕТ 1: Используем UUID из GameProfile для получения PlayerInfo
					Minecraft minecraft = Minecraft.getInstance();
					if (minecraft.getConnection() != null) {
						hud.senderInfo = minecraft.getConnection().getPlayerInfo(sender.id());
					}
				}
				
				// ПРИОРИТЕТ 2: Если PlayerInfo не найден по UUID, пытаемся найти по имени
				if (hud.senderInfo == null && senderName != null && !senderName.isEmpty()) {
					hud.senderInfo = PlayerInfoLookup.getPlayerInfoByName(senderName);
				}
				
				// ПРИОРИТЕТ 3: Если есть GameProfile, но PlayerInfo еще не загружен, сохраняем имя для последующего поиска
				if (hud.senderInfo == null && sender != null && sender.name() != null && senderName == null) {
					senderName = sender.name();
				}
		
				// Сохраняем в историю и определяем, было ли это повторяющееся сообщение
				// Передаем имя отправителя для повторного поиска когда скин загрузится (важно для SkinsRestorer)
				ChatMessageEntry entry = hud.addToHistory(hud.messageComponent, hud.senderInfo, false, senderName);
				boolean isRepeat = entry != null && entry.repeatCount > 1;
		
				long now = System.currentTimeMillis();
				if (isRepeat) {
					// Только обновляем таймер скрытия, анимацию появления не трогаем
					hud.lastMessageTime = now;
				} else {
					// Новое сообщение: инициализируем и анимацию, и таймер скрытия
					hud.firstMessageTime = now;
					hud.lastMessageTime = now;
				}
	}
}
