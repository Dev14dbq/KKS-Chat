package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.screen.KksChatSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для перехвата открытия экрана настроек чата и замены его на наш экран настроек.
 */
@Mixin(OptionsSubScreen.class)
public abstract class ChatOptionsScreenMixin extends Screen {

	@Shadow
	protected Screen lastScreen;

	private ChatOptionsScreenMixin(Component title) {
		super(title);
	}

	@Inject(
		method = "init()V",
		at = @At("HEAD")
	)
	private void interceptChatOptionsScreen(CallbackInfo ci) {
		// Проверяем, что это экран настроек чата
		Component title = this.getTitle();
		if (title == null) {
			return;
		}
		
		String titleKey = title.getString();
		// Проверяем по ключу перевода или тексту, что это экран настроек чата
		if (titleKey.contains("Chat") || titleKey.contains("чат") || 
		    titleKey.toLowerCase().contains("chat") || 
		    title.toString().contains("options.chat.title")) {
			// Заменяем экран на наш экран настроек
			Minecraft.getInstance().setScreen(new KksChatSettingsScreen(this.lastScreen));
		}
	}
}

