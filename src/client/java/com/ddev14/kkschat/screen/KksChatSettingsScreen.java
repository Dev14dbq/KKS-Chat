package com.ddev14.kkschat.screen;

import com.ddev14.kkschat.KksChatModClient;
import com.ddev14.kkschat.KksChatHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ChatVisiblity;
import java.util.List;

/**
 * Экран настроек KKS Chat в ванильном стиле Minecraft с прокруткой.
 */
public class KksChatSettingsScreen extends Screen {
	private final Screen parent;
	private SettingsList settingsList;
	private Button resetButton;
	private Button doneButton;
	
	// Дефолтные значения для проверки
	private static final boolean DEFAULT_ENABLED = true;
	private static final float DEFAULT_OPACITY = 0.3f;
	private static final boolean DEFAULT_MODIFY_TEXT = true;
	
	public KksChatSettingsScreen(Screen parent) {
		super(Component.translatable("kkschat.settings.title"));
		this.parent = parent;
	}
	
	/**
	 * Проверяет, равны ли текущие настройки дефолтным значениям
	 */
	private boolean areSettingsDefault() {
		KksChatHud hud = KksChatModClient.getHud();
		return hud.isEnabled() == DEFAULT_ENABLED &&
		       Math.abs(hud.getBackgroundOpacity() - DEFAULT_OPACITY) < 0.01f &&
		       hud.isModifyMessageText() == DEFAULT_MODIFY_TEXT;
	}
	
	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		
		// Создаем прокручиваемый список настроек
		int listTop = 40; // Отступ сверху для заголовка
		int listBottom = this.height - 40; // Отступ снизу для кнопок
		
		this.settingsList = new SettingsList(mc, this.width, this.height, listTop, listBottom);
		this.addWidget(this.settingsList);
		
		// Кнопки внизу экрана (фиксированные, не прокручиваются)
		int bottomY = this.height - 27;
		int buttonWidth = 200;
		int buttonSpacing = 5;
		
		// Кнопка "Готово" (слева)
		this.doneButton = Button.builder(
			Component.translatable("gui.done"),
			button -> {
				if (mc != null) {
					mc.setScreen(this.parent);
				}
			}
		)
		.bounds(
			this.width / 2 - buttonWidth - buttonSpacing / 2,
			bottomY,
			buttonWidth,
			20
		)
		.build();
		this.addRenderableWidget(this.doneButton);
		
		// Кнопка "Сбросить настройки" (справа)
		boolean isDefault = areSettingsDefault();
		this.resetButton = Button.builder(
			Component.literal("Сбросить настройки"),
			button -> {
				KksChatModClient.getHud().resetSettings();
				// Пересоздаем экран для обновления всех элементов
				mc.setScreen(new KksChatSettingsScreen(this.parent));
			}
		)
		.bounds(
			this.width / 2 + buttonSpacing / 2,
			bottomY,
			buttonWidth,
			20
		)
		.build();
		this.resetButton.active = !isDefault; // Делаем неактивной если настройки стандартные
		this.addRenderableWidget(this.resetButton);
	}
	
	/**
	 * Внутренний класс для прокручиваемого списка настроек
	 */
	private class SettingsList extends ContainerObjectSelectionList<SettingsList.Entry> {
		private int currentMouseX = 0;
		private int currentMouseY = 0;
		
		public SettingsList(Minecraft mc, int width, int height, int top, int bottom) {
			super(mc, width, height, top, bottom);
			
			// Получаем текущее состояние
			boolean isEnabled = KksChatModClient.getHud().isEnabled();
			float currentOpacity = KksChatModClient.getHud().getBackgroundOpacity();
			boolean modifyText = KksChatModClient.getHud().isModifyMessageText();
			
			int buttonX = this.width / 2 - 100;
			int buttonWidth = 200;
			int buttonHeight = 20;
			
			// Заголовок и описание (первая запись)
			this.addEntry(new HeaderEntry());
			
			// 1. Переключатель фильтра чата
			Button filterButton = Button.builder(
				Component.literal("Фильтр чата: " + (isEnabled ? "ВКЛ" : "ВЫКЛ")),
				button -> {
					boolean newState = !KksChatModClient.getHud().isEnabled();
					KksChatModClient.getHud().setEnabled(newState);
					button.setMessage(Component.literal("Фильтр чата: " + (newState ? "ВКЛ" : "ВЫКЛ")));
					// Обновляем состояние кнопки сброса
					if (resetButton != null) {
						resetButton.active = !areSettingsDefault();
					}
				}
			)
			.bounds(buttonX, 0, buttonWidth, buttonHeight)
			.build();
			this.addEntry(new ButtonEntry(filterButton));
			
			// 2. Переключатель изменения текста сообщений
			Button modifyTextButton = Button.builder(
				Component.literal("Изменять текст: " + (modifyText ? "ДА" : "НЕТ")),
				button -> {
					boolean newState = !KksChatModClient.getHud().isModifyMessageText();
					KksChatModClient.getHud().setModifyMessageText(newState);
					button.setMessage(Component.literal("Изменять текст: " + (newState ? "ДА" : "НЕТ")));
					// Обновляем состояние кнопки сброса
					if (resetButton != null) {
						resetButton.active = !areSettingsDefault();
					}
				}
			)
			.bounds(buttonX, 0, buttonWidth, buttonHeight)
			.build();
			this.addEntry(new ButtonEntry(modifyTextButton));
			
			// 3. Ползунок прозрачности фона
			int opacityPercent = (int) Math.round(currentOpacity * 100.0f);
			AbstractSliderButton opacitySlider = new AbstractSliderButton(
				buttonX, 0, buttonWidth, buttonHeight,
				Component.literal("Прозрачность фона: " + opacityPercent + "%"),
				currentOpacity
			) {
				@Override
				protected void updateMessage() {
					int percent = (int) Math.round(this.value * 100.0f);
					this.setMessage(Component.literal("Прозрачность фона: " + percent + "%"));
				}
				
				@Override
				protected void applyValue() {
					KksChatModClient.getHud().setBackgroundOpacity((float) this.value);
					// Обновляем состояние кнопки сброса
					if (resetButton != null) {
						resetButton.active = !areSettingsDefault();
					}
				}
			};
			this.addEntry(new SliderEntry(opacitySlider));
			
			// 4. Стандартные настройки чата Minecraft
			if (mc != null && mc.options != null) {
				// Видимость чата
				CycleButton<ChatVisiblity> chatVisibilityButton = CycleButton.<ChatVisiblity>builder(value -> {
					return Component.translatable("options.chat.visibility." + value.name().toLowerCase());
				})
					.withValues(ChatVisiblity.values())
					.withInitialValue(mc.options.chatVisibility().get())
					.create(
						buttonX, 0, buttonWidth, buttonHeight,
						Component.translatable("options.chat.visibility"),
						(button, value) -> {
							mc.options.chatVisibility().set(value);
						}
					);
				this.addEntry(new CycleButtonEntry(chatVisibilityButton));
				
				// Оратор (Narrator)
				CycleButton<net.minecraft.client.NarratorStatus> narratorButton = CycleButton.<net.minecraft.client.NarratorStatus>builder(value -> {
					return Component.translatable("options.narrator." + value.name().toLowerCase());
				})
					.withValues(net.minecraft.client.NarratorStatus.values())
					.withInitialValue(mc.options.narrator().get())
					.create(
						buttonX, 0, buttonWidth, buttonHeight,
						Component.translatable("options.narrator"),
						(button, value) -> {
							mc.options.narrator().set(value);
						}
					);
				this.addEntry(new CycleButtonEntry(narratorButton));
				
				// Задержка чата
				double chatDelay = mc.options.chatDelay().get();
				float chatDelayNormalized = (float) Math.min(1.0, chatDelay / 0.5);
				AbstractSliderButton chatDelaySlider = new AbstractSliderButton(
					buttonX, 0, buttonWidth, buttonHeight,
					Component.literal("Задержка чата: " + String.format("%.1f", chatDelay) + "с"),
					chatDelayNormalized
				) {
					@Override
					protected void updateMessage() {
						double delay = this.value * 0.5;
						this.setMessage(Component.literal("Задержка чата: " + String.format("%.1f", delay) + "с"));
					}
					
					@Override
					protected void applyValue() {
						double delay = this.value * 0.5;
						mc.options.chatDelay().set(delay);
					}
				};
				this.addEntry(new SliderEntry(chatDelaySlider));
			}
		}
		
		@Override
		public int getRowWidth() {
			return 400;
		}
		
		protected int getScrollbarX() {
			return this.width / 2 + 160;
		}
		
		@Override
		public int getRowLeft() {
			return this.width / 2 - 200;
		}
		
		// Сохраняем координаты мыши перед рендерингом записей
		@Override
		protected void renderItem(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, Entry entry) {
			this.currentMouseX = mouseX;
			this.currentMouseY = mouseY;
			super.renderItem(guiGraphics, mouseX, mouseY, delta, entry);
		}
		
		/**
		 * Базовый класс для записей списка
		 */
		private abstract class Entry extends ContainerObjectSelectionList.Entry<Entry> {
			@Override
			public int getContentHeight() {
				return 24; // Стандартная высота записи
			}
		}
		
		/**
		 * Запись с заголовком и описанием
		 */
		private class HeaderEntry extends Entry {
			@Override
			public int getContentHeight() {
				return 40; // Больше для заголовка и описания
			}
			
			@Override
			public void renderContent(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, boolean hovered, float delta) {
				// Заголовок
				Component modName = Component.literal("KKS Chat");
				guiGraphics.drawCenteredString(
					SettingsList.this.minecraft.font,
					modName,
					SettingsList.this.width / 2,
					y + 5,
					0xFFFFFFFF
				);
				
				// Описание
				Component modDescription = Component.literal("Удобный чат по середине экрана");
				guiGraphics.drawCenteredString(
					SettingsList.this.minecraft.font,
					modDescription,
					SettingsList.this.width / 2,
					y + 20,
					0xFFAAAAAA
				);
			}
			
			@Override
			public List<? extends GuiEventListener> children() {
				return List.of();
			}
			
			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of();
			}
		}
		
		/**
		 * Запись с кнопкой
		 */
		private class ButtonEntry extends Entry {
			private final Button button;
			private final int buttonX;
			
			public ButtonEntry(Button button) {
				this.button = button;
				this.buttonX = SettingsList.this.width / 2 - 100;
			}
			
			@Override
			public void renderContent(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, boolean hovered, float delta) {
				// Обновляем позицию кнопки
				this.button.setX(this.buttonX);
				this.button.setY(y + 2);
				// Используем сохраненные координаты мыши из списка
				this.button.render(guiGraphics, SettingsList.this.currentMouseX, SettingsList.this.currentMouseY, delta);
			}
			
			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(this.button);
			}
			
			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(this.button);
			}
		}
		
		/**
		 * Запись с CycleButton
		 */
		private class CycleButtonEntry extends Entry {
			private final AbstractWidget widget;
			private final int widgetX;
			
			public CycleButtonEntry(AbstractWidget widget) {
				this.widget = widget;
				this.widgetX = SettingsList.this.width / 2 - 100;
			}
			
			@Override
			public void renderContent(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, boolean hovered, float delta) {
				// Обновляем позицию виджета
				this.widget.setX(this.widgetX);
				this.widget.setY(y + 2);
				// Используем сохраненные координаты мыши из списка
				this.widget.render(guiGraphics, SettingsList.this.currentMouseX, SettingsList.this.currentMouseY, delta);
			}
			
			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(this.widget);
			}
			
			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(this.widget);
			}
		}
		
		/**
		 * Запись с ползунком
		 */
		private class SliderEntry extends Entry {
			private final AbstractSliderButton slider;
			private final int sliderX;
			
			public SliderEntry(AbstractSliderButton slider) {
				this.slider = slider;
				this.sliderX = SettingsList.this.width / 2 - 100;
			}
			
			@Override
			public void renderContent(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, boolean hovered, float delta) {
				// Обновляем позицию ползунка
				this.slider.setX(this.sliderX);
				this.slider.setY(y + 2);
				// Используем сохраненные координаты мыши из списка
				this.slider.render(guiGraphics, SettingsList.this.currentMouseX, SettingsList.this.currentMouseY, delta);
			}
			
			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(this.slider);
			}
			
			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(this.slider);
			}
		}
	}
		
	
	@Override
	public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		// Рендерим базовый экран (включает фон и все виджеты)
		super.render(guiGraphics, mouseX, mouseY, delta);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.settingsList != null) {
			return this.settingsList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
	
	@Override
	public void onClose() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) {
			mc.setScreen(this.parent);
		}
	}
}
