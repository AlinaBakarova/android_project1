package ru.messenger.featurechats.api

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistryOwner
import javax.inject.Inject
import ru.messenger.common.MessengerRepository
import ru.messenger.featurechats.internal.ChatsFragment
import ru.messenger.featurechats.internal.ChatsViewModel

object ChatsFeature {
    fun create(): Fragment = ChatsFragment()
}
interface ChatsDependencies { val chatsFactory: ChatsViewModelFactory }
class ChatsViewModelFactory @Inject constructor(private val repository: MessengerRepository) {
    fun forOwner(owner: SavedStateRegistryOwner, defaults: Bundle? = null): ViewModelProvider.Factory =
        object : AbstractSavedStateViewModelFactory(owner, defaults) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T =
                ChatsViewModel(repository, handle) as T
        }
}
