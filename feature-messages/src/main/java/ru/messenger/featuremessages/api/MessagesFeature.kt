package ru.messenger.featuremessages.api

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistryOwner
import javax.inject.Inject
import ru.messenger.common.MessengerRepository
import ru.messenger.featuremessages.internal.MessagesFragment
import ru.messenger.featuremessages.internal.MessagesViewModel

object MessagesFeature {
    fun create(chatId: Long, name: String): Fragment = MessagesFragment().apply { arguments = Bundle().apply { putLong("chatId", chatId); putString("name", name) } }
}
interface MessagesDependencies { val messagesFactory: MessagesViewModelFactory }
class MessagesViewModelFactory @Inject constructor(private val repository: MessengerRepository) {
    fun forOwner(owner: SavedStateRegistryOwner, defaults: Bundle? = null): ViewModelProvider.Factory =
        object : AbstractSavedStateViewModelFactory(owner, defaults) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T =
                MessagesViewModel(handle.get<Long>("chatId") ?: error("Missing chat ID"), repository, handle) as T
        }
}
