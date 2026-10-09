package ru.messenger.featurechats.internal

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.*
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import ru.messenger.featurechats.R
import ru.messenger.featurechats.api.ChatsDependencies
import ru.messenger.featurechats.databinding.FragmentChatsBinding

internal class ChatsFragment : Fragment(R.layout.fragment_chats) {
    private val vm: ChatsViewModel by lazy {
        val factory = (requireActivity().application as ChatsDependencies).chatsFactory.forOwner(requireActivity())
        ViewModelProvider(requireActivity(), factory)["chats", ChatsViewModel::class.java]
    }
    private var binding: FragmentChatsBinding? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentChatsBinding.bind(view)
        binding = b
        val select: (ru.messenger.common.Chat) -> Unit = { chat ->
            ViewCompat.getWindowInsetsController(b.root)?.hide(WindowInsetsCompat.Type.ime())
            setFragmentResult("selectChat", bundleOf("id" to chat.id, "name" to chat.name))
        }
        val paging = ChatsAdapter(select)
        val search = SearchAdapter(select)
        val normal = paging.withLoadStateFooter(LoadAdapter { paging.retry() })
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = normal
        // Keep the only visible row accessible when the landscape keyboard leaves little height.
        b.list.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            b.create.isVisible = bottom - top >= (144 * resources.displayMetrics.density).toInt()
        }
        b.search.setText(vm.query.value)
        b.search.doAfterTextChanged {
            val oldSearching = vm.query.value.trim().isNotEmpty()
            val newSearching = it.toString().trim().isNotEmpty()
            if (oldSearching != newSearching) {
                val state = b.list.layoutManager?.onSaveInstanceState()
                if (oldSearching) vm.searchScroll = state else vm.listScroll = state
            }
            vm.search(it.toString())
        }
        b.retry.setOnClickListener { paging.retry() }
        b.create.setOnClickListener {
            if (parentFragmentManager.findFragmentByTag("createChat") == null)
                CreateChatDialog().show(parentFragmentManager, "createChat")
        }
        var refreshState: LoadState = LoadState.Loading
        var pendingRestore = true
        fun restoreScroll() {
            if (pendingRestore && (b.list.adapter?.itemCount ?: 0) > 0) {
                val state = if (vm.query.value.trim().isNotEmpty()) vm.searchScroll else vm.listScroll
                state?.let { b.list.layoutManager?.onRestoreInstanceState(it) }
                pendingRestore = false
            }
        }
        fun renderStatus() {
            val filtering = vm.query.value.trim().isNotEmpty()
            val state = refreshState
            val empty = if (filtering) search.itemCount == 0 else paging.itemCount == 0
            b.status.isVisible = empty
            b.progress.isVisible = !filtering && state is LoadState.Loading
            b.retry.isVisible = !filtering && state is LoadState.Error
            b.statusText.text = when {
                filtering -> getString(R.string.no_matches)
                state is LoadState.Error -> state.error.message ?: "Не удалось загрузить чаты"
                state is LoadState.Loading -> "Загружаем чаты…"
                else -> getString(R.string.no_chats)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.pages.collect { paging.submitData(it) } }
                launch { vm.query.collect { q ->
                    val adapter = if (q.trim().isNotEmpty()) search else normal
                    if (b.list.adapter !== adapter) {
                        b.list.adapter = adapter
                        pendingRestore = true
                        restoreScroll()
                    }
                    renderStatus()
                } }
                launch { vm.searchResults.collect { search.submitList(it) { renderStatus(); restoreScroll() } } }
                launch { paging.loadStateFlow.collect { refreshState = it.refresh; renderStatus() } }
                launch { paging.onPagesUpdatedFlow.collect { renderStatus(); restoreScroll() } }
                launch { vm.refresh.receiveAsFlow().collect { paging.refresh() } }
            }
        }
    }
    override fun onPause() {
        binding?.list?.layoutManager?.onSaveInstanceState()?.let {
            if (vm.query.value.trim().isNotEmpty()) vm.searchScroll = it else vm.listScroll = it
        }
        super.onPause()
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
