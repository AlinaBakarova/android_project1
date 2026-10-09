package ru.messenger.featurechats.internal

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import ru.messenger.common.*

internal class ChatPagingSource(
    private val repository: MessengerRepository,
    private val onPage: (Int, List<Chat>) -> Unit = { _, _ -> }
) : PagingSource<Int, Chat>() {
    private val seen = hashSetOf<Long>()
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Chat> = try {
        val offset = params.key ?: 0
        val page = repository.chats(20, offset)
        val unique = page.chats.filter { seen.add(it.id) }
        onPage(offset, unique)
        LoadResult.Page(unique, null, nextOffset(offset, page.chats.size, page.total))
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { LoadResult.Error(e) }
    override fun getRefreshKey(state: PagingState<Int, Chat>): Int? = null
}
