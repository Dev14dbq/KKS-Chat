package com.ddev14.kkschat.chat;

/**
 * Тип чат-сообщения. Взаимоисключающие категории — ровно одно значение на сообщение.
 * Используется вместо набора булевых флагов в {@link ChatMessageEntry}.
 */
public enum MessageType {

    /** Обычное сообщение от игрока (player chat). */
    PLAYER_CHAT,

    /** Шёпот (в обе стороны: входящий и исходящий). */
    WHISPER,

    /** Вход / выход игрока с сервера. */
    JOIN_LEAVE,

    /** Достижение (task / goal). */
    ACHIEVEMENT,

    /** Испытание (challenge). */
    CHALLENGE,

    /** Ошибка команды или движка. */
    ERROR,

    /** Сообщение про кровать / сон / грозу. */
    SLEEP,

    /** Уведомление о сохранённом скриншоте. */
    SCREENSHOT,

    /** Любое другое системное сообщение. */
    SYSTEM;

    // ----- helpers -----

    public boolean isSystem() {
        return this != PLAYER_CHAT && this != WHISPER;
    }

    public boolean isPlayerRelated() {
        return this == PLAYER_CHAT || this == WHISPER;
    }
}
