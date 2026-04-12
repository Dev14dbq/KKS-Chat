package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatHud;
import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsSubScreen.class)
public class ChatOptionsScreenMixin {

	@Shadow
	protected OptionsList list;

	private static final String[] POSITION_KEYS = {
		"kkschat.position.center",
		"kkschat.position.center_top",
		"kkschat.position.left_top",
		"kkschat.position.left_bottom",
		"kkschat.position.right_top",
		"kkschat.position.right_bottom"
	};

	@Inject(method = "addContents()V", at = @At("TAIL"))
	private void kkschat_addSettings(CallbackInfo ci) {
		if (((Object) this).getClass() != ChatOptionsScreen.class) return;
		if (this.list == null) return;
		KksChatHud hud = KksChatModClient.getHud();
		if (hud == null) return;

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;

		StringWidget header = new StringWidget(
			Component.translatable("kkschat.settings.header")
				.withStyle(net.minecraft.ChatFormatting.GRAY), font
		);
		this.list.addSmall(header, null);

		// Ряд 1: KKS Chat вкл/выкл | Стилизация
		boolean enabled = hud.isEnabled();
		Button enabledBtn = Button.builder(
			enabledLabel(enabled),
			btn -> {
				boolean next = !KksChatModClient.getHud().isEnabled();
				KksChatModClient.getHud().setEnabled(next);
				btn.setMessage(enabledLabel(next));
			}
		).width(150).build();

		Button modifyTextBtn = Button.builder(
			styleLabel(hud.isModifyMessageText()),
			btn -> {}
		).width(150).build();
		modifyTextBtn.active = false;
		this.list.addSmall(enabledBtn, modifyTextBtn);

		// Ряд 2: Анти-спам | Позиция
		boolean antiSpam = hud.isAntiSpamEnabled();
		Button antiSpamBtn = Button.builder(
			antiSpamLabel(antiSpam),
			btn -> {
				boolean next = !KksChatModClient.getHud().isAntiSpamEnabled();
				KksChatModClient.getHud().setAntiSpamEnabled(next);
				btn.setMessage(antiSpamLabel(next));
			}
		).width(150).build();

		// Ряд 3: Позиция | Прозрачность
		int currentPos = hud.getChatPosition();
		Button positionBtn = Button.builder(
			positionLabel(currentPos),
			btn -> {
				int next = (KksChatModClient.getHud().getChatPosition() + 1) % POSITION_KEYS.length;
				KksChatModClient.getHud().setChatPosition(next);
				btn.setMessage(positionLabel(next));
			}
		).width(150).build();

		AbstractSliderButton opacitySlider = new AbstractSliderButton(
			0, 0, 150, 20,
			opacityLabel(hud.getBackgroundOpacity()),
			hud.getBackgroundOpacity()
		) {
			@Override protected void updateMessage() { setMessage(opacityLabel((float) value)); }
			@Override protected void applyValue() { KksChatModClient.getHud().setBackgroundOpacity((float) value); }
		};

		int displayTime = hud.getDisplayTimeSeconds();
		AbstractSliderButton displayTimeSlider = new AbstractSliderButton(
			0, 0, 150, 20,
			displayTimeLabel(displayTime),
			(displayTime - 1) / 59.0
		) {
			@Override protected void updateMessage() {
				setMessage(displayTimeLabel((int) Math.round(value * 59) + 1));
			}
			@Override protected void applyValue() {
				KksChatModClient.getHud().setDisplayTimeSeconds((int) Math.round(value * 59) + 1);
			}
		};

		int maxHistory = hud.getMaxHistorySize();
		AbstractSliderButton maxVisibleSlider = new AbstractSliderButton(
			0, 0, 150, 20,
			maxMessagesLabel(maxHistory),
			(maxHistory - 50) / 450.0
		) {
			@Override protected void updateMessage() {
				setMessage(maxMessagesLabel((int) Math.round(value * 450) + 50));
			}
			@Override protected void applyValue() {
				KksChatModClient.getHud().setMaxHistorySize((int) Math.round(value * 450) + 50);
			}
		};

		this.list.addSmall(antiSpamBtn, positionBtn);
		this.list.addSmall(opacitySlider, displayTimeSlider);

		// Ряд 4: Макс. сообщений
		this.list.addSmall(maxVisibleSlider, null);
	}

	private static Component onOff(boolean on) {
		return Component.translatable(on ? "kkschat.settings.on" : "kkschat.settings.off");
	}

	private static Component enabledLabel(boolean on) {
		return Component.translatable("kkschat.settings.enabled", onOff(on));
	}

	private static Component styleLabel(boolean on) {
		return Component.translatable("kkschat.settings.styling", onOff(on));
	}

	private static Component antiSpamLabel(boolean on) {
		return Component.translatable("kkschat.settings.antispam", onOff(on));
	}

	private static Component positionLabel(int pos) {
		return Component.translatable("kkschat.settings.position",
			Component.translatable(POSITION_KEYS[pos]));
	}

	private static Component opacityLabel(float v) {
		return Component.translatable("kkschat.settings.opacity", (int) Math.round(v * 100));
	}

	private static Component displayTimeLabel(int secs) {
		return Component.translatable("kkschat.settings.display_time", secs);
	}

	private static Component maxMessagesLabel(int max) {
		return Component.translatable("kkschat.settings.max_messages", max);
	}
}
