package com.ddev14.kkschat.mixin.client;

import com.ddev14.kkschat.KksChatModClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для перехвата очистки чата (F3+D) и всех сообщений, добавляемых в чат
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	
	@Inject(
		method = "clearMessages",
		at = @At("HEAD")
	)
	private void onClearMessages(boolean clearHistory, CallbackInfo ci) {
		// Когда стандартный чат очищается (F3+D), очищаем и наш кастомный чат
		KksChatModClient.getHud().clearChat();
	}
	
	/**
	 * Перехватывает все сообщения, которые добавляются в чат напрямую
	 * Это ловит сообщения, которые не проходят через ClientReceiveMessageEvents или ChatListener
	 * (например, сообщения от модов при входе в мир, которые добавляются напрямую в ChatComponent)
	 * 
	 * Блокируем добавление сообщения в стандартный чат, если наш чат включен.
	 */
	@Inject(
		method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void kkschat$onAddMessage(Component message, CallbackInfo ci) {
		// Проверяем, что сообщение не пустое
		if (message != null && !message.getString().isEmpty()) {
			// Всегда сохраняем сообщение в наш HUD (даже когда выключен)
			KksChatModClient.getHud().onSystemMessage(message);
			
			// Блокируем добавление в стандартный чат, если наш чат включен
			if (KksChatModClient.getHud().isEnabled()) {
				ci.cancel();
			}
		}
	}
}

