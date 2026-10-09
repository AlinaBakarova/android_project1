package ru.messenger.featuremessages.internal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import ru.messenger.common.*

internal data class MessagesUiState(
    val messages: List<Message> = emptyList(), val loading: Boolean = false,
    val sending: Boolean = false, val error: String? = null, val scrollVersion: Int = 0
)
internal class MessagesViewModel(private val chatId: Long, private val repository: MessengerRepository, private val saved: SavedStateHandle) : ViewModel() {
    val draft = saved.getStateFlow("draft", "")
    val state = MutableStateFlow(MessagesUiState())
    var firstScroll: Boolean
        get() = saved["firstScroll"] ?: true
        set(value) { saved["firstScroll"] = value }
    var consumedScroll: Int
        get() = saved["consumedScroll"] ?: 0
        set(value) { saved["consumedScroll"] = value }
    var scrollState: android.os.Parcelable?
        get() = saved["scrollState"]
        set(value) { saved["scrollState"] = value }
    init { load() }
    fun edit(text: String) { saved["draft"] = text }
    fun load() {
        if (state.value.loading || state.value.sending) return
        state.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try { state.value = state.value.copy(messages = repository.messages(chatId), loading = false) }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { state.value = state.value.copy(loading = false, error = e.message ?: "Не удалось загрузить сообщения") }
        }
    }
    fun send() {
        val text = draft.value.trim()
        if (!validMessage(text) || state.value.sending || state.value.loading) return
        state.value = state.value.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                val result = repository.send(chatId, text)
                saved["draft"] = ""
                state.value = state.value.copy(messages = result, sending = false, scrollVersion = state.value.scrollVersion + 1)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { state.value = state.value.copy(sending = false, error = e.message ?: "Не удалось отправить сообщение") }
        }
    }
}
