package ru.messenger.featuremessages.internal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.*
import ru.messenger.common.Message
import ru.messenger.featuremessages.databinding.RowMessageBinding

internal class MessagesAdapter : ListAdapter<Message, MessagesAdapter.Holder>(object : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
    override fun areContentsTheSame(a: Message, b: Message) = a == b
}) {
    class Holder(val binding: RowMessageBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(RowMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) { holder.binding.text.text = getItem(position).text }
}
