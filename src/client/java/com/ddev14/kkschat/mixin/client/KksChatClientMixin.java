package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.screen.KksChatSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class KksChatClientMixin {
	
	@Inject(
		at = @At("HEAD"),
		method = "setScreen",
		cancellable = true
	)
	private void interceptChatOptionsScreen(Screen screen, CallbackInfo ci) {
		// Проверяем, открывается ли экран настроек чата
		if (screen instanceof OptionsSubScreen) {
			OptionsSubScreen optionsScreen = (OptionsSubScreen) screen;
			Component title = optionsScreen.getTitle();
			if (title != null) {
				String titleKey = title.getString();
				// Проверяем, что это экран настроек чата
				if (titleKey.contains("Chat") || titleKey.contains("чат") || 
				    titleKey.toLowerCase().contains("chat")) {
					// Получаем родительский экран через рефлексию
					Screen parent = null;
					try {
						java.lang.reflect.Field lastScreenField = OptionsSubScreen.class.getDeclaredField("lastScreen");
						lastScreenField.setAccessible(true);
						parent = (Screen) lastScreenField.get(optionsScreen);
					} catch (Exception e) {
						// Если не удалось получить через рефлексию, используем текущий экран
						parent = Minecraft.getInstance().screen;
					}
					Minecraft.getInstance().setScreen(new KksChatSettingsScreen(parent));
					ci.cancel(); // Отменяем открытие оригинального экрана
				}
			}
		}
	}
}

