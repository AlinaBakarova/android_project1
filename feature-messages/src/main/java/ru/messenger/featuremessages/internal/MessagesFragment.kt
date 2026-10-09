package ru.messenger.featuremessages.internal

import android.os.Bundle
import android.view.View
import android.content.res.Configuration
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import ru.messenger.featuremessages.R
import ru.messenger.featuremessages.api.MessagesDependencies
import ru.messenger.featuremessages.databinding.FragmentMessagesBinding

internal class MessagesFragment : Fragment(R.layout.fragment_messages) {
    private val vm: MessagesViewModel by lazy {
        val factory = (requireActivity().application as MessagesDependencies).messagesFactory.forOwner(requireActivity(), requireArguments())
        ViewModelProvider(requireActivity(), factory)["messages-${requireArguments().getLong("chatId")}", MessagesViewModel::class.java]
    }
    private var binding: FragmentMessagesBinding? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMessagesBinding.bind(view)
        binding = b
        // Explicitly reopening a cached chat refreshes its history; rotation keeps its data.
        // The ViewModel's loading guard prevents a second request on its initial creation.
        if (savedInstanceState == null) vm.load()
        val adapter = MessagesAdapter()
        adapter.stateRestorationPolicy = androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        val manager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        b.list.layoutManager = manager
        b.list.adapter = adapter
        b.toolbar.title = requireArguments().getString("name")
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            b.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            b.toolbar.navigationContentDescription = getString(R.string.back)
            b.toolbar.setNavigationOnClickListener {
                ViewCompat.getWindowInsetsController(b.root)?.hide(WindowInsetsCompat.Type.ime())
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        b.input.setText(vm.draft.value)
        b.input.doAfterTextChanged { vm.edit(it.toString()); b.send.isEnabled = it.toString().isNotBlank() && !vm.state.value.sending && !vm.state.value.loading }
        b.send.setOnClickListener { vm.send() }
        b.retry.setOnClickListener { vm.load() }
        var shownError: String? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.draft.collect { text ->
                    if (b.input.text.toString() != text) b.input.setText(text)
                } }
                launch { vm.state.collect { state ->
                    b.status.isVisible = state.messages.isEmpty()
                    b.progress.isVisible = state.loading
                    b.retry.isVisible = state.error != null && !state.sending
                    b.statusText.text = state.error ?: if (state.loading) "Загружаем сообщения…" else getString(R.string.no_messages)
                    b.input.isEnabled = !state.sending
                    b.send.isEnabled = vm.draft.value.isNotBlank() && !state.sending && !state.loading
                    b.send.text = if (state.sending) "…" else getString(R.string.send)
                    if (state.error != null && shownError != state.error) Snackbar.make(b.root, state.error, Snackbar.LENGTH_LONG).show()
                    shownError = state.error
                    adapter.submitList(state.messages) {
                        if (state.messages.isNotEmpty()) {
                            when {
                                state.scrollVersion > vm.consumedScroll -> {
                                    b.list.scrollToPosition(adapter.itemCount - 1)
                                    vm.consumedScroll = state.scrollVersion
                                    vm.firstScroll = false
                                    vm.scrollState = null
                                }
                                vm.scrollState != null -> { manager.onRestoreInstanceState(vm.scrollState); vm.scrollState = null }
                                vm.firstScroll -> { b.list.scrollToPosition(adapter.itemCount - 1); vm.firstScroll = false }
                            }
                        }
                    }
                } }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("messagesViewRestored", true)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        binding?.list?.layoutManager?.onSaveInstanceState()?.let { vm.scrollState = it }
        super.onPause()
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
