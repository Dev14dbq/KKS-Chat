package com.ddev14.kkschat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

/**
 * HUD overlay that shows the latest chat message
 * in a compact box above the hotbar.
 */
public class KksChatHud {

	// Максимальная ширина контейнера; фактическая ширина адаптивная под сообщение
	private static final int MAX_BOX_WIDTH = 260;
	// Avatar (head) size in GUI pixels – matches изначальный красный квадрат
	private static final int AVATAR_SIZE = 14;

	// Горизонтальные отступы слева/справа от контейнера
	private static final int HORIZONTAL_PADDING = 5;
	private static final int VERTICAL_PADDING = 6;

	// Distance from the bottom of the screen (above the hotbar)
	private static final int BOTTOM_OFFSET = 40;

	// Animation durations (in milliseconds)
	private static final long FADE_IN_TIME_MS = 200L;
	private static final long FADE_OUT_TIME_MS = 300L;

	/**
	 * Альфа строки по настройке «время отображения» (по времени последнего обновления записи).
	 * @return отрицательное значение, если строку не нужно показывать (истекло время)
	 */
	private float alphaForDisplayDuration(ChatMessageEntry entry, long now) {
		long displayTimeMs = displayTimeSeconds * 1000L;
		long entryAge = now - entry.timestamp;
		if (entryAge > displayTimeMs) {
			return -1f;
		}
		long timeSinceFirst = now - entry.firstMessageTime;
		long timeUntilHide = displayTimeMs - entryAge;
		if (timeSinceFirst < FADE_IN_TIME_MS) {
			return (float) timeSinceFirst / FADE_IN_TIME_MS;
		}
		if (timeUntilHide < FADE_OUT_TIME_MS) {
			return (float) timeUntilHide / FADE_OUT_TIME_MS;
		}
		return 1.0f;
	}

	// Full, styled chat line as Component (supports colors, hover, etc.)
	// Эти поля используются для отображения последнего сообщения, когда чат закрыт
	private Component messageComponent;
	private PlayerInfo senderInfo;    // for head rendering (player messages)
	// firstMessageTime – когда этот блок сообщения впервые показался (для анимации появления)
	// lastMessageTime  – когда последнее событие (сообщение/повтор) было получено (для таймера скрытия)
	// Эти поля используются для анимации и таймера скрытия последнего сообщения
	// В данный момент используются только для очистки, но могут быть использованы для будущего функционала
	@SuppressWarnings("unused")
	private boolean systemMessage;    // true for system messages
	@SuppressWarnings("unused")
	private long firstMessageTime;
	@SuppressWarnings("unused")
	private long lastMessageTime;

	// История сообщений для отображения когда чат открыт
	private final List<ChatMessageEntry> messageHistory = new ArrayList<>();
	
	// Значения по умолчанию (ранее были в конфиге)
	private static final boolean ENABLE_MESSAGE_DUPLICATION = true;
	private int maxHistorySize = 100;
	private float backgroundOpacity = 0.3f; // Прозрачность фона (0.0 - 1.0, соответствует 0-100%)
	private int displayTimeSeconds = 5;
	private float fontScale = 1.0f;
	private int chatPosition = 0; // 0=по центру, 1=слева, 2=справа
	private boolean antiSpamEnabled = true;
	
	// Прокрутка истории чата
	private int scrollOffset = 0;
	private static final int SCROLL_SPEED = 3; // Количество сообщений на прокрутку

	// Флаг включения/выключения мода (фильтр чата - блокировка стандартного чата)
	private boolean enabled = true;
	
	// Флаг изменения текста сообщений (true = изменять форматирование, false = показывать как есть)
	private boolean modifyMessageText = true;
	
	// Максимальное количество сообщений для отображения без сжатия
	private int maxVisibleMessages = 10;
	
	// Хранит позиции сообщений для обработки кликов (индекс в истории -> позиция на экране)
	private final Map<Integer, MessageBounds> messageBounds = new ConcurrentHashMap<>();
	
	// Хранит состояние развернутости сжатых сообщений
	private boolean collapsedMessagesExpanded = false;
	
	/**
	 * Класс для хранения границ сообщения на экране (для обработки кликов)
	 */
	private static class MessageBounds {
		int x, y, width, height;
		@SuppressWarnings("unused")
		int historyIndex; // индекс в messageHistory
		int textX; // X координата начала текста
		int textY; // Y координата начала текста
		int maxTextWidth; // максимальная ширина текста
		Component component; // Component сообщения для обработки кликов
		
		MessageBounds(int x, int y, int width, int height, int historyIndex) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.historyIndex = historyIndex;
		}
		
		MessageBounds(int x, int y, int width, int height, int historyIndex, int textX, int textY, int maxTextWidth, Component component) {
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
		
		boolean contains(int mouseX, int mouseY) {
			return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
		}
	}
	
	/**
	 * Очищает историю чата (вызывается при нажатии F3+D)
	 */
	public void clearChat() {
		messageHistory.clear();
		messageComponent = null;
		senderInfo = null;
		systemMessage = false;
		scrollOffset = 0;
		messageBounds.clear();
	}

	// Фиксированное имя игрока для тестирования (если нужно)
	private static final String DEFAULT_PLAYER_NAME = "DDev14";

	// Reusable stack for barrier icon
	private static final ItemStack BARRIER_STACK = new ItemStack(Items.BARRIER);
	// Reusable stack for emerald icon (for achievements)
	private static final ItemStack EMERALD_STACK = new ItemStack(Items.EMERALD);
	// Reusable stack for netherite icon (for challenges)
	private static final ItemStack NETHERITE_STACK = new ItemStack(Items.NETHERITE_INGOT);
	// Reusable stack for clock icon (for "X more messages")
	private static final ItemStack CLOCK_STACK = new ItemStack(Items.CLOCK);
	// Reusable stack for stick icon (for system messages)
	private static final ItemStack STICK_STACK = new ItemStack(Items.STICK);
	// Reusable stack for bed icon (for sleep-related messages)
	private static final ItemStack BED_STACK = new ItemStack(Items.RED_BED);
	
	// HTTP клиент для загрузки скинов через Mojang API (fallback для SkinsRestorer)
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	
	// Кэш для URL текстур скинов (имя игрока -> URL текстуры)
	private static final Map<String, String> skinUrlCache = new ConcurrentHashMap<>();
	
	// Кэш для UUID игроков (имя -> UUID)
	private static final Map<String, UUID> uuidCache = new ConcurrentHashMap<>();
	
	// Флаг загрузки для предотвращения дублирования запросов
	private static final Map<String, Boolean> loadingFlags = new ConcurrentHashMap<>();
	
	/**
	 * Класс для хранения одного сообщения в истории
	 */
	private static class ChatMessageEntry {
		Component message;
		PlayerInfo senderInfo;
		PlayerInfo receiverInfo; // для whisper сообщений - получатель (игрок, которому шепчут)
		boolean systemMessage;
		boolean isWhisper; // флаг whisper сообщения
		long timestamp; // время последнего обновления (для таймера скрытия)
		long firstMessageTime; // время первого появления (для анимации)
		int repeatCount = 1; // сколько раз подряд пришло такое же сообщение
		java.util.UUID senderUUID; // UUID отправителя для повторного поиска когда скин загрузится
		String senderName; // имя отправителя для повторного поиска PlayerInfo (важно для SkinsRestorer)
		boolean isSpam = false; // флаг спам-сообщения (паттерн с числами или чередующиеся)
		String messagePattern; // паттерн сообщения без чисел (для обнаружения спама)
		@SuppressWarnings("unused")
		boolean isCollapsed = false; // флаг сжатого сообщения (показывает "X more")
		@SuppressWarnings("unused")
		int collapsedCount = 0; // количество сжатых сообщений
		boolean isExpanded = false; // флаг развернутого повторяющегося сообщения (показывает все повторения отдельно)
		// Список сообщений, которые были сжаты в это сообщение (для разворачивания)
		// Используется когда repeatCount > 1, но сообщения могут быть разными
		List<ChatMessageEntry> expandedMessages = null;
		boolean isAchievement = false; // флаг сообщения о достижении
		boolean isChallenge = false; // флаг сообщения о завершении испытания
		boolean isError = false; // флаг сообщения об ошибке
		boolean isSleepMessage = false; // флаг сообщения о сне
		boolean isJoinMessage = false; // флаг сообщения о присоединении/выходе из игры (не показывать голову)
		boolean isScreenshot = false; // флаг сообщения о скриншоте
		
		ChatMessageEntry(Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo, boolean systemMessage, boolean isWhisper, String senderName) {
			this.message = message;
			this.senderInfo = senderInfo;
			this.receiverInfo = receiverInfo;
			this.systemMessage = systemMessage;
			this.isWhisper = isWhisper;
			this.senderName = senderName;
			long now = System.currentTimeMillis();
			this.timestamp = now;
			this.firstMessageTime = now;
			// Сохраняем UUID для повторного поиска PlayerInfo когда скин загрузится
			if (senderInfo != null) {
				this.senderUUID = senderInfo.getProfile().id();
			}
			// Извлекаем паттерн сообщения (без чисел) для обнаружения спама
			this.messagePattern = extractMessagePattern(message.getString());
		}
		
		/**
		 * Обновляет PlayerInfo для отправителя (используется когда скин загружается позже)
		 */
		void updateSenderInfo(PlayerInfo newInfo) {
			if (newInfo != null && (senderInfo == null || senderUUID == null || senderUUID.equals(newInfo.getProfile().id()))) {
				this.senderInfo = newInfo;
				if (senderUUID == null) {
					this.senderUUID = newInfo.getProfile().id();
				}
			}
		}
	}

	/**
	 * Player chat message – keeps full Component formatting and tries to
	 * resolve PlayerInfo for the head from the plain text (\"<Name> msg\" pattern).
	 * 
	 * @param component Сообщение от игрока
	 * @param sender GameProfile отправителя (может быть null для некоторых типов сообщений)
	 */
	public void onPlayerMessage(Component component, com.mojang.authlib.GameProfile sender) {
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
			handleWhisperMessage(component, rawOriginal);
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
			senderName = extractPlayerNameFromComponent(component);
		}
		
		// ПРИОРИТЕТ 3: Пытаемся извлечь имя из текста (для серверов с кастомным форматированием)
		// Только если GameProfile и Component не дали результата
		if (senderName == null) {
			senderName = extractPlayerNameFromText(rawOriginal);
		}
		
		// Fallback: try to detect vanilla "<Name> msg" pattern from plain text
		String bodyText = rawOriginal;
		if (senderName == null && rawOriginal.startsWith("<")) {
			int end = rawOriginal.indexOf('>');
			if (end > 1) {
				String potentialName = rawOriginal.substring(1, end);
				// Проверяем, что это похоже на ник (используем общий метод проверки)
				if (isValidPlayerName(potentialName)) {
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
							originalBodyComponent = applyLegacyColorCodes(bodyTextFromOriginal, ChatFormatting.WHITE);
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
						originalBodyComponent = applyLegacyColorCodes(bodyTextFromOriginal, ChatFormatting.WHITE);
					} else {
						originalBodyComponent = Component.literal(bodyTextFromOriginal)
								.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
					}
				}
				
				// Добавляем пробел между ником и текстом
				MutableComponent separator = Component.literal(" ")
						.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
				this.messageComponent = originalNickComponent.append(separator).append(originalBodyComponent);
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
					body = applyLegacyColorCodes(bodyText, ChatFormatting.WHITE);
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
				this.messageComponent = nick.append(separator).append(body);
			}
		} else if (hasLegacy) {
			// Нет ника, но есть &-коды — конвертим всё сообщение в стили
			this.messageComponent = applyLegacyColorCodes(rawOriginal);
		} else {
			// Уже отформатированный сервером Component — оставляем как есть.
			this.messageComponent = component;
		}

		this.systemMessage = false;

		// Resolve PlayerInfo by name or UUID (same idea as in Chat Heads suggestions mixin)
		this.senderInfo = null;
		if (sender != null && sender.id() != null) {
			// ПРИОРИТЕТ 1: Используем UUID из GameProfile для получения PlayerInfo
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.getConnection() != null) {
				this.senderInfo = minecraft.getConnection().getPlayerInfo(sender.id());
			}
		}
		
		// ПРИОРИТЕТ 2: Если PlayerInfo не найден по UUID, пытаемся найти по имени
		if (this.senderInfo == null && senderName != null && !senderName.isEmpty()) {
			this.senderInfo = getPlayerInfoByName(senderName);
		}
		
		// ПРИОРИТЕТ 3: Если есть GameProfile, но PlayerInfo еще не загружен, сохраняем имя для последующего поиска
		if (this.senderInfo == null && sender != null && sender.name() != null && senderName == null) {
			senderName = sender.name();
		}

		// Сохраняем в историю и определяем, было ли это повторяющееся сообщение
		// Передаем имя отправителя для повторного поиска когда скин загрузится (важно для SkinsRestorer)
		ChatMessageEntry entry = addToHistory(this.messageComponent, this.senderInfo, false, senderName);
		boolean isRepeat = entry != null && entry.repeatCount > 1;

		long now = System.currentTimeMillis();
		if (isRepeat) {
			// Только обновляем таймер скрытия, анимацию появления не трогаем
			this.lastMessageTime = now;
		} else {
			// Новое сообщение: инициализируем и анимацию, и таймер скрытия
			this.firstMessageTime = now;
			this.lastMessageTime = now;
		}
	}

	/**
	 * Обрабатывает whisper сообщения
	 */
	private void handleWhisperMessage(Component component, String raw) {
		
		// Парсим whisper сообщение
		// Форматы: "Player whispers to you: message" или "Player whispers: message"
		// Или на русском: "Игрок шепчет вам: сообщение" или "Игрок шепчет: сообщение"
		// Или когда игрок сам шепчет: "You whisper to Player: message" или "Вы прошептали Игроку: сообщение"
		
		String lowerRaw = raw.toLowerCase();
		Minecraft minecraft = Minecraft.getInstance();
		String playerName = minecraft.player != null ? minecraft.player.getName().getString() : "";
		// Если имя игрока пустое или содержит "Player" с цифрами (рандомный ник), используем фиксированное имя
		if (playerName.isEmpty() || playerName.matches("(?i)Player\\d+")) {
			playerName = DEFAULT_PLAYER_NAME;
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
							if (potentialName != null && isValidPlayerName(potentialName)) {
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
			messageComponent = applyLegacyColorCodes(messageText, ChatFormatting.WHITE);
		} else {
			messageComponent = Component.literal(messageText)
					.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
		}
		
		MutableComponent separator = Component.literal(" ")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
		
		this.messageComponent = whisperPrefix.append(nameComponent).append(separator).append(messageComponent);
		this.systemMessage = false;
		
		// Получаем PlayerInfo для отправителя и получателя
		PlayerInfo senderInfo = null;
		PlayerInfo receiverInfo = null;
		
		ClientPacketListener connection = minecraft.getConnection();
		if (connection != null) {
			if (isPlayerSending) {
				// Отправитель - текущий игрок
				if (playerName != null && !playerName.isEmpty()) {
					senderInfo = getPlayerInfoByName(playerName);
				}
				// Получатель - тот, кому шепчем
				if (receiverName != null && !receiverName.isEmpty()) {
					receiverInfo = getPlayerInfoByName(receiverName);
				}
			} else {
				// Отправитель - тот, кто шепчет
				if (senderName != null && !senderName.isEmpty()) {
					senderInfo = getPlayerInfoByName(senderName);
				}
				// Получатель - текущий игрок
				if (playerName != null && !playerName.isEmpty()) {
					receiverInfo = getPlayerInfoByName(playerName);
				}
			}
		}
		
		this.senderInfo = senderInfo;
		
		// Сохраняем в историю как whisper сообщение
		// Передаем имя отправителя для повторного поиска когда скин загрузится (важно для SkinsRestorer)
		ChatMessageEntry whisperEntry = addToHistoryWhisper(this.messageComponent, senderInfo, receiverInfo, senderName);
		boolean isRepeat = whisperEntry != null && whisperEntry.repeatCount > 1;
		
		long now = System.currentTimeMillis();
		if (isRepeat) {
			this.lastMessageTime = now;
		} else {
			this.firstMessageTime = now;
			this.lastMessageTime = now;
		}
	}

	/**
	 * System message – only Component, no player head.
	 * Всегда применяем серый цвет (§7) для системных сообщений.
	 */
	public void onSystemMessage(Component component) {
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
		
		String playerName = extractPlayerNameFromComponent(component);
		if (playerName == null) {
			playerName = extractPlayerNameFromText(raw);
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
					if (isValidPlayerName(potentialName)) {
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
				onPlayerMessage(component, null);
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
			handleWhisperMessage(component, raw);
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
				Component colorized = applyLegacyColorCodesForChallenge(raw);
				// Копируем оригинальные обработчики кликов из оригинального Component
				styled = copyClickHandlers(component, colorized);
			} else if (isAchievement) {
				// Для достижений применяем зеленый цвет, но сохраняем оригинальные обработчики
				Component colorized = applyLegacyColorCodesForAchievement(raw);
				styled = copyClickHandlers(component, colorized);
			} else {
				Component colorized = applyLegacyColorCodesForSystem(raw);
				styled = copyClickHandlers(component, colorized);
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

		this.messageComponent = styled;
		this.senderInfo = null;
		this.systemMessage = true;

		// Сохраняем в историю и определяем, было ли это повторяющееся сообщение
		ChatMessageEntry entry = addToHistory(this.messageComponent, null, true, null);
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
			this.lastMessageTime = now;
		} else {
			// Новое сообщение: инициализируем и анимацию, и таймер скрытия
			this.firstMessageTime = now;
			this.lastMessageTime = now;
		}
	}
	
	/**
	 * Извлекает имя игрока из начала текста сообщения
	 * Ник: от 2 до 16 символов, только английские буквы, цифры и подчеркивания
	 * Пропускает коды форматирования (& или § + символ)
	 */
	private String extractPlayerNameFromText(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		
		// Убираем пробелы в начале
		text = text.trim();
		if (text.isEmpty()) {
			return null;
		}
		
		// Список символов кодов форматирования
		String formatCodes = "0123456789abcdefklmnor";
		
		// Ищем последовательность символов, которая может быть ником
		// Ник начинается с буквы или подчеркивания, затем буквы, цифры и подчеркивания
		// Длина от 2 до 16 символов
		int start = 0;
		int end = 0;
		
		// Пропускаем коды форматирования и префиксы
		while (start < text.length()) {
			char c = text.charAt(start);
			
			// Пропускаем пробелы
			if (c == ' ') {
				start++;
				continue;
			}
			
			// Пропускаем коды форматирования (& или § + символ)
			if (c == '&' || c == '§') {
				if (start + 1 < text.length()) {
					char codeChar = text.charAt(start + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						start += 2; // Пропускаем &/§ и символ кода
						continue;
					}
				}
			}
			
			// Пропускаем префиксы в квадратных скобках (может не быть)
			if (c == '[') {
				int bracketEnd = text.indexOf(']', start);
				if (bracketEnd != -1) {
					start = bracketEnd + 1;
					// Пропускаем пробелы после скобки
					while (start < text.length() && text.charAt(start) == ' ') {
						start++;
					}
					continue;
				} else {
					break;
				}
			}
			
			// Если дошли сюда, значит нашли начало потенциального ника
			break;
		}
		
		// Проверяем, что есть место для ника
		if (start >= text.length()) {
			return null;
		}
		
		// Пропускаем оставшиеся коды форматирования перед ником
		while (start < text.length()) {
			char c = text.charAt(start);
			if (c == '&' || c == '§') {
				if (start + 1 < text.length()) {
					char codeChar = text.charAt(start + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						start += 2;
						continue;
					}
				}
			}
			break;
		}
		
		// Проверяем, начинается ли с буквы или подчеркивания
		if (start >= text.length()) {
			return null;
		}
		
		char firstChar = text.charAt(start);
		
		// Проверяем, что это не эмодзи или специальный символ Unicode
		// Эмодзи обычно находятся в диапазонах: U+1F300-1F9FF, U+2600-26FF, U+2700-27BF и т.д.
		if (firstChar >= 0x1F300 && firstChar <= 0x1F9FF) { // Эмодзи
			return null;
		}
		if (firstChar >= 0x2600 && firstChar <= 0x26FF) { // Разные символы
			return null;
		}
		if (firstChar >= 0x2700 && firstChar <= 0x27BF) { // Dingbats
			return null;
		}
		
		// Проверяем, что это буква или подчеркивание (не эмодзи и не специальные символы)
		if (!((firstChar >= 'a' && firstChar <= 'z') || (firstChar >= 'A' && firstChar <= 'Z') || firstChar == '_')) {
			// Если это не ASCII символ и не буква/подчеркивание - пропускаем
			if (firstChar > 127) {
				return null;
			}
			return null;
		}
		
		// Ищем конец ника (до пробела, двоеточия, стрелок, кодов форматирования и т.д.)
		end = start + 1;
		int nameCharCount = 1; // Считаем только символы ника (без кодов форматирования)
		
		while (end < text.length() && nameCharCount < 16) {
			char c = text.charAt(end);
			
			// Пропускаем коды форматирования внутри ника
			if (c == '&' || c == '§') {
				if (end + 1 < text.length()) {
					char codeChar = text.charAt(end + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						end += 2; // Пропускаем &/§ и символ кода
						continue;
					}
				}
			}
			
			// Если это символ ника (буква, цифра, подчеркивание)
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				end++;
				nameCharCount++;
			} else {
				// Встретили символ, который не является частью ника
				break;
			}
		}
		
		// Извлекаем ник, удаляя коды форматирования
		StringBuilder nameBuilder = new StringBuilder();
		for (int i = start; i < end; i++) {
			char c = text.charAt(i);
			if (c == '&' || c == '§') {
				if (i + 1 < text.length()) {
					char codeChar = text.charAt(i + 1);
					if (formatCodes.indexOf(Character.toLowerCase(codeChar)) != -1) {
						i++; // Пропускаем символ кода
						continue;
					}
				}
			}
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				nameBuilder.append(c);
			}
		}
		
		String name = nameBuilder.toString();
		
		// Используем общий метод проверки валидности ника
		if (isValidPlayerName(name)) {
			return name;
		}
		
		return null;
	}
	
	/**
	 * Проверяет, является ли строка валидным ником игрока
	 * Ник должен:
	 * - Быть длиной от 2 до 16 символов
	 * - Содержать только буквы, цифры и подчеркивания
	 * - Содержать хотя бы одну букву (не только цифры)
	 * - Не содержать эмодзи или специальные Unicode символы
	 * - Не состоять только из подчеркиваний
	 */
	private boolean isValidPlayerName(String name) {
		if (name == null || name.length() < 2 || name.length() > 16) {
			return false;
		}
		
		// Проверяем базовый формат (только буквы, цифры, подчеркивания)
		if (!name.matches("^[a-zA-Z0-9_]+$") || name.matches("^_+$")) {
			return false;
		}
		
		// Проверяем, что имя содержит хотя бы одну букву (не только цифры)
		boolean hasLetter = false;
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
				hasLetter = true;
				break;
			}
		}
		
		if (!hasLetter) {
			return false;
		}
		
		// Проверяем на эмодзи и специальные Unicode символы
		for (int i = 0; i < name.length(); i++) {
			char ch = name.charAt(i);
			// Проверяем на эмодзи и специальные символы Unicode
			if (ch >= 0x1F300 && ch <= 0x1F9FF) { // Эмодзи
				return false;
			}
			if (ch >= 0x2600 && ch <= 0x26FF) { // Разные символы
				return false;
			}
			if (ch >= 0x2700 && ch <= 0x27BF) { // Dingbats
				return false;
			}
			// Проверяем на другие не-ASCII символы, которые не являются буквами/цифрами/подчеркиванием
			if (ch > 127 && !((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || 
			                  (ch >= '0' && ch <= '9') || ch == '_')) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Извлекает имя игрока из стилизованного Component
	 * Проверяет siblings и translatable arguments для определения ника
	 */
	private String extractPlayerNameFromComponent(Component component) {
		if (component == null) {
			return null;
		}
		
		// Сначала пробуем извлечь из полного текста Component
		String fullText = component.getString();
		String nameFromText = extractPlayerNameFromText(fullText);
		if (nameFromText != null) {
			return nameFromText;
		}
		
		// Проверяем содержимое самого компонента
		if (component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents plainText) {
			String text = plainText.text();
			// Проверяем паттерн "<Name>" в начале
			if (text.startsWith("<") && text.length() > 2) {
				int end = text.indexOf('>');
				if (end > 1 && end < text.length()) {
					String name = text.substring(1, end);
					// Проверяем, что это похоже на имя игрока (от 2 до 16 символов, только буквы, цифры, подчеркивания)
					if (name.length() >= 2 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$")) {
						return name;
					}
				}
			}
			// Пробуем извлечь ник из начала текста
			String name = extractPlayerNameFromText(text);
			if (name != null) {
				return name;
			}
		}
		
		// Проверяем siblings компонента
		for (Component sibling : component.getSiblings()) {
			String name = extractPlayerNameFromComponent(sibling);
			if (name != null) {
				return name;
			}
		}
		
		// Проверяем translatable arguments
		if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
			Object[] args = translatable.getArgs();
			if (args != null && args.length > 0) {
				// Первый аргумент часто содержит имя игрока
				for (Object arg : args) {
					if (arg instanceof Component argComponent) {
						String name = extractPlayerNameFromComponent(argComponent);
						if (name != null) {
							return name;
						}
						// Также проверяем строковое представление
						String argText = argComponent.getString();
						if (!argText.isEmpty() && argText.length() <= 16 && !argText.contains(" ") && 
						    !argText.contains("<") && !argText.contains(">")) {
							// Проверяем, что это похоже на имя игрока (только буквы, цифры, подчеркивания)
							if (argText.matches("^[a-zA-Z0-9_]+$")) {
								return argText;
							}
						}
					} else if (arg instanceof String argStr) {
						// Проверяем строковый аргумент
						if (!argStr.isEmpty() && argStr.length() <= 16 && !argStr.contains(" ") &&
						    !argStr.contains("<") && !argStr.contains(">")) {
							if (argStr.matches("^[a-zA-Z0-9_]+$")) {
								return argStr;
							}
						}
					}
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Извлекает паттерн сообщения (без чисел) для обнаружения спама
	 * Например: "блабла бла 5 блабла бла" -> "блабла бла N блабла бла"
	 */
	private static String extractMessagePattern(String messageText) {
		if (messageText == null || messageText.isEmpty()) {
			return "";
		}
		// Заменяем все числа на "N" для сравнения паттернов
		return messageText.replaceAll("\\d+", "N");
	}
	
	/**
	 * Проверяет, является ли сообщение спамом с паттерном чисел
	 * Например: "блабла бла 5", "блабла бла 4", "блабла бла 3"
	 */
	private static boolean isNumberPatternSpam(String pattern1, String pattern2) {
		if (pattern1 == null || pattern2 == null || pattern1.isEmpty() || pattern2.isEmpty()) {
			return false;
		}
		// Если паттерны совпадают (без учета чисел), это может быть спам
		return pattern1.equals(pattern2) && pattern1.contains("N");
	}
	
	/**
	 * Проверяет, является ли сообщение повторяющимся спамом в последних 5 сообщениях
	 * Если паттерн сообщения повторяется в ближайших 5 сообщениях от того же отправителя - это спам
	 * Например: сообщение A, сообщение B, сообщение C, сообщение A (через 2)
	 * Или: сообщение A, B, C, D, E, A (через 5)
	 */
	private static boolean isAlternatingSpam(List<ChatMessageEntry> history, ChatMessageEntry newEntry, int checkDepth) {
		if (history.size() < 1 || checkDepth < 1) {
			return false;
		}
		
		// Получаем паттерн нового сообщения
		String newPattern = newEntry.messagePattern;
		if (newPattern == null || newPattern.isEmpty()) {
			return false;
		}
		
		// Проверяем последние checkDepth сообщений (по умолчанию 5)
		int startIndex = Math.max(0, history.size() - checkDepth);
		int matchCount = 0; // Счетчик совпадений паттерна
		
		for (int i = startIndex; i < history.size(); i++) {
			ChatMessageEntry entry = history.get(i);
			if (entry == null || entry.messagePattern == null) {
				continue;
			}
			
			// Проверяем, от того же отправителя
			boolean sameSender = false;
			if (newEntry.senderInfo == null && entry.senderInfo == null) {
				sameSender = true;
			} else if (newEntry.senderInfo != null && entry.senderInfo != null) {
				sameSender = newEntry.senderInfo.getProfile().id().equals(entry.senderInfo.getProfile().id());
			}
			
			if (!sameSender) {
				continue; // Разные отправители - пропускаем
			}
			
			// Если паттерн совпадает - увеличиваем счетчик
			if (newPattern.equals(entry.messagePattern)) {
				matchCount++;
			}
		}
		
		// Если паттерн повторился хотя бы один раз в последних 5 сообщениях от того же отправителя - это спам
		// (не считаем первое вхождение, так как это может быть просто повтор)
		return matchCount >= 1;
	}

	/**
	 * Добавляет сообщение в историю чата с указанием имени отправителя
	 * Возвращает ChatMessageEntry для возможности обновления PlayerInfo позже
	 */
	private ChatMessageEntry addToHistory(Component message, PlayerInfo senderInfo, boolean systemMessage, String senderName) {
		if (message == null) {
			return null;
		}

		String messageText = message.getString();
		String messagePattern = extractMessagePattern(messageText);

		// Если новое сообщение такое же, как предыдущее, просто увеличиваем счётчик x2/x3 и не добавляем новый элемент
		if (!messageHistory.isEmpty()) {
			ChatMessageEntry last = messageHistory.get(messageHistory.size() - 1);

			boolean sameSystemFlag = last.systemMessage == systemMessage;

			boolean sameSender;
			if (last.senderInfo == null && senderInfo == null) {
				sameSender = true;
			} else if (last.senderInfo != null && senderInfo != null) {
				sameSender = last.senderInfo.getProfile().id().equals(senderInfo.getProfile().id());
			} else {
				sameSender = false;
			}

			boolean sameText = last.message.getString().equals(messageText);
			
			// Проверка на спам с паттерном чисел (например: "блабла бла 5", "блабла бла 4")
			boolean isNumberSpam = sameSender && sameSystemFlag && 
				isNumberPatternSpam(last.messagePattern, messagePattern);

			if (sameSystemFlag && sameSender && sameText && ENABLE_MESSAGE_DUPLICATION) {
				last.repeatCount++;
				last.timestamp = System.currentTimeMillis();
				// Сохраняем каждое повторяющееся сообщение для разворачивания
				if (last.expandedMessages == null) {
					last.expandedMessages = new ArrayList<>();
					// Добавляем первое сообщение
					last.expandedMessages.add(new ChatMessageEntry(last.message, last.senderInfo, last.receiverInfo, last.systemMessage, last.isWhisper, last.senderName));
				}
				// Добавляем текущее сообщение (даже если оно такое же, сохраняем его отдельно)
				ChatMessageEntry newEntry = new ChatMessageEntry(message, senderInfo, null, systemMessage, false, senderName);
				last.expandedMessages.add(newEntry);
				// Обновляем PlayerInfo если он был null, а теперь доступен
				if (last.senderInfo == null && senderInfo != null) {
					last.updateSenderInfo(senderInfo);
				}
				// Обновляем имя если оно было null
				if (last.senderName == null && senderName != null) {
					last.senderName = senderName;
				}
				return last;
			} else if (isNumberSpam) {
				// Обнаружен спам с паттерном чисел - обновляем существующее сообщение
				last.message = message; // Обновляем текст сообщения
				last.repeatCount++;
				last.timestamp = System.currentTimeMillis();
				last.isSpam = true;
				// Обновляем PlayerInfo если он был null, а теперь доступен
				if (last.senderInfo == null && senderInfo != null) {
					last.updateSenderInfo(senderInfo);
				}
				// Обновляем имя если оно было null
				if (last.senderName == null && senderName != null) {
					last.senderName = senderName;
				}
				// Перемещаем в конец списка (опускаем вниз)
				moveToEnd(messageHistory, last);
				return last;
			}
		}

		ChatMessageEntry entry = new ChatMessageEntry(message, senderInfo, null, systemMessage, false, senderName);
		
		// Проверяем на повторяющийся спам в последних 5 сообщениях
		if (isAlternatingSpam(messageHistory, entry, 5)) {
			entry.isSpam = true;
		}
		
		messageHistory.add(entry);
		
		// Если это спам, перемещаем в конец списка
		if (entry.isSpam) {
			moveToEnd(messageHistory, entry);
		}
		
		// Ограничиваем размер истории
		trimMessageHistory();
		
		// Сбрасываем прокрутку при новом сообщении (показываем последние сообщения)
		scrollOffset = 0;
		return entry;
	}
	
	/**
	 * Перемещает запись в конец списка (опускает спам вниз)
	 */
	private static void moveToEnd(List<ChatMessageEntry> history, ChatMessageEntry entry) {
		int index = history.indexOf(entry);
		if (index >= 0 && index < history.size() - 1) {
			history.remove(index);
			history.add(entry);
		}
	}

	private void trimMessageHistory() {
		while (messageHistory.size() > maxHistorySize) {
			messageHistory.remove(0);
		}
	}

	/**
	 * Добавляет whisper сообщение в историю чата
	 * Возвращает ChatMessageEntry для возможности обновления PlayerInfo позже
	 */
	private ChatMessageEntry addToHistoryWhisper(Component message, PlayerInfo senderInfo, PlayerInfo receiverInfo, String senderName) {
		if (message == null) {
			return null;
		}

		String messageText = message.getString();
		String messagePattern = extractMessagePattern(messageText);

		// Если новое сообщение такое же, как предыдущее, просто увеличиваем счётчик
		if (!messageHistory.isEmpty()) {
			ChatMessageEntry last = messageHistory.get(messageHistory.size() - 1);

			if (last.isWhisper) {
				boolean sameSender;
				if (last.senderInfo == null && senderInfo == null) {
					sameSender = true;
				} else if (last.senderInfo != null && senderInfo != null) {
					sameSender = last.senderInfo.getProfile().id().equals(senderInfo.getProfile().id());
				} else {
					sameSender = false;
				}

				boolean sameText = last.message.getString().equals(messageText);
				
				// Проверка на спам с паттерном чисел для whisper сообщений
				boolean isNumberSpam = sameSender && 
					isNumberPatternSpam(last.messagePattern, messagePattern);

				if (sameSender && sameText && ENABLE_MESSAGE_DUPLICATION) {
					last.repeatCount++;
					last.timestamp = System.currentTimeMillis();
					// Сохраняем каждое повторяющееся сообщение для разворачивания
					if (last.expandedMessages == null) {
						last.expandedMessages = new ArrayList<>();
						// Добавляем первое сообщение
						last.expandedMessages.add(new ChatMessageEntry(last.message, last.senderInfo, last.receiverInfo, last.systemMessage, last.isWhisper, last.senderName));
					}
					// Добавляем текущее сообщение (даже если оно такое же, сохраняем его отдельно)
					ChatMessageEntry newEntry = new ChatMessageEntry(message, senderInfo, receiverInfo, false, true, senderName);
					last.expandedMessages.add(newEntry);
					// Обновляем PlayerInfo если он был null, а теперь доступен
					if (last.senderInfo == null && senderInfo != null) {
						last.updateSenderInfo(senderInfo);
					}
					// Обновляем имя если оно было null
					if (last.senderName == null && senderName != null) {
						last.senderName = senderName;
					}
					return last;
				} else if (isNumberSpam) {
					// Обнаружен спам с паттерном чисел - обновляем существующее сообщение
					last.message = message; // Обновляем текст сообщения
					last.repeatCount++;
					last.timestamp = System.currentTimeMillis();
					last.isSpam = true;
					// Обновляем PlayerInfo если он был null, а теперь доступен
					if (last.senderInfo == null && senderInfo != null) {
						last.updateSenderInfo(senderInfo);
					}
					// Обновляем имя если оно было null
					if (last.senderName == null && senderName != null) {
						last.senderName = senderName;
					}
					// Перемещаем в конец списка (опускаем вниз)
					moveToEnd(messageHistory, last);
					return last;
				}
			}
		}

		ChatMessageEntry entry = new ChatMessageEntry(message, senderInfo, receiverInfo, false, true, senderName);
		
		// Проверяем на повторяющийся спам в последних 5 сообщениях для whisper сообщений
		if (isAlternatingSpam(messageHistory, entry, 5)) {
			entry.isSpam = true;
		}
		
		messageHistory.add(entry);
		
		// Если это спам, перемещаем в конец списка
		if (entry.isSpam) {
			moveToEnd(messageHistory, entry);
		}
		
		// Ограничиваем размер истории
		trimMessageHistory();
		
		// Сбрасываем прокрутку при новом сообщении
		scrollOffset = 0;
		return entry;
	}
	
	/**
	 * Обрабатывает прокрутку колесиком мыши
	 */
	public void handleMouseScroll(double delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof ChatScreen) {
			// Прокрутка вверх (delta > 0) - показываем более старые сообщения
			// Прокрутка вниз (delta < 0) - показываем более новые сообщения
			if (delta > 0) {
				scrollOffset = Math.min(scrollOffset + SCROLL_SPEED, messageHistory.size() - 1);
			} else if (delta < 0) {
				scrollOffset = Math.max(scrollOffset - SCROLL_SPEED, 0);
			}
		}
	}

	public void onHudRender(GuiGraphics guiGraphics) {
		// Показываем чат только если он включен
		// Но сообщения продолжают сохраняться даже когда чат выключен
		if (!enabled) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}

		// Обновляем PlayerInfo для сообщений, у которых скин еще не загрузился
		// Это критично для поддержки SkinsRestorer плагина, так как скины загружаются асинхронно
		updateMissingPlayerInfos(minecraft);

		// Всегда рисуем наш HUD (ранее проверяли конфиг)
		
		// Проверяем, открыт ли чат
		boolean chatOpen = minecraft.screen instanceof ChatScreen;
		
		if (chatOpen) {
			// Когда чат открыт, показываем историю сообщений
			renderChatHistory(guiGraphics, minecraft);
			// Рендерим tooltip при наведении на сообщение о достижении
			if (minecraft.screen != null) {
				double mouseX = minecraft.mouseHandler.xpos();
				double mouseY = minecraft.mouseHandler.ypos();
				renderTooltip(guiGraphics, (int)mouseX, (int)mouseY);
			}
			return;
		}
		
		// Когда чат закрыт, показываем несколько последних сообщений (пока помещаются на экране)
		if (messageHistory.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		Font font = minecraft.font;
		
		// Рендерим последние сообщения снизу вверх, пока они помещаются на экране
		int currentY = screenHeight - BOTTOM_OFFSET;
		int spacing = 2; // Расстояние между сообщениями
		int minY = 20; // Минимальная высота от верха экрана
		
		long displayTimeMs = displayTimeSeconds * 1000L;

		// Проходим по истории с конца (последние сообщения)
		for (int i = messageHistory.size() - 1; i >= 0 && currentY > minY; i--) {
			ChatMessageEntry entry = messageHistory.get(i);
			if (entry == null || entry.message == null) {
				continue;
			}

			float alpha = alphaForDisplayDuration(entry, now);
			if (alpha < 0f) {
				break;
			}

			// Рендерим сообщение
			int messageHeight = renderSingleMessage(guiGraphics, font, screenWidth, currentY, entry, alpha);
			
			// Перемещаемся вверх для следующего сообщения
			currentY -= messageHeight + spacing;
			
			// Если сообщение не поместилось, останавливаемся
			if (currentY <= minY) {
				break;
			}
		}
		
		// Очищаем messageComponent если все сообщения истекли
		if (messageHistory.isEmpty() ||
			(now - messageHistory.get(messageHistory.size() - 1).timestamp > displayTimeMs)) {
			messageComponent = null;
			senderInfo = null;
			systemMessage = false;
		}
	}

	/**
	 * Рендерит историю сообщений когда чат открыт
	 */
	private void renderChatHistory(GuiGraphics guiGraphics, Minecraft minecraft) {
		if (messageHistory.isEmpty()) {
			return;
		}
		
		// Очищаем границы сообщений перед рендерингом
		messageBounds.clear();
		
		Font font = minecraft.font;
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		long now = System.currentTimeMillis();
		// При прокрутке вверх показываем историю без таймера — иначе нельзя прочитать старые строки
		boolean timeLimitActive = scrollOffset == 0;
		
		// Определяем сколько сообщений показывать (с учетом прокрутки)
		int startIndex = Math.max(0, messageHistory.size() - scrollOffset - maxVisibleMessages);
		int endIndex = messageHistory.size() - scrollOffset;
		
		if (startIndex >= endIndex || startIndex < 0) {
			return;
		}
		
		// Рендерим сообщения снизу вверх
		int currentY = screenHeight - BOTTOM_OFFSET;
		int spacing = 2; // Расстояние между сообщениями
		
		// Если сообщений больше MAX_VISIBLE_MESSAGES и они не развернуты, показываем сжатое сообщение
		if (startIndex > 0 && !collapsedMessagesExpanded) {
			// Создаем сжатое сообщение
			ChatMessageEntry collapsedEntry = createCollapsedEntry(startIndex);
			if (collapsedEntry != null) {
				// Рендерим сжатое сообщение
				int messageHeight = renderSingleMessage(guiGraphics, font, screenWidth, currentY, collapsedEntry, 1.0f);
				
				// Сохраняем границы сжатого сообщения (используем -1 как специальный индекс)
				int boxWidth = calculateMessageWidth(font, collapsedEntry.message);
				int x = (screenWidth - boxWidth) / 2;
				messageBounds.put(-1, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, -1));
				
				currentY -= messageHeight + spacing;
			}
		} else if (startIndex > 0 && collapsedMessagesExpanded) {
			// Если развернуто, показываем все сжатые сообщения (от startIndex-1 до 0, снизу вверх)
			// Показываем все сообщения, даже если они не помещаются на экране
			for (int j = startIndex - 1; j >= 0; j--) {
				ChatMessageEntry collapsedMsg = messageHistory.get(j);
				if (collapsedMsg == null || collapsedMsg.message == null) {
					continue;
				}
				float lineAlpha = timeLimitActive ? alphaForDisplayDuration(collapsedMsg, now) : 1.0f;
				if (timeLimitActive && lineAlpha < 0f) {
					break;
				}
				// Проверяем, не вышли ли мы за пределы экрана (но все равно рендерим для прокрутки)
				if (currentY <= 0) {
					break; // Прекращаем рендеринг, если вышли за пределы экрана
				}
				int msgHeight = renderSingleMessage(guiGraphics, font, screenWidth, currentY, collapsedMsg, lineAlpha);
				int msgWidth = calculateMessageWidth(font, collapsedMsg.message);
				int msgX = (screenWidth - msgWidth) / 2;
				messageBounds.put(j, new MessageBounds(msgX, currentY - msgHeight, msgWidth, msgHeight, j));
				currentY -= msgHeight + spacing;
			}
		}
		
		// Рендерим оставшиеся сообщения снизу вверх
		for (int i = endIndex - 1; i >= startIndex && currentY > 0; i--) {
			ChatMessageEntry entry = messageHistory.get(i);
			if (entry == null || entry.message == null) {
				continue;
			}
			
			// Если сообщение развернуто и имеет повторения, показываем все повторения отдельно
			if (entry.isExpanded && entry.repeatCount > 1) {
				// Если есть список развернутых сообщений, показываем их все
				if (entry.expandedMessages != null && !entry.expandedMessages.isEmpty()) {
					for (int repeat = 0; repeat < entry.expandedMessages.size() && currentY > 0; repeat++) {
						ChatMessageEntry expandedEntry = entry.expandedMessages.get(repeat);
						if (expandedEntry != null && expandedEntry.message != null) {
							float lineAlpha = timeLimitActive ? alphaForDisplayDuration(expandedEntry, now) : 1.0f;
							if (timeLimitActive && lineAlpha < 0f) {
								break;
							}
							int messageHeight = renderSingleMessage(guiGraphics, font, screenWidth, currentY, expandedEntry, lineAlpha);
							int boxWidth = calculateMessageWidth(font, expandedEntry.message);
							int x = (screenWidth - boxWidth) / 2;
							// Используем специальный индекс для повторений: i * 1000 + repeat
							messageBounds.put(i * 1000 + repeat, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, i));
							currentY -= messageHeight + spacing;
						}
					}
				} else {
					float entryAlpha = timeLimitActive ? alphaForDisplayDuration(entry, now) : 1.0f;
					if (timeLimitActive && entryAlpha < 0f) {
						break;
					}
					// Если списка нет, показываем сообщение repeatCount раз подряд (fallback)
					for (int repeat = 0; repeat < entry.repeatCount && currentY > 0; repeat++) {
						int messageHeight = renderSingleMessage(guiGraphics, font, screenWidth, currentY, entry, entryAlpha);
						int boxWidth = calculateMessageWidth(font, entry.message);
						int x = (screenWidth - boxWidth) / 2;
						// Используем специальный индекс для повторений: i * 1000 + repeat
						messageBounds.put(i * 1000 + repeat, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, i));
						currentY -= messageHeight + spacing;
					}
				}
			} else {
				float entryAlpha = timeLimitActive ? alphaForDisplayDuration(entry, now) : 1.0f;
				if (timeLimitActive && entryAlpha < 0f) {
					break;
				}
				// Рендерим одно сообщение
				int messageHeight = renderSingleMessage(guiGraphics, font, screenWidth, currentY, entry, entryAlpha);
				int boxWidth = calculateMessageWidth(font, entry.message);
				int x = (screenWidth - boxWidth) / 2;
				messageBounds.put(i, new MessageBounds(x, currentY - messageHeight, boxWidth, messageHeight, i));
				currentY -= messageHeight + spacing;
			}
		}
	}
	
	/**
	 * Создает сжатое сообщение для старых сообщений
	 */
	private ChatMessageEntry createCollapsedEntry(int collapsedCount) {
		if (collapsedCount <= 0) {
			return null;
		}
		
		// Создаем сообщение "X more messages" (на русском: "X сообщений")
		String collapsedText = collapsedCount + " more messages";
		if (collapsedCount == 1) {
			collapsedText = "1 more message";
		}
		MutableComponent collapsedMessage = Component.literal(collapsedText)
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
		
		ChatMessageEntry entry = new ChatMessageEntry(collapsedMessage, null, null, true, false, null);
		entry.isCollapsed = true;
		entry.collapsedCount = collapsedCount;
		return entry;
	}
	
	// Флаг для открытия экрана достижений в следующем тике
	private boolean shouldOpenAdvancements = false;
	
	public boolean shouldOpenAdvancements() {
		return shouldOpenAdvancements;
	}
	
	public void setShouldOpenAdvancements(boolean value) {
		shouldOpenAdvancements = value;
	}
	
	/**
	 * Обрабатывает клик по сообщению
	 */
	public boolean handleMessageClick(double mouseX, double mouseY) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.screen instanceof ChatScreen)) {
			return false;
		}
		
		int mx = (int)mouseX;
		int my = (int)mouseY;
		
		// Проверяем клик по сжатому сообщению ("X more messages")
		MessageBounds collapsedBounds = messageBounds.get(-1);
		if (collapsedBounds != null && collapsedBounds.contains(mx, my)) {
			// Переключаем состояние развернутости
			collapsedMessagesExpanded = !collapsedMessagesExpanded;
			// Очищаем границы, чтобы они пересчитались при следующем рендеринге
			messageBounds.clear();
			return true;
		}
		
		// Проверяем клик по повторяющемуся сообщению (x2, x3, x4 и т.д.)
		for (Map.Entry<Integer, MessageBounds> boundsEntry : messageBounds.entrySet()) {
			int index = boundsEntry.getKey();
			MessageBounds bounds = boundsEntry.getValue();
			
			// Пропускаем сжатое сообщение (уже обработано выше)
			if (index == -1) {
				continue;
			}
			
			// Проверяем, является ли это обычным сообщением (не повторением)
			// Повторения имеют индекс >= 1000
			if (index < 1000 && bounds.contains(mx, my)) {
				int historyIndex = bounds.historyIndex;
				if (historyIndex >= 0 && historyIndex < messageHistory.size()) {
					ChatMessageEntry entry = messageHistory.get(historyIndex);
					if (entry != null) {
						// Если это сообщение о достижении, открываем экран достижений
						if (entry.isAchievement && entry.message != null) {
							// Устанавливаем флаг для открытия экрана достижений
							shouldOpenAdvancements = true;
							return true;
						}
						// Если это повторяющееся сообщение, переключаем развернутость
						if (entry.repeatCount > 1) {
							// Переключаем состояние развернутости для этого сообщения
							entry.isExpanded = !entry.isExpanded;
							// Очищаем границы, чтобы они пересчитались при следующем рендеринге
							messageBounds.clear();
							return true;
						}
						
						// Обрабатываем клик по тексту сообщения (ссылки, команды и т.д.)
						if (bounds.component != null && bounds.textX > 0 && bounds.textY > 0) {
							// Проверяем, кликнули ли по тексту (а не по аватару или фону)
							if (mx >= bounds.textX && mx < bounds.textX + bounds.maxTextWidth &&
							    my >= bounds.textY && my < bounds.textY + bounds.height) {
								// Определяем позицию символа в тексте
								Font font = minecraft.font;
								int relativeX = mx - bounds.textX;
								int relativeY = my - bounds.textY;
								
								// Находим строку по Y координате
								List<FormattedCharSequence> lines = font.split(bounds.component, bounds.maxTextWidth);
								int lineIndex = relativeY / font.lineHeight;
								if (lineIndex >= 0 && lineIndex < lines.size()) {
									FormattedCharSequence line = lines.get(lineIndex);
									
									// Используем StringSplitter для определения Style в позиции клика
									net.minecraft.client.StringSplitter splitter = font.getSplitter();
									Style style = splitter.componentStyleAtWidth(line, relativeX);
									
									if (style != null) {
										ClickEvent clickEvent = style.getClickEvent();
										if (clickEvent != null) {
											// Выполняем действие из ClickEvent
											handleClickEvent(clickEvent);
											return true;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Получает Style из Component в указанной позиции символа
	 */
	private Style getStyleAtPosition(Component component, int position) {
		if (component == null || position < 0) {
			return Style.EMPTY;
		}
		
		// Проверяем текущий компонент
		int currentLength = component.getString().length();
		if (position < currentLength) {
			return component.getStyle();
		}
		
		// Проверяем siblings
		int offset = currentLength;
		for (Component sibling : component.getSiblings()) {
			int siblingLength = sibling.getString().length();
			if (position < offset + siblingLength) {
				return getStyleAtPosition(sibling, position - offset);
			}
			offset += siblingLength;
		}
		
		// Если позиция за пределами компонента, возвращаем стиль последнего символа
		return component.getStyle();
	}
	
	/**
	 * Обрабатывает ClickEvent из Component
	 */
	private void handleClickEvent(ClickEvent clickEvent) {
		if (clickEvent == null) {
			return;
		}
		
		Minecraft minecraft = Minecraft.getInstance();
		// Получаем action и value из ClickEvent через рефлексию
		// Это необходимо, так как API может отличаться в разных версиях Minecraft
		ClickEvent.Action action = null;
		String value = null;
		
		try {
			// Получаем action через рефлексию
			try {
				java.lang.reflect.Method actionMethod = clickEvent.getClass().getMethod("action");
				action = (ClickEvent.Action) actionMethod.invoke(clickEvent);
			} catch (Exception e1) {
				try {
					java.lang.reflect.Method getActionMethod = clickEvent.getClass().getMethod("getAction");
					action = (ClickEvent.Action) getActionMethod.invoke(clickEvent);
				} catch (Exception e2) {
					try {
						java.lang.reflect.Field actionField = clickEvent.getClass().getField("action");
						action = (ClickEvent.Action) actionField.get(clickEvent);
					} catch (Exception e3) {
						// Не удалось получить action
					}
				}
			}
			
			// Получаем value через рефлексию
			try {
				java.lang.reflect.Method valueMethod = clickEvent.getClass().getMethod("value");
				value = (String) valueMethod.invoke(clickEvent);
			} catch (Exception e1) {
				try {
					java.lang.reflect.Method getValueMethod = clickEvent.getClass().getMethod("getValue");
					value = (String) getValueMethod.invoke(clickEvent);
				} catch (Exception e2) {
					try {
						java.lang.reflect.Field valueField = clickEvent.getClass().getField("value");
						value = (String) valueField.get(clickEvent);
					} catch (Exception e3) {
						// Не удалось получить value
					}
				}
			}
		} catch (Exception e) {
			// Игнорируем ошибки рефлексии
		}
		
		if (action == null || value == null || value.isEmpty()) {
			return;
		}
		
		switch (action) {
			case OPEN_URL:
				// Открываем URL в браузере
				if (minecraft.options.chatLinks().get()) {
					try {
						java.net.URI uri = new java.net.URI(value);
						java.awt.Desktop.getDesktop().browse(uri);
					} catch (Exception e) {
						// Если не удалось открыть, копируем в буфер обмена
						minecraft.keyboardHandler.setClipboard(value);
					}
				}
				break;
			case OPEN_FILE:
				// Открываем файл
				try {
					java.io.File file = new java.io.File(value);
					if (file.exists()) {
						java.awt.Desktop.getDesktop().open(file);
					}
				} catch (Exception e) {
					// Игнорируем ошибки
				}
				break;
			case RUN_COMMAND:
			case SUGGEST_COMMAND:
				// Выполняем команду или предлагаем её в чате
				if (minecraft.player != null) {
					if (action == ClickEvent.Action.RUN_COMMAND) {
						// Выполняем команду
						minecraft.player.connection.sendCommand(value);
					} else {
						// Предлагаем команду в чате
						if (minecraft.screen instanceof ChatScreen) {
							((ChatScreen) minecraft.screen).handleChatInput(value, true);
						}
					}
				}
				break;
			case COPY_TO_CLIPBOARD:
				// Копируем в буфер обмена
				minecraft.keyboardHandler.setClipboard(value);
				break;
			default:
				break;
		}
	}
	
	// Хранит текущее сообщение под курсором для tooltip
	private ChatMessageEntry hoveredEntry = null;
	
	/**
	 * Обрабатывает наведение мыши для показа tooltip
	 */
	public void handleMouseMove(double mouseX, double mouseY) {
		hoveredEntry = null;
		int mx = (int)mouseX;
		int my = (int)mouseY;
		
		// Проверяем, находится ли курсор над сообщением о достижении
		for (Map.Entry<Integer, MessageBounds> boundsEntry : messageBounds.entrySet()) {
			int index = boundsEntry.getKey();
			MessageBounds bounds = boundsEntry.getValue();
			
			// Пропускаем сжатое сообщение и повторения
			if (index == -1 || index >= 1000) {
				continue;
			}
			
			if (index < 1000 && bounds.contains(mx, my)) {
				int historyIndex = bounds.historyIndex;
				if (historyIndex >= 0 && historyIndex < messageHistory.size()) {
					ChatMessageEntry entry = messageHistory.get(historyIndex);
					if (entry != null && entry.isAchievement && entry.message != null) {
						hoveredEntry = entry;
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Рендерит tooltip для сообщения о достижении при наведении
	 */
	public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// Tooltip будет реализован позже, когда найдем правильный API для GuiGraphics
		// Пока фокусируемся на том, чтобы клик открывал экран достижений
	}
	
	/**
	 * Сворачивает развернутые сообщения при закрытии чата
	 */
	public void collapseMessagesOnChatClose() {
		collapsedMessagesExpanded = false;
		// Сворачиваем все развернутые повторяющиеся сообщения
		for (ChatMessageEntry entry : messageHistory) {
			if (entry != null) {
				entry.isExpanded = false;
			}
		}
		messageBounds.clear();
	}
	
	/**
	 * Вычисляет ширину сообщения
	 */
	private int calculateMessageWidth(Font font, Component component) {
		int fullTextWidth = font.width(component);
		// Для сообщений о присоединении не учитываем ширину аватара
		int avatarAreaWidth = AVATAR_SIZE;
		int leftNonText = HORIZONTAL_PADDING + avatarAreaWidth + 7;
		int rightNonText = HORIZONTAL_PADDING;
		int totalNonTextWidth = leftNonText + rightNonText;
		int logicalTextWidth = Math.min(MAX_BOX_WIDTH - totalNonTextWidth, fullTextWidth);
		return logicalTextWidth + totalNonTextWidth;
	}
	
	/**
	 * Рендерит одно сообщение (используется и для истории, и для последнего сообщения)
	 */
	private int renderSingleMessage(GuiGraphics guiGraphics, Font font, int screenWidth, int startY, 
			ChatMessageEntry entry, float alpha) {
		Component component = entry.message;
		if (component == null || component.getString().isEmpty()) {
			return 0;
		}

		// Добавляем суффикс " x2/x3..." серым цветом, если сообщение повторялось и не развернуто
		if (entry.repeatCount > 1 && !entry.isExpanded) {
			MutableComponent withSuffix = component.copy();
			MutableComponent suffix = Component.literal(" x" + entry.repeatCount)
					.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
			withSuffix.append(suffix);
			component = withSuffix;
		}
		
		// Вычисляем ширину и высоту блока
		int fullTextWidth = font.width(component);

		boolean isWhisper = entry.isWhisper;
		
		// Определяем, короткое ли сообщение для whisper (одна строка и небольшая ширина)
		boolean isShortWhisper = false;
		if (isWhisper) {
			// Проверяем, помещается ли текст в одну строку и его ширина меньше порога
			int shortMessageThreshold = 150; // Порог для короткого сообщения
			isShortWhisper = fullTextWidth < shortMessageThreshold && component.getString().split("\n").length == 1;
		}
		
		int avatarAreaWidth;
		// Для сообщений о присоединении/выходе не показываем голову - ширина 0
		if (entry.isJoinMessage) {
			avatarAreaWidth = 0;
		} else if (isWhisper) {
			if (isShortWhisper) {
				// Горизонтальное расположение: две головы рядом + стрелочка с отступами
				// голова1 + отступ 3px + стрелочка (3px ширина) + отступ 3px + голова2
				avatarAreaWidth = AVATAR_SIZE + 3 + 3 + 3 + AVATAR_SIZE;
			} else {
				// Вертикальное расположение: одна голова по ширине
				avatarAreaWidth = AVATAR_SIZE;
			}
		} else {
			avatarAreaWidth = AVATAR_SIZE;
		}

		// Отступы:
		// слева:   [рамка] -- H_PADDING -- голова(ы) -- 7px -- текст
		// справа:  [рамка] -- H_PADDING -- текст
		// Для сообщений о присоединении (без головы) уменьшаем отступ
		int leftNonText = entry.isJoinMessage ? HORIZONTAL_PADDING : (HORIZONTAL_PADDING + avatarAreaWidth + 7);
		int rightNonText = HORIZONTAL_PADDING;
		int totalNonTextWidth = leftNonText + rightNonText;

		// Ширина текста ограничена только максимумом, минимум убран чтобы контейнер подстраивался под короткие сообщения
		int logicalTextWidth = Math.min(MAX_BOX_WIDTH - totalNonTextWidth, fullTextWidth);

		int boxWidth = logicalTextWidth + totalNonTextWidth;
		int x = (screenWidth - boxWidth) / 2;
		
		int textAreaStartX = x + leftNonText;
		int maxTextWidth = logicalTextWidth;
		
		// Wrap text into multiple lines
		List<FormattedCharSequence> lines = font.split(component, maxTextWidth);
		if (lines.isEmpty()) {
			return 0;
		}
		
		int textHeight = font.lineHeight * lines.size();
		// Для сообщений о присоединении не учитываем высоту аватара
		int contentHeight = entry.isJoinMessage ? textHeight : Math.max(AVATAR_SIZE, textHeight);
		int boxHeight = contentHeight + VERTICAL_PADDING * 2;
		int y = startY - boxHeight; // Начинаем снизу
		
		// Background box (прозрачность)
		int baseAlpha = (int) (backgroundOpacity * 255.0f);
		int animatedAlpha = (int) (baseAlpha * alpha);
		int backgroundColor = (animatedAlpha << 24) | 0x000000;
		guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, backgroundColor);
		
		// Avatar(s)
		int avatarX = x + HORIZONTAL_PADDING;
		
		// Если это collapsed сообщение ("X more messages"), показываем иконку часов
		if (entry.isCollapsed) {
			int avatarY = y + (boxHeight - AVATAR_SIZE) / 2;
			renderClockIcon(guiGraphics, avatarX, avatarY, alpha);
		} else {
				// Обычное сообщение: одна голова
				int avatarY = y + (boxHeight - AVATAR_SIZE) / 2;
				// Для сообщений о присоединении/выходе не показываем голову вообще
				if (entry.isJoinMessage) {
					// Не показываем ничего - только текст
				} else if (entry.senderInfo != null) {
					renderPlayerHead(guiGraphics, avatarX, avatarY, entry.senderInfo, alpha);
				} else if (entry.senderName != null) {
					renderPlayerHeadByName(guiGraphics, avatarX, avatarY, entry.senderName, alpha);
				} else {
					// Для испытаний показываем незерит, для достижений - изумруд, для ошибок - барьер, для сообщений о сне - кровать, для системных - палку, для игроков - скин Стива
					if (entry.isChallenge) {
						renderNetheriteIcon(guiGraphics, avatarX, avatarY, alpha);
					} else if (entry.isAchievement) {
						renderEmeraldIcon(guiGraphics, avatarX, avatarY, alpha);
					} else if (entry.isError) {
						renderBarrierIcon(guiGraphics, avatarX, avatarY, alpha);
					} else if (entry.isSleepMessage) {
						renderBedIcon(guiGraphics, avatarX, avatarY, alpha);
					} else if (entry.isScreenshot) {
						renderCameraIcon(guiGraphics, avatarX, avatarY, alpha);
					} else if (entry.systemMessage) {
						renderStickIcon(guiGraphics, avatarX, avatarY, alpha);
					} else {
						// Если это сообщение от игрока (не системное), показываем скин Стива
						renderDefaultSteveHead(guiGraphics, avatarX, avatarY, alpha);
					}
				}
		}
		
		// Text
		int textX = textAreaStartX;
		int totalTextHeight = font.lineHeight * lines.size();
		int avatarCenterY = y + (boxHeight - AVATAR_SIZE) / 2 + AVATAR_SIZE / 2;
		int textY = avatarCenterY - totalTextHeight / 2;
		
		int textAlpha = (int) (255 * alpha);
		int textColorWithAlpha = (textAlpha << 24) | 0xFFFFFF;
		
		for (int i = 0; i < lines.size(); i++) {
			FormattedCharSequence line = lines.get(i);
			int lineY = textY + i * font.lineHeight;
			guiGraphics.drawString(font, line, textX, lineY, textColorWithAlpha, false);
		}
		
		// Сохраняем информацию о тексте для обработки кликов (только если это не анимация)
		// Используем component без суффикса для правильной обработки кликов
		if (alpha >= 0.99f) {
			// Находим соответствующий MessageBounds и обновляем его
			// Ищем по координатам и размерам
			for (Map.Entry<Integer, MessageBounds> boundsEntry : messageBounds.entrySet()) {
				MessageBounds bounds = boundsEntry.getValue();
				// Проверяем совпадение координат и размеров (с небольшой погрешностью)
				if (Math.abs(bounds.x - x) < 5 && Math.abs(bounds.y - y) < 5 && 
				    Math.abs(bounds.width - boxWidth) < 5 && Math.abs(bounds.height - boxHeight) < 5) {
					// Обновляем информацию о тексте
					bounds.textX = textX;
					bounds.textY = textY;
					bounds.maxTextWidth = maxTextWidth;
					bounds.component = entry.message; // Используем оригинальный Component без суффикса
					break;
				}
			}
		}
		
		return boxHeight;
	}

	/**
	 * Обновляет PlayerInfo для сообщений, у которых он еще не загружен.
	 * Вызывается периодически в onHudRender для проверки последних сообщений.
	 * 
	 * ВАЖНО: Мы НЕ различаем между Chat Heads и SkinsRestorer - оба используют стандартный протокол Minecraft!
	 * 
	 * Как это работает:
	 * 
	 * 1. Chat Heads (клиентский мод):
	 *    - Просто читает PlayerInfo из стандартного протокола Minecraft
	 *    - Использует те же методы, что и мы: connection.getPlayerInfo(), getOnlinePlayers()
	 * 
	 * 2. SkinsRestorer (серверный плагин):
	 *    - Сервер обновляет GameProfile игрока с новым скином через setGameProfileTextures()
	 *    - Отправляет ClientboundPlayerInfoUpdatePacket клиенту с обновленным профилем
	 *    - Клиент получает обновление через стандартный протокол Minecraft и обновляет PlayerInfo
	 *    - Мы используем те же методы: connection.getPlayerInfo(), getOnlinePlayers()
	 * 
	 * 3. Ванильный Minecraft:
	 *    - Скины загружаются автоматически через стандартный протокол
	 *    - Используем те же методы получения PlayerInfo
	 * 
	 * ЕДИНЫЙ ПОДХОД:
	 * - Приоритет: Стандартный API Minecraft (connection.getPlayerInfo(), getOnlinePlayers())
	 *   → Работает с Chat Heads, SkinsRestorer, ванильным Minecraft
	 * 
	 * - Fallback: HTTP запросы к Mojang API (loadSkinFromMojangAPI)
	 *   → Используется только если стандартный метод не работает
	 *   → Для случаев, когда PlayerInfo недоступен (игрок не онлайн, скин еще не загрузился)
	 * 
	 * На неофициальных серверах скины могут загружаться асинхронно, поэтому мы периодически проверяем
	 * и обновляем PlayerInfo для недавних сообщений. Также проверяем обновления даже если PlayerInfo уже есть,
	 * так как скины могут обновиться позже через ClientboundPlayerInfoUpdatePacket.
	 */
	private void updateMissingPlayerInfos(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null || messageHistory.isEmpty()) {
			return;
		}
		
		// Проверяем последние 30 сообщений для лучшей поддержки SkinsRestorer на неофициальных серверах
		// Увеличено с 20 до 30, так как скины могут загружаться с задержкой
		int startIndex = Math.max(0, messageHistory.size() - 30);
		for (int i = startIndex; i < messageHistory.size(); i++) {
			ChatMessageEntry entry = messageHistory.get(i);
			if (entry == null) {
				continue;
			}
			
			// Если PlayerInfo отсутствует, пытаемся получить его несколькими способами
			if (entry.senderInfo == null) {
				// Метод 1: По UUID (если есть) - самый надежный способ
				// SkinsRestorer отправляет обновления через ClientboundPlayerInfoUpdatePacket,
				// которые обновляют PlayerInfo по UUID
				if (entry.senderUUID != null) {
					PlayerInfo info = connection.getPlayerInfo(entry.senderUUID);
					if (info != null) {
						entry.updateSenderInfo(info);
						continue; // Успешно нашли, переходим к следующему
					}
				}
				
				// Метод 2: По имени через getOnlinePlayers() (как в Chat Heads)
				// Это важно для SkinsRestorer, так как скины могут загрузиться позже
				if (entry.senderName != null && !entry.senderName.isEmpty()) {
					PlayerInfo info = getPlayerInfoByName(entry.senderName);
					if (info != null) {
						entry.updateSenderInfo(info);
						// Сохраняем UUID для будущих обновлений
						if (entry.senderUUID == null && info.getProfile().id() != null) {
							entry.senderUUID = info.getProfile().id();
						}
					} else {
						// Если PlayerInfo не найден через стандартный способ, пытаемся загрузить скин через Mojang API
						// Это fallback для случаев когда SkinsRestorer не отправляет скины автоматически
						loadSkinFromMojangAPI(entry.senderName);
					}
				}
			} else {
				// Даже если PlayerInfo уже есть, проверяем, не обновился ли скин
				// SkinsRestorer может обновить скин асинхронно после того, как мы получили PlayerInfo
				// через ClientboundPlayerInfoUpdatePacket, который обновляет PlayerInfo по UUID
				if (entry.senderUUID != null) {
					PlayerInfo updatedInfo = connection.getPlayerInfo(entry.senderUUID);
					if (updatedInfo != null && updatedInfo != entry.senderInfo) {
						// PlayerInfo обновился (возможно, загрузился новый скин от SkinsRestorer)
						entry.updateSenderInfo(updatedInfo);
					}
				}
			}
			
			// Для whisper сообщений также обновляем receiverInfo
			if (entry.isWhisper && entry.receiverInfo == null) {
				// Получатель - это текущий игрок
				if (minecraft.player != null) {
					PlayerInfo receiverInfo = connection.getPlayerInfo(minecraft.player.getUUID());
					if (receiverInfo != null) {
						entry.receiverInfo = receiverInfo;
					}
				}
			}
		}
	}

	/**
	 * Получает PlayerInfo по имени игрока, используя стандартные методы Minecraft API.
	 * 
	 * ЕДИНЫЙ ПОДХОД для всех случаев:
	 * - Chat Heads (клиентский мод) - использует те же методы
	 * - SkinsRestorer (серверный плагин) - скины приходят через стандартный протокол
	 * - Ванильный Minecraft - стандартный протокол
	 * 
	 * Методы поиска (в порядке приоритета):
	 * 1. connection.getPlayerInfo(name) - прямой поиск по имени
	 * 2. connection.getOnlinePlayers() - перебор всех онлайн игроков (как в Chat Heads)
	 * 3. Поиск через мир (level.players()) - fallback для случаев, когда игрок в мире, но не в табе
	 */
	private static PlayerInfo getPlayerInfoByName(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return null;
		}
		
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		if (connection == null) {
			return null;
		}
		
		// Убираем цветовые коды и форматирование из имени
		String cleanName = playerName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim();
		
		// Метод 1: Попробовать найти напрямую по имени (как в Chat Heads)
		PlayerInfo info = connection.getPlayerInfo(playerName);
		if (info != null) {
			return info;
		}
		
		// Метод 2: Попробовать по очищенному имени
		if (!cleanName.equals(playerName)) {
			info = connection.getPlayerInfo(cleanName);
			if (info != null) {
				return info;
			}
		}
		
		// Метод 3: Перебираем всех онлайн игроков через connection.getOnlinePlayers() (как в Chat Heads)
		// Это более надежный способ, так как получаем все PlayerInfo напрямую
		for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
			// Проверяем имя профиля
			String profileName = playerInfo.getProfile().name();
			if (profileName != null) {
				String cleanProfileName = profileName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim();
				if (profileName.equalsIgnoreCase(playerName) || 
				    profileName.equalsIgnoreCase(cleanName) ||
				    cleanProfileName.equalsIgnoreCase(cleanName)) {
					return playerInfo;
				}
			}
			
			// Проверяем отображаемое имя из таба (как в Chat Heads)
			if (playerInfo.getTabListDisplayName() != null) {
				String displayName = playerInfo.getTabListDisplayName().getString();
				String cleanDisplayName = displayName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim();
				if (displayName.equalsIgnoreCase(playerName) || 
				    displayName.equalsIgnoreCase(cleanName) ||
				    cleanDisplayName.equalsIgnoreCase(cleanName)) {
					return playerInfo;
				}
			}
		}
		
		// Метод 4: Поиск через мир - перебираем всех игроков в мире
		ClientLevel level = minecraft.level;
		if (level != null) {
			for (Player player : level.players()) {
				String playerDisplayName = player.getDisplayName().getString();
				String playerNameStr = player.getName().getString();
				
				// Сравниваем имена без учета регистра и форматирования
				if (playerNameStr.equalsIgnoreCase(playerName) || 
				    playerNameStr.equalsIgnoreCase(cleanName) ||
				    playerDisplayName.replaceAll("(?i)[§&][0-9A-FK-OR]", "").trim().equalsIgnoreCase(cleanName)) {
					// Если нашли игрока в мире, получаем его PlayerInfo по UUID
					return connection.getPlayerInfo(player.getUUID());
				}
			}
		}
		
		return null;
	}

	/**
	 * Загружает скин игрока через Mojang API (fallback для SkinsRestorer)
	 * Используется когда стандартный способ получения PlayerInfo не работает
	 */
	private static void loadSkinFromMojangAPI(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return;
		}
		
		// Проверяем кэш
		if (skinUrlCache.containsKey(playerName)) {
			return; // Уже загружен
		}
		
		// Проверяем флаг загрузки
		if (loadingFlags.putIfAbsent(playerName, true) != null) {
			return; // Уже загружается
		}
		
		// Запускаем асинхронную загрузку в отдельном потоке
		Thread.ofVirtual().start(() -> {
			try {
				// Шаг 1: Получаем UUID по имени игрока
				UUID uuid = uuidCache.get(playerName);
				if (uuid == null) {
					HttpRequest uuidRequest = HttpRequest.newBuilder()
						.uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
						.GET()
						.header("User-Agent", "Minecraft")
						.build();
					
					HttpResponse<String> uuidResponse = HTTP_CLIENT.send(uuidRequest, HttpResponse.BodyHandlers.ofString());
					if (uuidResponse.statusCode() == 200) {
						JsonObject profile = JsonParser.parseString(uuidResponse.body()).getAsJsonObject();
						String uuidString = profile.get("id").getAsString();
						// Форматируем UUID (добавляем дефисы)
						uuid = UUID.fromString(uuidString.replaceFirst(
							"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
							"$1-$2-$3-$4-$5"
						));
						uuidCache.put(playerName, uuid);
					} else {
						loadingFlags.remove(playerName);
						return; // Игрок не найден
					}
				}
				
				// Шаг 2: Получаем профиль с текстурами по UUID
				// UUID должен быть без дефисов для этого запроса
				String uuidWithoutDashes = uuid.toString().replace("-", "");
				HttpRequest profileRequest = HttpRequest.newBuilder()
					.uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidWithoutDashes))
					.GET()
					.header("User-Agent", "Minecraft")
					.build();
				
				HttpResponse<String> profileResponse = HTTP_CLIENT.send(profileRequest, HttpResponse.BodyHandlers.ofString());
				if (profileResponse.statusCode() == 200) {
					JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
					JsonArray properties = profile.getAsJsonArray("properties");
					
					if (properties != null) {
						for (var prop : properties) {
							JsonObject property = prop.getAsJsonObject();
							if ("textures".equals(property.get("name").getAsString())) {
								// Декодируем base64 значение
								String value = property.get("value").getAsString();
								String decoded = new String(Base64.getDecoder().decode(value));
								JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
								
								if (textures.has("textures")) {
									JsonObject texturesObj = textures.getAsJsonObject("textures");
									if (texturesObj.has("SKIN")) {
										JsonObject skinObj = texturesObj.getAsJsonObject("SKIN");
										String skinUrl = skinObj.get("url").getAsString();
										
										// Сохраняем URL в кэш
										skinUrlCache.put(playerName, skinUrl);
									}
								}
								break;
							}
						}
					}
				}
			} catch (Exception e) {
				// Игнорируем ошибки загрузки скинов через API
			} finally {
				loadingFlags.remove(playerName);
			}
		});
	}

	/**
	 * Рендеринг головы игрока, основанный на логике Chat Heads для правильного отображения.
	 * Поддерживает скины, установленные через SkinRestorer и другие серверные моды.
	 * SkinRestorer автоматически отправляет скины клиенту через стандартный протокол Minecraft,
	 * поэтому они обрабатываются через стандартный PlayerInfo API.
	 */
	private static void renderPlayerHead(GuiGraphics guiGraphics, int x, int y, PlayerInfo owner, float opacity) {
		if (owner == null) {
			return;
		}

		try {
			String playerName = owner.getProfile().name();
			
			// Получаем скин через стандартный API Minecraft
			// Это автоматически работает с SkinRestorer, так как сервер отправляет скины клиенту
			ResourceLocation skinLocation = owner.getSkin().body().texturePath();

			int color = ARGB.white(opacity);

			ClientLevel level = Minecraft.getInstance().level;
			Player player = level != null ? level.getPlayerByUUID(owner.getProfile().id()) : null;
			boolean upsideDown = player != null && AvatarRenderer.isPlayerUpsideDown(player);

			boolean showHat = owner.showHat();

			int yOffset = (upsideDown ? 8 : 0);
			int yDirection = (upsideDown ? -1 : 1);

			// Рендерим голову используя правильные параметры как в Chat Heads
			// Формат: blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color)
			// Где u, v - координаты текстуры (8, 8 для лица), width/height - размер на экране, regionWidth/Height - размер области текстуры (8x8)
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinLocation, x, y,
					8.0f, 8 + yOffset,
					AVATAR_SIZE, AVATAR_SIZE,
					8, yDirection * 8,
					64, 64, color);
			
			// Hat / overlay поверх (координаты текстуры 40, 8 для шапки)
			if (showHat) {
				guiGraphics.blit(RenderPipelines.GUI_TEXTURED, skinLocation, x, y,
						40.0f, 8 + yOffset,
						AVATAR_SIZE, AVATAR_SIZE,
						8, yDirection * 8,
						64, 64, color);
			}
		} catch (Exception e) {
			// Если скин не загружен (например, еще загружается или недоступен),
			// просто пропускаем рендеринг - будет показан барьер или пустое место
			// Это нормально для SkinRestorer, так как скины могут загружаться асинхронно
		}
	}
	
	/**
	 * Рендерит голову игрока по имени, используя кэш Mojang API если PlayerInfo недоступен
	 */
	private static void renderPlayerHeadByName(GuiGraphics guiGraphics, int x, int y, String playerName, float opacity) {
		if (playerName == null || playerName.isEmpty()) {
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
			return;
		}
		
		// Сначала пытаемся найти PlayerInfo
		PlayerInfo info = getPlayerInfoByName(playerName);
		if (info != null) {
			renderPlayerHead(guiGraphics, x, y, info, opacity);
			return;
		}
		
		// Если PlayerInfo не найден, проверяем кэш Mojang API
		String skinUrl = skinUrlCache.get(playerName);
		if (skinUrl != null) {
			// URL текстуры сохранен в кэше, но для рендеринга нужен PlayerInfo
			// Показываем дефолтный скин Стива, если PlayerInfo недоступен
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
		} else {
			// Запускаем загрузку через Mojang API
			loadSkinFromMojangAPI(playerName);
			// Показываем дефолтный скин Стива пока загружается
			renderDefaultSteveHead(guiGraphics, x, y, opacity);
		}
	}
	
	/**
	 * Рендерит дефолтный скин Стива (когда скин игрока неизвестен)
	 */
	private static void renderDefaultSteveHead(GuiGraphics guiGraphics, int x, int y, float opacity) {
		// Используем кастомный скин Стива из assets/kks-chat/steve.png
		ResourceLocation steveSkin = ResourceLocation.fromNamespaceAndPath("kks-chat", "steve.png");
		
		int color = ARGB.white(opacity);
		
		// Рендерим голову Стива (координаты текстуры 8, 8 для головы)
		// Формат: blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color)
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, steveSkin, x, y,
				8.0f, 8.0f,
				AVATAR_SIZE, AVATAR_SIZE,
				8, 8,
				64, 64, color);
		
		// Hat / overlay поверх (координаты текстуры 40, 8 для шапки)
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, steveSkin, x, y,
				40.0f, 8.0f,
				AVATAR_SIZE, AVATAR_SIZE,
				8, 8,
				64, 64, color);
	}

	/**
	 * Renders a barrier item icon for system messages with alpha support.
	 */
	private static void renderBarrierIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Небольшое смещение вверх, чтобы иконка красиво вписалась в AVATAR_SIZE
		int iconY = y - 1;
		
		// Применяем альфа через ARGB цвет
		int color = ARGB.white(alpha);
		guiGraphics.renderItem(BARRIER_STACK, x, iconY, color);
	}
	
	/**
	 * Renders an emerald item icon for achievement messages with alpha support.
	 */
	private static void renderEmeraldIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Небольшое смещение вверх, чтобы иконка красиво вписалась в AVATAR_SIZE
		int iconY = y - 1;
		
		// Применяем альфа через ARGB цвет
		int color = ARGB.white(alpha);
		guiGraphics.renderItem(EMERALD_STACK, x, iconY, color);
	}
	
	/**
	 * Renders a netherite item icon for challenge messages with alpha support.
	 */
	private static void renderNetheriteIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Небольшое смещение вверх, чтобы иконка красиво вписалась в AVATAR_SIZE
		int iconY = y - 1;
		
		// Применяем альфа через ARGB цвет
		int color = ARGB.white(alpha);
		guiGraphics.renderItem(NETHERITE_STACK, x, iconY, color);
	}
	
	/**
	 * Renders a clock item icon for "X more messages" with alpha support.
	 */
	private static void renderClockIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Небольшое смещение вверх, чтобы иконка красиво вписалась в AVATAR_SIZE
		int iconY = y - 1;
		
		// Применяем альфа через ARGB цвет
		int color = ARGB.white(alpha);
		guiGraphics.renderItem(CLOCK_STACK, x, iconY, color);
	}
	
	/**
	 * Renders a stick item icon for system messages with alpha support.
	 */
	private static void renderStickIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Небольшое смещение вверх, чтобы иконка красиво вписалась в AVATAR_SIZE
		int iconY = y - 1;
		
		// Применяем альфа через ARGB цвет
		int color = ARGB.white(alpha);
		guiGraphics.renderItem(STICK_STACK, x, iconY, color);
	}
	
	/**
	 * Renders a bed item icon for sleep-related messages with alpha support.
	 */
	private static void renderBedIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Небольшое смещение вверх, чтобы иконка красиво вписалась в AVATAR_SIZE
		int iconY = y - 1;
		
		// Применяем альфа через ARGB цвет
		int color = ARGB.white(alpha);
		guiGraphics.renderItem(BED_STACK, x, iconY, color);
	}
	
	/**
	 * Renders a camera icon for screenshot messages with alpha support.
	 */
	private static void renderCameraIcon(GuiGraphics guiGraphics, int x, int y, float alpha) {
		// Используем кастомную текстуру камеры из assets/kks-chat/camera.png
		ResourceLocation cameraTexture = ResourceLocation.fromNamespaceAndPath("kks-chat", "camera.png");
		
		int color = ARGB.white(alpha);
		
		// Рендерим текстуру камеры размером AVATAR_SIZE x AVATAR_SIZE
		// Предполагаем, что текстура имеет размер AVATAR_SIZE x AVATAR_SIZE
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, cameraTexture, x, y,
				0.0f, 0.0f,
				AVATAR_SIZE, AVATAR_SIZE,
				AVATAR_SIZE, AVATAR_SIZE,
				AVATAR_SIZE, AVATAR_SIZE, color);
	}

	/**
	 * Рендерит стрелочку вверх для вертикального whisper сообщения
	 */
	private static void renderArrowUp(GuiGraphics guiGraphics, int x, int y, float alpha) {
		int color = ARGB.white(alpha);
		// Рисуем простую стрелочку вверх (^)
		guiGraphics.fill(x, y, x + 4, y + 1, color);
		guiGraphics.fill(x + 1, y + 1, x + 3, y + 2, color);
		guiGraphics.fill(x + 2, y + 2, x + 2, y + 3, color);
	}

	/**
	 * Рендерит стрелочку вправо для горизонтального whisper сообщения
	 */
	private static void renderArrowRight(GuiGraphics guiGraphics, int x, int y, float alpha) {
		int color = ARGB.white(alpha);
		// Рисуем простую стрелочку вправо (>)
		guiGraphics.fill(x, y, x + 1, y + 4, color);
		guiGraphics.fill(x + 1, y + 1, x + 2, y + 3, color);
		guiGraphics.fill(x + 2, y + 2, x + 3, y + 2, color);
	}

	/**
	 * Parser for system messages: applies formatting codes but ignores color codes.
	 * Always uses gray color (§7) for system messages.
	 */
	private static Component applyLegacyColorCodesForSystem(String text) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY.withColor(ChatFormatting.GRAY); // Always gray for system messages
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					// flush accumulated text with previous style
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						// Reset to gray (system messages always gray)
						style = Style.EMPTY.withColor(ChatFormatting.GRAY);
					} else if (fmt.isColor()) {
						// Ignore color codes for system messages, keep gray
						// Don't change style color
					} else {
						// Apply format codes (bold, italic, etc.)
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

					i++; // skip format char
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
	
	/**
	 * Копирует обработчики кликов из оригинального Component в новый Component
	 */
	private static Component copyClickHandlers(Component original, Component styled) {
		// Если оригинальный Component имеет обработчики кликов, копируем их
		Style originalStyle = original.getStyle();
		ClickEvent clickEvent = originalStyle.getClickEvent();
		if (clickEvent != null) {
			// Копируем обработчик кликов в styled Component
			MutableComponent result = styled.copy();
			Style styledStyle = result.getStyle();
			Style newStyle = styledStyle.withClickEvent(clickEvent);
			result.setStyle(newStyle);
			return result;
		}
		// Если нет обработчиков кликов, возвращаем styled как есть
		return styled;
	}
	
	/**
	 * Находит ClickEvent в Component и его siblings
	 */
	private static ClickEvent findClickEvent(Component component) {
		if (component == null) {
			return null;
		}
		// Проверяем стиль текущего Component
		Style style = component.getStyle();
		ClickEvent clickEvent = style.getClickEvent();
		if (clickEvent != null) {
			return clickEvent;
		}
		// Проверяем siblings
		for (Component sibling : component.getSiblings()) {
			clickEvent = findClickEvent(sibling);
			if (clickEvent != null) {
				return clickEvent;
			}
		}
		return null;
	}
	
	/**
	 * Parser for challenge messages: applies formatting codes but ignores color codes.
	 * Always uses light purple color (§d) for challenge messages.
	 */
	private static Component applyLegacyColorCodesForChallenge(String text) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE); // Always light purple for challenge messages
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					// flush accumulated text with previous style
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						// Reset to light purple (challenge messages always light purple)
						style = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);
					} else if (fmt.isColor()) {
						// Ignore color codes for challenge messages, keep light purple
						// Don't change style color
					} else {
						// Apply format codes (bold, italic, etc.)
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

					i++; // skip format char
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
	
	/**
	 * Parser for achievement messages: applies formatting codes but ignores color codes.
	 * Always uses green color (§a) for achievement messages.
	 */
	private static Component applyLegacyColorCodesForAchievement(String text) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY.withColor(ChatFormatting.GREEN); // Always green for achievement messages
		StringBuilder current = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char codeChar = Character.toLowerCase(text.charAt(i + 1));
				ChatFormatting fmt = ChatFormatting.getByCode(codeChar);

				if (fmt != null) {
					// flush accumulated text with previous style
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						// Reset to green (achievement messages always green)
						style = Style.EMPTY.withColor(ChatFormatting.GREEN);
					} else if (fmt.isColor()) {
						// Ignore color codes for achievement messages, keep green
						// Don't change style color
					} else {
						// Apply format codes (bold, italic, etc.)
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

					i++; // skip format char
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

	/**
	 * Simple parser for legacy '&' color/format codes (similar to Bukkit).
	 * Supports &0-9, &a-f (colors) and &k, &l, &m, &n, &o, &r (formats).
	 */
	private static Component applyLegacyColorCodes(String text) {
		return applyLegacyColorCodes(text, null);
	}

	/**
	 * Same as above, but with an initial default color for text that doesn't start with a color code.
	 */
	private static Component applyLegacyColorCodes(String text, ChatFormatting defaultColor) {
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
					// flush accumulated text with previous style
					if (current.length() > 0) {
						result.append(Component.literal(current.toString()).setStyle(style));
						current.setLength(0);
					}

					if (fmt == ChatFormatting.RESET) {
						// Reset to default color if provided, otherwise empty style
						style = defaultColor != null 
							? Style.EMPTY.withColor(defaultColor) 
							: Style.EMPTY;
					} else if (fmt.isColor()) {
						style = style.withColor(fmt);
					} else {
						// format codes
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

					i++; // skip format char
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

	/**
	 * Получить состояние включения/выключения мода
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Получить прозрачность фона (0.0 - 1.0)
	 */
	public float getBackgroundOpacity() {
		return backgroundOpacity;
	}
	
	/**
	 * Установить прозрачность фона (0.0 - 1.0, где 0.0 = полностью прозрачный, 1.0 = полностью непрозрачный)
	 */
	public void setBackgroundOpacity(float opacity) {
		this.backgroundOpacity = Math.max(0.0f, Math.min(1.0f, opacity)); // Ограничиваем от 0 до 1
	}
	
	/**
	 * Установить состояние включения/выключения мода
	 */
	public void setEnabled(boolean enabled) {
		boolean wasEnabled = this.enabled;
		this.enabled = enabled;
		
		// Если чат был выключен, а теперь включен - очищаем стандартный чат
		// чтобы старые сообщения не отображались там
		if (!wasEnabled && enabled) {
			clearVanillaChat();
		}
	}
	
	/**
	 * Очищает стандартный чат Minecraft
	 */
	private void clearVanillaChat() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.gui == null || minecraft.gui.getChat() == null) {
			return;
		}
		
		// Очищаем стандартный чат (сообщения уже сохранены в нашем HUD)
		minecraft.gui.getChat().clearMessages(false);
	}
	
	/**
	 * Получить флаг изменения текста сообщений
	 */
	public boolean isModifyMessageText() {
		return modifyMessageText;
	}
	
	/**
	 * Установить флаг изменения текста сообщений
	 * @param modify true = изменять форматирование (добавлять цвета, префиксы и т.д.), false = показывать как есть
	 */
	public void setModifyMessageText(boolean modify) {
		this.modifyMessageText = modify;
	}

	public int getDisplayTimeSeconds() { return displayTimeSeconds; }
	public void setDisplayTimeSeconds(int seconds) { this.displayTimeSeconds = Math.max(1, Math.min(60, seconds)); }

	public int getMaxVisibleMessages() { return maxVisibleMessages; }
	public void setMaxVisibleMessages(int max) { this.maxVisibleMessages = Math.max(1, Math.min(50, max)); }

	public int getMaxHistorySize() { return maxHistorySize; }
	public void setMaxHistorySize(int max) {
		this.maxHistorySize = Math.max(50, Math.min(500, max));
		trimMessageHistory();
	}

	public float getFontScale() { return fontScale; }
	public void setFontScale(float scale) { this.fontScale = Math.max(0.5f, Math.min(2.0f, scale)); }

	public int getChatPosition() { return chatPosition; }
	public void setChatPosition(int position) { this.chatPosition = Math.max(0, Math.min(2, position)); }

	public boolean isAntiSpamEnabled() { return antiSpamEnabled; }
	public void setAntiSpamEnabled(boolean enabled) { this.antiSpamEnabled = enabled; }

	/**
	 * Сбросить все настройки к значениям по умолчанию
	 */
	public void resetSettings() {
		this.enabled = true;
		this.backgroundOpacity = 0.3f;
		this.modifyMessageText = true;
		this.displayTimeSeconds = 5;
		this.maxVisibleMessages = 10;
		this.maxHistorySize = 100;
		this.fontScale = 1.0f;
		this.chatPosition = 0;
		this.antiSpamEnabled = true;
	}
}

