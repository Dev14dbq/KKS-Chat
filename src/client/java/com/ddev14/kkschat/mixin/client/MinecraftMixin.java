package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	
	@Inject(
		at = @At("HEAD"),
		method = "handleKeybinds"
	)
	private void onHandleKeybinds(CallbackInfo ci) {
		Minecraft minecraft = (Minecraft)(Object)this;
		// Проверяем флаг открытия экрана достижений
		if (KksChatModClient.getHud().shouldOpenAdvancements()) {
			KksChatModClient.getHud().setShouldOpenAdvancements(false);
			// Открываем экран достижений через обработчик клавиши
			if (minecraft.options != null && minecraft.options.keyAdvancements != null) {
				// Устанавливаем клавишу как нажатую, чтобы она обработалась в этом же тике
				minecraft.options.keyAdvancements.setDown(true);
			}
		}
	}
}

