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
	// Намеренно не перехватываем handleSystemMessage здесь:
	// Fabric API ALLOW_GAME уже обрабатывает серверные системные сообщения в KksChatModClient.
	// Если добавить вызов onSystemMessage ещё и здесь, сообщение отображается дважды —
	// ChatListenerMixin.HEAD срабатывает ДО того как ALLOW_GAME успевает отменить обработку.
}

