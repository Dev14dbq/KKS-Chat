package com.ddev14.kkschat;

import com.ddev14.kkschat.chat.ChatMessageEntry;
import com.ddev14.kkschat.chat.LegacyAmpersandFormatting;
import com.ddev14.kkschat.chat.PlayerNameResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class SystemChatHandler {
	private SystemChatHandler() {}

	public static void handle(KksChatHud hud, Component component) {
				// Всегда сохраняем сообщения, даже когда чат выключен
				// Они будут показаны когда чат включится
				if (component == null || component.getString().isEmpty()) {
					return;
				}
		
				String raw = component.getString();
				String rawLower = raw.toLowerCase();
				
				// ВАЖНО: Проверяем, является ли это сообщением от игрока, которое пришло через системный канал
				// Это часто происходит на серверах с кастомными плагинами чата
				// Паттерны: [L] имя » текст, [G] имя » текст, имя: текст, <имя> текст и т.д.
				
				String playerName = PlayerNameResolver.extractPlayerNameFromComponent(component);
				if (playerName == null) {
					playerName = PlayerNameResolver.extractPlayerNameFromText(raw);
				}
				
				// Если имя не найдено, но есть паттерн с текстом после имени, пробуем извлечь имя из паттерна
				if (playerName == null) {
					// Паттерны типа: [L] имя » текст, [G] имя » текст
					if (raw.contains("»")) {
						int arrowIndex = raw.indexOf("»");
						String beforeArrow = raw.substring(0, arrowIndex).trim();
						// Ищем имя после последнего пробела или скобки
						int lastSpace = beforeArrow.lastIndexOf(' ');
						int lastBracket = beforeArrow.lastIndexOf(']');
						int startIndex = Math.max(lastSpace, lastBracket) + 1;
						if (startIndex < beforeArrow.length()) {
							String potentialName = beforeArrow.substring(startIndex).trim();
							// Убираем коды форматирования
							potentialName = potentialName.replaceAll("[§&][0-9A-FK-ORa-fk-or]", "").trim();
							if (PlayerNameResolver.isValidPlayerName(potentialName)) {
								playerName = potentialName;
							}
						}
					}
				}
				
				// Если нашли имя игрока и сообщение содержит текст после имени (не просто системное сообщение)
				// Обрабатываем как сообщение от игрока
				if (playerName != null && playerName.length() >= 2 && playerName.length() <= 16) {
					// Проверяем, что это действительно сообщение от игрока, а не системное
					// Ищем паттерны типа: [префикс] имя » текст, имя: текст, <имя> текст
					boolean looksLikePlayerMessage = raw.contains("»") || raw.contains(":") || 
					                                   raw.contains(">") || raw.contains("<") ||
					                                   (raw.indexOf(playerName) >= 0 && raw.length() > playerName.length() + 5);
					
					if (looksLikePlayerMessage) {
						// Обрабатываем как сообщение от игрока (sender будет null, но имя извлечем из текста)
						hud.onPlayerMessage(component, null);
						return;
					}
				}
				
				// Проверяем translation key для универсальной поддержки всех языков
				String translationKey = null;
				if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
					translationKey = translatable.getKey();
				}
		
				// Проверяем, является ли это сообщением о присоединении/выходе из игры
				// Для таких сообщений не показываем голову, только иконку или ничего
				boolean isJoinMessage = rawLower.contains("[+]") || rawLower.contains("[-]") ||
				                        rawLower.contains("joined") || rawLower.contains("присоединился") ||
				                        rawLower.contains("присоединилась") || rawLower.contains("вошел") ||
				                        rawLower.contains("вошла") || rawLower.contains("left") ||
				                        rawLower.contains("покинул") || rawLower.contains("покинула") ||
				                        rawLower.contains("вышел") || rawLower.contains("вышла") ||
				                        rawLower.contains("disconnected") || rawLower.contains("отключился") ||
				                        rawLower.contains("отключилась") ||
				                        (translationKey != null && (
				                            translationKey.contains("multiplayer.player.joined") ||
				                            translationKey.contains("multiplayer.player.left")));
		
				// Проверяем, является ли это whisper сообщением в системных сообщениях
				boolean isWhisper = rawLower.contains("whispers") || rawLower.contains("шепчет") || 
				                    rawLower.contains("whisper") || rawLower.contains("шепнул") ||
				                    rawLower.contains("шепнула") || rawLower.contains("tell") ||
				                    (translationKey != null && (translationKey.contains("chat.type.text") || translationKey.contains("commands.message")));
		
				if (isWhisper) {
					WhisperChatHandler.handleWhisper(hud, component, raw);
					return;
				}
		
				// Проверяем, является ли это сообщением о завершении испытания
				boolean isChallenge = rawLower.contains("has completed the challenge") ||
				                       rawLower.contains("завершил испытание") ||
				                       rawLower.contains("завершила испытание") ||
				                       rawLower.contains("completed the challenge") ||
				                       (translationKey != null && translationKey.contains("advancements.challenge"));
				
				// Проверяем, является ли это сообщением о достижении (но не испытанием)
				boolean isAchievement = !isChallenge && (
				                        rawLower.contains("has made the advancement") ||
				                        rawLower.contains("получил достижение") ||
				                        rawLower.contains("получила достижение") ||
				                        rawLower.contains("получил прогресс") ||
				                        rawLower.contains("получила прогресс") ||
				                        rawLower.contains("achievement") ||
				                        rawLower.contains("advancement") ||
				                        (translationKey != null && translationKey.contains("advancements")));
				
				// Проверяем, является ли это сообщением об ошибке (проверяем ПЕРЕД проверкой на сообщения о сне)
				// Используем translation key для универсальной поддержки всех языков
				boolean isError = rawLower.contains("error") || rawLower.contains("ошибка") ||
				                  rawLower.contains("exception") || rawLower.contains("исключение") ||
				                  rawLower.contains("failed") || rawLower.contains("не удалось") ||
				                  rawLower.contains("failed to") ||
				                  rawLower.contains("cannot") || rawLower.contains("не может") ||
				                  rawLower.contains("unable") || rawLower.contains("не в состоянии") ||
				                  rawLower.contains("unknown command") || rawLower.contains("неизвестная команда") ||
				                  (rawLower.contains("unknown") && rawLower.contains("command")) ||
				                  (rawLower.contains("неизвестная") && rawLower.contains("команда")) ||
				                  rawLower.contains("invalid") || rawLower.contains("неверная") ||
				                  rawLower.contains("does not exist") || rawLower.contains("не существует") ||
				                  (rawLower.contains("command") && (rawLower.contains("unknown") || rawLower.contains("неизвестная") || rawLower.contains("invalid") || rawLower.contains("неверная"))) ||
				                  // Проверка по translation key для всех языков
				                  (translationKey != null && (
				                      translationKey.contains("commands.help.failed") ||
				                      translationKey.contains("argument.entity.unknown") ||
				                      translationKey.contains("argument.item.id.invalid") ||
				                      translationKey.contains("argument.nbt.unknown") ||
				                      translationKey.contains("argument.uuid.invalid") ||
				                      translationKey.contains("commands.generic.exception") ||
				                      translationKey.contains("commands.generic.syntax") ||
				                      translationKey.contains("commands.generic.unknown")));
				
				// Проверяем, является ли это сообщением о сне
				// Используем translation key для универсальной поддержки всех языков
				boolean isSleepMessage = rawLower.contains("bed") || rawLower.contains("кровать") ||
				                        rawLower.contains("sleep") || rawLower.contains("спать") ||
				                        rawLower.contains("сон") || rawLower.contains("thunderstorm") ||
				                        rawLower.contains("гроза") || rawLower.contains("too far") ||
				                        rawLower.contains("слишком далеко") || rawLower.contains("occupied") ||
				                        rawLower.contains("занята") || rawLower.contains("not safe") ||
				                        rawLower.contains("небезопасно") || rawLower.contains("monsters nearby") ||
				                        rawLower.contains("монстры поблизости") ||
				                        // Проверка по translation key для всех языков
				                        (translationKey != null && (
				                            translationKey.contains("tile.bed") ||
				                            translationKey.contains("sleep") ||
				                            translationKey.contains("bed.noSleep")));
				
				// Проверяем, является ли это сообщением о скриншоте
				// Используем translation key для универсальной поддержки всех языков
				boolean isScreenshot = rawLower.contains("screenshot") || rawLower.contains("скриншот") ||
				                       rawLower.contains("screenshot saved") || rawLower.contains("скриншот сохранен") ||
				                       rawLower.contains("скриншот сохранён") || rawLower.contains("saved screenshot") ||
				                       (translationKey != null && (
				                           translationKey.contains("screenshot") ||
				                           translationKey.contains("screenshot.saved")));
		
				// Используем оригинальный Component со всем его функционалом (кликабельность и т.д.)
				// Применяем только цвет, если нужно, но сохраняем оригинальные обработчики кликов
				Component styled = component;
				
				// Если есть &-коды форматирования, применяем их, но сохраняем оригинальные обработчики
				if (raw.indexOf('&') != -1) {
					if (isChallenge) {
						// Для испытаний применяем фиолетовый цвет, но сохраняем оригинальные обработчики
						Component colorized = LegacyAmpersandFormatting.applyLegacyColorCodesForChallenge(raw);
						// Копируем оригинальные обработчики кликов из оригинального Component
						styled = LegacyAmpersandFormatting.copyClickHandlers(component, colorized);
					} else if (isAchievement) {
						// Для достижений применяем зеленый цвет, но сохраняем оригинальные обработчики
						Component colorized = LegacyAmpersandFormatting.applyLegacyColorCodesForAchievement(raw);
						styled = LegacyAmpersandFormatting.copyClickHandlers(component, colorized);
					} else {
						Component colorized = LegacyAmpersandFormatting.applyLegacyColorCodesForSystem(raw);
						styled = LegacyAmpersandFormatting.copyClickHandlers(component, colorized);
					}
				} else {
					// Если нет &-кодов, просто применяем цвет, но сохраняем оригинальные обработчики
					if (isChallenge || isAchievement) {
						// Копируем стиль с цветом, но сохраняем оригинальные обработчики кликов
						Style originalStyle = component.getStyle();
						ChatFormatting color = isChallenge ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GREEN;
						Style newStyle = originalStyle.withColor(color);
						styled = component.copy().setStyle(newStyle);
					}
				}
				
				// Проигрываем звук завершения испытания
				if (isChallenge) {
					Minecraft minecraft = Minecraft.getInstance();
					if (minecraft.player != null && minecraft.level != null) {
						minecraft.player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
					}
				}
		
				hud.messageComponent = styled;
				hud.senderInfo = null;
				hud.systemMessage = true;
		
				// Сохраняем в историю и определяем, было ли это повторяющееся сообщение
				ChatMessageEntry entry = hud.addToHistory(hud.messageComponent, null, true, null);
				if (entry != null) {
					entry.isAchievement = isAchievement;
					entry.isChallenge = isChallenge;
					entry.isError = isError;
					entry.isSleepMessage = isSleepMessage;
					entry.isJoinMessage = isJoinMessage;
					entry.isScreenshot = isScreenshot;
				}
				
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
