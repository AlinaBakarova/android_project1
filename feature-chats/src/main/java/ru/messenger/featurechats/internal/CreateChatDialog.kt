package ru.messenger.featurechats.internal

import android.app.Dialog
import android.os.Bundle
import ru.messenger.featurechats.databinding.DialogCreateChatBinding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.messenger.featurechats.api.ChatsDependencies
import kotlinx.coroutines.launch

internal class CreateChatDialog : DialogFragment() {
    private val vm: ChatsViewModel by lazy {
        ViewModelProvider(requireActivity(), (requireActivity().application as ChatsDependencies).chatsFactory.forOwner(requireActivity()))["chats", ChatsViewModel::class.java]
    }
    private lateinit var field: TextInputEditText
    private lateinit var layout: TextInputLayout
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogCreateChatBinding.inflate(layoutInflater)
        layout = binding.root
        field = binding.chatNameInput
        field.setText(vm.createName.value)
        field.doAfterTextChanged { vm.name(it.toString()) }
        return MaterialAlertDialogBuilder(requireContext()).setTitle("Новый чат").setView(binding.root)
            .setNegativeButton("Отмена") { _, _ -> vm.resetCreate() }
            .setPositiveButton("Создать", null).create()
    }
    override fun onStart() {
        super.onStart()
        val alert = dialog as androidx.appcompat.app.AlertDialog
        alert.getButton(-1).setOnClickListener { vm.create() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.createName.collect { alert.getButton(-1).isEnabled = it.isNotBlank() && !vm.createState.value.busy } }
                launch { vm.createState.collect { state ->
                    if (state.success) { vm.resetCreate(); dismiss(); return@collect }
                    isCancelable = !state.busy
                    field.isEnabled = !state.busy
                    alert.getButton(-1).isEnabled = !state.busy && vm.createName.value.isNotBlank()
                    alert.getButton(-1).text = if (state.busy) "Создаём…" else "Создать"
                    alert.getButton(-2).isEnabled = !state.busy
                    layout.error = state.error
                } }
            }
        }
    }
}
