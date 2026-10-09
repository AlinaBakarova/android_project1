package ru.messenger.featurechats.internal

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import ru.messenger.common.*

class ChatPagingSourceTest {
    private class FakeRepository : MessengerRepository {
        var fail = false
        override suspend fun chats(limit: Int, offset: Int): ChatPage {
            if (fail) throw MessengerException("offline")
            return if (offset == 0) ChatPage(listOf(Chat(1,"a"),Chat(2,"b")),3,0)
                else ChatPage(listOf(Chat(2,"b")),3,offset)
        }
        override suspend fun messages(chatId: Long) = emptyList<Message>()
        override suspend fun createChat(name: String) { }
        override suspend fun send(chatId: Long,text: String) = emptyList<Message>()
    }
    @Test fun advancesByRawCountAndRemovesDuplicates() = runTest {
        val source=ChatPagingSource(FakeRepository())
        val first=source.load(PagingSource.LoadParams.Refresh(null,20,false)) as PagingSource.LoadResult.Page
        assertEquals(2,first.nextKey)
        val last=source.load(PagingSource.LoadParams.Append(2,20,false)) as PagingSource.LoadResult.Page
        assertTrue(last.data.isEmpty())
        assertNull(last.nextKey)
    }
    @Test fun returnsLoadErrorThenAllowsRetry() = runTest {
        val repo=FakeRepository().apply { fail=true }
        val source=ChatPagingSource(repo)
        val params=PagingSource.LoadParams.Refresh<Int>(null,20,false)
        assertTrue(source.load(params) is PagingSource.LoadResult.Error)
        repo.fail=false
        assertTrue(source.load(params) is PagingSource.LoadResult.Page)
    }
}
