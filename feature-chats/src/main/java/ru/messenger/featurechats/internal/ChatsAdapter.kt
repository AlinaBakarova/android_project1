package ru.messenger.featurechats.internal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.*
import com.bumptech.glide.Glide
import ru.messenger.common.Chat
import ru.messenger.featurechats.R
import ru.messenger.featurechats.databinding.RowChatBinding
import ru.messenger.featurechats.databinding.RowLoadBinding

internal object ChatDiff : DiffUtil.ItemCallback<Chat>() {
    override fun areItemsTheSame(a: Chat, b: Chat) = a.id == b.id
    override fun areContentsTheSame(a: Chat, b: Chat) = a == b
}
internal class ChatHolder(private val b: RowChatBinding, private val click: (Chat) -> Unit) : RecyclerView.ViewHolder(b.root) {
    fun bind(chat: Chat) {
        b.name.text = chat.name
        Glide.with(b.avatar).load(R.drawable.avatar).placeholder(R.drawable.avatar).error(R.drawable.avatar).circleCrop().into(b.avatar)
        b.root.setOnClickListener { click(chat) }
    }
}
internal class ChatsAdapter(private val click: (Chat) -> Unit) : PagingDataAdapter<Chat, ChatHolder>(ChatDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = ChatHolder(RowChatBinding.inflate(LayoutInflater.from(parent.context), parent, false), click)
    override fun onBindViewHolder(holder: ChatHolder, position: Int) { getItem(position)?.let(holder::bind) }
}
internal class SearchAdapter(private val click: (Chat) -> Unit) : ListAdapter<Chat, ChatHolder>(ChatDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = ChatHolder(RowChatBinding.inflate(LayoutInflater.from(parent.context), parent, false), click)
    override fun onBindViewHolder(holder: ChatHolder, position: Int) { holder.bind(getItem(position)) }
}
internal class LoadAdapter(private val retry: () -> Unit) : LoadStateAdapter<LoadAdapter.Holder>() {
    class Holder(val b: RowLoadBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, state: LoadState) = Holder(RowLoadBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, state: LoadState) {
        holder.b.progress.isVisible = state is LoadState.Loading
        holder.b.retry.isVisible = state is LoadState.Error
        holder.b.error.isVisible = state is LoadState.Error
        holder.b.error.text = (state as? LoadState.Error)?.error?.message
        holder.b.retry.setOnClickListener { retry() }
    }
}
