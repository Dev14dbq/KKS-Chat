package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для перехвата системных сообщений, включая сообщения от модов при входе в мир
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	
	/**
	 * Перехватывает системные сообщения (включая сообщения от модов)
	 * Вызывается для всех системных сообщений, которые добавляются в чат
	 * 
	 * ВАЖНО: Мы сохраняем сообщение в наш HUD, но не блокируем здесь,
	 * так как блокировка происходит в ChatComponentMixin.addMessage()
	 */
	@Inject(
		method = "handleSystemMessage",
		at = @At("HEAD")
	)
	private void kkschat$onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
		// Всегда сохраняем системные сообщения в наш HUD (даже когда выключен)
		// Это перехватывает все системные сообщения, включая сообщения от модов при входе в мир
		// Блокировка добавления в стандартный чат происходит в ChatComponentMixin.addMessage()
		if (message != null && !message.getString().isEmpty()) {
			KksChatModClient.getHud().onSystemMessage(message);
		}
	}
}

