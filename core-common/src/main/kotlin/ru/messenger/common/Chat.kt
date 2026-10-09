package ru.messenger.common

/** Domain model, independent of the still unverified server JSON schema. */
data class Chat(val id: Long, val name: String)

fun filterLoadedChats(chats: List<Chat>, query: String): List<Chat> {
    val normalized = query.trim()
    return chats.distinctBy { it.id }.filter { it.name.contains(normalized, ignoreCase = true) }
}

fun nextOffset(offset: Int, received: Int, total: Int): Int? {
    require(offset >= 0 && received >= 0 && total >= 0)
    val next = offset.toLong() + received
    return if (received == 0 || next >= total) null else next.toInt()
}

fun validMessage(text: String): Boolean = text.isNotBlank()
