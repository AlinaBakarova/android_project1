package ru.messenger.common

import org.junit.Assert.*
import org.junit.Test

class ChatTest {
    @Test fun advancesByActualPageSize() { assertEquals(27, nextOffset(20, 7, 100)) }
    @Test fun endsAtTotal() { assertNull(nextOffset(20, 7, 27)) }
    @Test fun endsOnEmptyPage() { assertNull(nextOffset(20, 0, 100)) }
    @Test fun avoidsOffsetOverflow() { assertNull(nextOffset(Int.MAX_VALUE - 1, 20, Int.MAX_VALUE)) }
    @Test fun filtersLoadedChatsLocally() {
        val input = listOf(Chat(1, "Работа"), Chat(2, "Семья"), Chat(1, "Работа"))
        assertEquals(listOf(Chat(1, "Работа")), filterLoadedChats(input, "  РАБ  "))
        assertEquals(2, filterLoadedChats(input, " ").size)
        assertTrue(filterLoadedChats(input, "нет совпадений").isEmpty())
    }
    @Test fun rejectsBlankMessages() {
        assertFalse(validMessage(" \n\t"))
        assertTrue(validMessage("Привет"))
    }
}
