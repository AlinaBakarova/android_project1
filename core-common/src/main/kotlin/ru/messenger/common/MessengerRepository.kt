package ru.messenger.common

data class Message(val id: Long, val text: String)
data class ChatPage(val chats: List<Chat>, val total: Int, val offset: Int)
interface MessengerRepository {
    suspend fun chats(limit: Int, offset: Int): ChatPage
    suspend fun messages(chatId: Long): List<Message>
    suspend fun createChat(name: String)
    suspend fun send(chatId: Long, text: String): List<Message>
}

/** Only safe, user-facing text is propagated to screens. */
class MessengerException(message: String) : Exception(message)
