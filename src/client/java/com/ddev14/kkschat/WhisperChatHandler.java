package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.LegacyAmpersandFormatting;
import com.ddev14.kkschat.chat.MessageType;
import com.ddev14.kkschat.chat.PlayerNameResolver;
import com.ddev14.kkschat.skin.PlayerInfoLookup;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class WhisperChatHandler {
	private WhisperChatHandler() {}

	static void handleWhisper(KksChatHud hud, Component component, String raw) {
		if (SystemChatHandler.isVisiblyEmpty(component)) {
			return;
		}

		// Если стилизация выключена — показываем оригинальный компонент без переформатирования
		if (!hud.isModifyMessageText()) {
			hud.messageComponent = component;
			hud.senderInfo = null;
			ChatMessageEntry e = hud.addToHistoryWhisper(component, null, null, null);
			long now = System.currentTimeMillis();
			if (e != null && e.repeatCount > 1) { hud.lastMessageTime = now; }
			else { hud.firstMessageTime = now; hud.lastMessageTime = now; }
			return;
		}

		// Парсим whisper сообщение
				// Форматы: "Player whispers to you: message" или "Player whispers: message"
				// Или на русском: "Игрок шепчет вам: сообщение" или "Игрок шепчет: сообщение"
				// Или когда игрок сам шепчет: "You whisper to Player: message" или "Вы прошептали Игроку: сообщение"
				
				String lowerRaw = raw.toLowerCase();
				Minecraft minecraft = Minecraft.getInstance();
				String playerName = minecraft.player != null ? minecraft.player.getName().getString() : "";
				// Если имя игрока пустое или содержит "Player" с цифрами (рандомный ник), используем фиксированное имя
				if (playerName.isEmpty() || playerName.matches("(?i)Player\\d+")) {
					playerName = KksChatHud.DEFAULT_PLAYER_NAME;
				}
				boolean isPlayerSending = false; // Игрок сам отправляет whisper
				
				// Проверяем, является ли это системным сообщением о том, что игрок прошептал
				// Такие сообщения не нужно показывать вообще
				if (lowerRaw.startsWith("вы прошептали") || lowerRaw.startsWith("you whispered") ||
				    lowerRaw.startsWith("вы шепнул") || lowerRaw.startsWith("вы шепнула") ||
				    (lowerRaw.contains("вы прошептали") && !lowerRaw.contains(":")) ||
				    (lowerRaw.contains("you whispered") && !lowerRaw.contains(":"))) {
					// Это системное сообщение о том, что игрок прошептал - не показываем его
					return;
				}
				
				// Проверяем, отправляет ли игрок сам whisper
				if (lowerRaw.startsWith("you ") || lowerRaw.startsWith("вы ") || 
				    lowerRaw.contains("you whisper") || lowerRaw.contains("вы прошептали") ||
				    lowerRaw.contains("you whispered") || lowerRaw.contains("вы шепнул") ||
				    lowerRaw.contains("вы шепнула")) {
					isPlayerSending = true;
				}
				
				String senderName = null;
				String receiverName = null;
				String messageText = raw;
				
				if (isPlayerSending) {
					// Игрок сам отправляет whisper - парсим различные форматы
					// Формат 1: "You whisper to Player: message" или "Вы прошептали Player: message"
					String[] sendingPatterns = {
						"you whisper to ",
						"you whispered to ",
						"вы прошептали ",
						"вы шепнул ",
						"вы шепнула "
					};
					
					boolean foundPattern = false;
					for (String pattern : sendingPatterns) {
						int index = lowerRaw.indexOf(pattern);
						if (index >= 0) {
							int nameStart = index + pattern.length();
							// Ищем двоеточие после имени получателя
							int colonIndex = raw.indexOf(':', nameStart);
							if (colonIndex > nameStart) {
								receiverName = raw.substring(nameStart, colonIndex).trim();
								// Убираем "вы прошептали" и подобные фразы из текста
								String textPart = raw.substring(colonIndex + 1).trim();
								// Убираем возможные остатки "вы прошептали" из текста
								textPart = textPart.replaceAll("(?i)(вы прошептали|you whispered|you whisper).*?:\\s*", "");
								messageText = textPart;
								foundPattern = true;
							}
							break;
						}
					}
					
					// Формат 2: Гибкий парсинг для формата "Вы/Ты [любые символы] Имя: текст" или "Имя [любые символы] Вы/Ты: текст"
					// Поддерживает любые символы между "Вы/Ты" и именем, и любой порядок
					// Примеры: "Вы → PlayerName: текст", "Ты » PlayerName: текст", "PlayerName → Вы: текст", "Вы PlayerName: текст"
					if (!foundPattern) {
						// Ищем двоеточие в сообщении (разделяет имя и текст)
						int colonIndex = raw.indexOf(':');
						if (colonIndex > 0 && colonIndex < raw.length() - 1) {
							String beforeColon = raw.substring(0, colonIndex).trim();
							String afterColon = raw.substring(colonIndex + 1).trim();
							
							// Проверяем, содержит ли часть до двоеточия "Вы", "Ты" или "You"
							boolean containsYou = beforeColon.toLowerCase().contains("вы") || 
							                      beforeColon.toLowerCase().contains("ты") ||
							                      beforeColon.toLowerCase().contains("you");
							
							if (containsYou) {
								// Убираем коды форматирования для поиска, но сохраняем оригинал для извлечения имени
								String beforeColonClean = beforeColon.replaceAll("[§&][0-9A-FK-ORa-fk-or]", "");
								String beforeColonLower = beforeColonClean.toLowerCase();
								
								// Ищем позиции "Вы", "Ты" или "You" (с пробелом или без)
								int youIndex = -1;
								int youLength = 0;
								String youWord = null;
								
								// Проверяем различные варианты расположения "Вы/Ты/You"
								String[] youVariants = {"вы ", "ты ", "you ", " вы", " ты", " you", "вы", "ты", "you"};
								
								for (String variant : youVariants) {
									int idx = beforeColonLower.indexOf(variant.toLowerCase());
									if (idx >= 0) {
										// Проверяем, что это отдельное слово (не часть другого слова)
										boolean isWordStart = idx == 0 || !Character.isLetterOrDigit(beforeColonClean.charAt(idx - 1));
										boolean isWordEnd = idx + variant.length() >= beforeColonClean.length() || 
										                   !Character.isLetterOrDigit(beforeColonClean.charAt(idx + variant.length()));
										
										if (isWordStart && isWordEnd) {
											youIndex = idx;
											youLength = variant.length();
											youWord = variant;
											break;
										}
									}
								}
								
								if (youIndex >= 0) {
									// Извлекаем имя получателя - это часть до или после "Вы/Ты"
									String potentialName = null;
									
									if (youIndex == 0 || (youIndex < 5 && beforeColonClean.substring(0, youIndex).trim().isEmpty())) {
										// Формат "Вы [любые символы] Имя" - имя после "Вы"
										String afterYou = beforeColonClean.substring(youIndex + youLength).trim();
										// Убираем все символы-разделители и пробелы в начале
										afterYou = afterYou.replaceAll("^[→»>\\-: \\s]+", "").trim();
										// Берем первое слово как имя (до пробела, разделителя или конца)
										// Ищем конец имени (до пробела или разделителя)
										int nameEnd = afterYou.length();
										for (int i = 0; i < afterYou.length(); i++) {
											char c = afterYou.charAt(i);
											if (c == ' ' || c == '→' || c == '»' || c == '>' || c == '-' || c == ':') {
												nameEnd = i;
												break;
											}
										}
										if (nameEnd > 0) {
											potentialName = afterYou.substring(0, nameEnd).trim();
										} else if (!afterYou.isEmpty()) {
											potentialName = afterYou.trim();
										}
									} else {
										// Формат "Имя [любые символы] Вы" - имя до "Вы"
										String beforeYou = beforeColonClean.substring(0, youIndex).trim();
										// Убираем все символы-разделители и пробелы в конце
										beforeYou = beforeYou.replaceAll("[→»>\\-: \\s]+$", "").trim();
										// Берем последнее слово как имя
										int lastSpace = beforeYou.lastIndexOf(' ');
										int lastSeparator = Math.max(
											Math.max(beforeYou.lastIndexOf('→'), beforeYou.lastIndexOf('»')),
											Math.max(beforeYou.lastIndexOf('>'), Math.max(beforeYou.lastIndexOf('-'), beforeYou.lastIndexOf(':')))
										);
										int nameStart = Math.max(lastSpace, lastSeparator) + 1;
										if (nameStart < beforeYou.length()) {
											potentialName = beforeYou.substring(nameStart).trim();
										} else if (!beforeYou.isEmpty()) {
											potentialName = beforeYou.trim();
										}
									}
									
									// Проверяем, что это похоже на имя игрока (используем общий метод проверки)
									if (potentialName != null && PlayerNameResolver.isValidPlayerName(potentialName)) {
										// Используем найденное имя (коды форматирования будут убраны при использовании)
										receiverName = potentialName;
										messageText = afterColon;
										foundPattern = true;
									}
								}
							}
						}
					}
					
					// Если не нашли паттерн, возможно это сообщение без указания получателя (шепчет всем)
					if (!foundPattern) {
						// Пробуем найти двоеточие после "вы прошептали" или "you whisper"
						int colonIndex = raw.indexOf(':');
						if (colonIndex > 0) {
							messageText = raw.substring(colonIndex + 1).trim();
							// Убираем "вы прошептали" и подобные фразы из текста
							messageText = messageText.replaceAll("(?i)(вы прошептали|you whispered|you whisper).*?:\\s*", "");
							receiverName = null; // Неизвестный получатель - значит всем
						}
					}
					
					// Отправитель - это текущий игрок
					senderName = playerName;
				} else {
					// Игроку шепчут - парсим "Player whispers to you: message"
					String[] receivingPatterns = {
						" whispers to you: ",
						" whispers: ",
						" шепчет вам: ",
						" шепчет: ",
						" whisper to you: ",
						" whisper: ",
						" шепнул вам: ",
						" шепнула вам: ",
						" шепнул: ",
						" шепнула: "
					};
					
					for (String pattern : receivingPatterns) {
						int index = lowerRaw.indexOf(pattern.toLowerCase());
						if (index > 0) {
							senderName = raw.substring(0, index).trim();
							int msgStart = index + pattern.length();
							if (msgStart < raw.length()) {
								messageText = raw.substring(msgStart).trim();
								// Убираем "вы прошептали" и подобные фразы из текста
								messageText = messageText.replaceAll("(?i)(вы прошептали|you whispered|you whisper).*?:\\s*", "");
							} else {
								messageText = "";
							}
							break;
						}
					}
					
					// Если не нашли паттерн, пробуем найти через "whispers" или "шепчет" в любом месте
					if (senderName == null) {
						int whisperIndex = Math.max(
							Math.max(lowerRaw.indexOf("whispers"), lowerRaw.indexOf("шепчет")),
							Math.max(lowerRaw.indexOf("whisper"), Math.max(lowerRaw.indexOf("шепнул"), lowerRaw.indexOf("шепнула")))
						);
						
						if (whisperIndex > 0) {
							// Берем текст до "whispers"/"шепчет" как имя отправителя
							senderName = raw.substring(0, whisperIndex).trim();
							// Ищем двоеточие после "whispers"/"шепчет"
							int colonIndex = raw.indexOf(':', whisperIndex);
							if (colonIndex > 0 && colonIndex < raw.length() - 1) {
								messageText = raw.substring(colonIndex + 1).trim();
								// Убираем "вы прошептали" и подобные фразы из текста
								messageText = messageText.replaceAll("(?i)(вы прошептали|you whispered|you whisper).*?:\\s*", "");
							}
						}
					}
					
					// Получатель - это текущий игрок
					receiverName = playerName;
				}
				
				// Форматируем сообщение
				MutableComponent whisperPrefix;
				if (isPlayerSending) {
					whisperPrefix = Component.literal("Вы прошептали ")
							.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
				} else {
					whisperPrefix = Component.literal("Шепчет ")
							.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
				}
				
				MutableComponent nameComponent;
				if (isPlayerSending) {
					// Показываем имя получателя
					if (receiverName != null && !receiverName.isEmpty()) {
						nameComponent = Component.literal("[" + receiverName + "]:")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
					} else {
						nameComponent = Component.literal("[Всем]:")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
					}
				} else {
					// Показываем имя отправителя
					if (senderName != null && !senderName.isEmpty()) {
						nameComponent = Component.literal("[" + senderName + "]:")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
					} else {
						nameComponent = Component.literal("[Всем]:")
								.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
					}
				}
				
				Component messageComponent;
				if (messageText.indexOf('&') != -1) {
					messageComponent = LegacyAmpersandFormatting.applyLegacyColorCodes(messageText, ChatFormatting.WHITE);
				} else {
					messageComponent = Component.literal(messageText)
							.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
				}
				
				MutableComponent separator = Component.literal(" ")
						.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
				
			hud.messageComponent = whisperPrefix.append(nameComponent).append(separator).append(messageComponent);
				
				// Получаем PlayerInfo для отправителя и получателя
				PlayerInfo senderInfo = null;
				PlayerInfo receiverInfo = null;
				
				ClientPacketListener connection = minecraft.getConnection();
				if (connection != null) {
					if (isPlayerSending) {
						// Отправитель - текущий игрок
						if (playerName != null && !playerName.isEmpty()) {
							senderInfo = PlayerInfoLookup.getPlayerInfoByName(playerName);
						}
						// Получатель - тот, кому шепчем
						if (receiverName != null && !receiverName.isEmpty()) {
							receiverInfo = PlayerInfoLookup.getPlayerInfoByName(receiverName);
						}
					} else {
						// Отправитель - тот, кто шепчет
						if (senderName != null && !senderName.isEmpty()) {
							senderInfo = PlayerInfoLookup.getPlayerInfoByName(senderName);
						}
						// Получатель - текущий игрок
						if (playerName != null && !playerName.isEmpty()) {
							receiverInfo = PlayerInfoLookup.getPlayerInfoByName(playerName);
						}
					}
				}
				
				hud.senderInfo = senderInfo;
				
			ChatMessageEntry whisperEntry = hud.addToHistoryWhisper(hud.messageComponent, senderInfo, receiverInfo, senderName);
				boolean isRepeat = whisperEntry != null && whisperEntry.repeatCount > 1;
				
				long now = System.currentTimeMillis();
				if (isRepeat) {
					hud.lastMessageTime = now;
				} else {
					hud.firstMessageTime = now;
					hud.lastMessageTime = now;
				}
	}
}
