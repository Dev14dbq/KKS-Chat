package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatHud;
import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsSubScreen.class)
public class ChatOptionsScreenMixin {

	@Shadow
	protected OptionsList list;

	@Shadow
	@Final
	protected Screen lastScreen;

	@Shadow
	@Final
	protected Options options;

	@Inject(method = "addContents()V", at = @At("TAIL"))
	private void kkschat_addSettings(CallbackInfo ci) {
		if (((Object) this).getClass() != ChatOptionsScreen.class) return;
		if (this.list == null) return;
		KksChatHud hud = KksChatModClient.getHud();
		if (hud == null) return;

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;

		// Заголовок секции
		StringWidget header = new StringWidget(
			Component.literal("── KKS Chat ──"), font
		).setColor(0xFFAAAAAA);
		this.list.addSmall(header, null);

		// Стилизация текста | Прозрачность фона
		boolean modifyText = hud.isModifyMessageText();
		Button modifyTextBtn = Button.builder(
			Component.literal("Стилизация текста: " + (modifyText ? "ВКЛ" : "ВЫКЛ")),
			btn -> {
				boolean next = !KksChatModClient.getHud().isModifyMessageText();
				KksChatModClient.getHud().setModifyMessageText(next);
				btn.setMessage(Component.literal("Стилизация текста: " + (next ? "ВКЛ" : "ВЫКЛ")));
			}
		).width(150).build();

		AbstractSliderButton opacitySlider = new AbstractSliderButton(
			0, 0, 150, 20,
			Component.literal("Прозрачность: " + (int) Math.round(hud.getBackgroundOpacity() * 100) + "%"),
			hud.getBackgroundOpacity()
		) {
			@Override
			protected void updateMessage() {
				setMessage(Component.literal("Прозрачность: " + (int) Math.round(value * 100) + "%"));
			}
			@Override
			protected void applyValue() {
				KksChatModClient.getHud().setBackgroundOpacity((float) value);
			}
		};
		this.list.addSmall(modifyTextBtn, opacitySlider);

		// Позиционирование чата
		String[] positions = {"По центру", "По центру сверху", "Слева сверху", "Слева снизу", "Справа сверху", "Справа снизу"};
		int currentPos = hud.getChatPosition();
		Button positionBtn = Button.builder(
			Component.literal("Позиция: " + positions[currentPos]),
			btn -> {
				int next = (KksChatModClient.getHud().getChatPosition() + 1) % positions.length;
				KksChatModClient.getHud().setChatPosition(next);
				btn.setMessage(Component.literal("Позиция: " + positions[next]));
			}
		).width(200).build();
		this.list.addSmall(positionBtn, null);

		// Время отображения | Макс. сообщений
		int displayTime = hud.getDisplayTimeSeconds();
		AbstractSliderButton displayTimeSlider = new AbstractSliderButton(
			0, 0, 150, 20,
			Component.literal("Время показа: " + displayTime + "с"),
			(displayTime - 1) / 59.0
		) {
			@Override
			protected void updateMessage() {
				int secs = (int) Math.round(value * 59) + 1;
				setMessage(Component.literal("Время показа: " + secs + "с"));
			}
			@Override
			protected void applyValue() {
				KksChatModClient.getHud().setDisplayTimeSeconds((int) Math.round(value * 59) + 1);
			}
		};

		int maxHistory = hud.getMaxHistorySize();
		AbstractSliderButton maxVisibleSlider = new AbstractSliderButton(
			0, 0, 150, 20,
			Component.literal("Макс. сообщений: " + maxHistory),
			(maxHistory - 50) / 450.0
		) {
			@Override
			protected void updateMessage() {
				int max = (int) Math.round(value * 450) + 50;
				setMessage(Component.literal("Макс. сообщений: " + max));
			}
			@Override
			protected void applyValue() {
				KksChatModClient.getHud().setMaxHistorySize((int) Math.round(value * 450) + 50);
			}
		};
		this.list.addSmall(displayTimeSlider, maxVisibleSlider);

		// Анти-спам | Сбросить настройки
		boolean antiSpam = hud.isAntiSpamEnabled();
		Button antiSpamBtn = Button.builder(
			Component.literal("Анти-спам: " + (antiSpam ? "ВКЛ" : "ВЫКЛ")),
			btn -> {
				boolean next = !KksChatModClient.getHud().isAntiSpamEnabled();
				KksChatModClient.getHud().setAntiSpamEnabled(next);
				btn.setMessage(Component.literal("Анти-спам: " + (next ? "ВКЛ" : "ВЫКЛ")));
			}
		).width(150).build();

		Screen parentScreen = this.lastScreen;
		Options opts = this.options;
		Button resetBtn = Button.builder(
			Component.literal("Сбросить настройки"),
			btn -> {
				KksChatModClient.getHud().resetSettings();
				Minecraft.getInstance().setScreen(new ChatOptionsScreen(parentScreen, opts));
			}
		).width(150).build();
		this.list.addSmall(antiSpamBtn, resetBtn);
	}
}
