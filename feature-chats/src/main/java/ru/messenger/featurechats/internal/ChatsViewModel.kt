package ru.messenger.featurechats.internal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.messenger.common.*

internal data class CreateState(val busy: Boolean = false, val error: String? = null, val success: Boolean = false)
internal class ChatsViewModel(private val repository: MessengerRepository, private val saved: SavedStateHandle) : ViewModel() {
    var listScroll: android.os.Parcelable?
        get() = saved["listScroll"]
        set(value) { saved["listScroll"] = value }
    var searchScroll: android.os.Parcelable?
        get() = saved["searchScroll"]
        set(value) { saved["searchScroll"] = value }
    val query = saved.getStateFlow("query", "")
    val createName = saved.getStateFlow("createName", "")
    private val loaded = MutableStateFlow<List<Chat>>(emptyList())
    val searchResults = combine(loaded, query) { chats, q -> filterLoadedChats(chats, q) }
    val refresh = Channel<Unit>(Channel.CONFLATED)
    val createState = MutableStateFlow(CreateState())
    val pages = Pager(PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false)) {
        ChatPagingSource(repository) { offset, chats ->
            loaded.value = ((if (offset == 0) emptyList() else loaded.value) + chats).distinctBy { it.id }
        }
    }.flow.cachedIn(viewModelScope)
    fun search(text: String) { saved["query"] = text }
    fun name(text: String) { saved["createName"] = text }
    fun resetCreate() { createState.value = CreateState(); saved["createName"] = "" }
    fun create() {
        val name = createName.value.trim()
        if (name.isBlank() || createState.value.busy) return
        createState.value = CreateState(busy = true)
        viewModelScope.launch {
            try {
                repository.createChat(name)
                refresh.trySend(Unit)
                createState.value = CreateState(success = true)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { createState.value = CreateState(error = e.message ?: "Не удалось создать чат") }
        }
    }
}
