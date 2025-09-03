package com.example.chatbot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

private const val VIEW_USER = 1
private const val VIEW_ASSISTANT = 2
private const val VIEW_SYSTEM = 3

class MessageAdapter (
    private val listener: MessageActionListener
): ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
                oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            Role.USER -> VIEW_USER
            Role.ASSISTANT -> VIEW_ASSISTANT
            Role.SYSTEM -> VIEW_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER -> UserVH(inf.inflate(R.layout.item_message_user, parent, false))
            VIEW_ASSISTANT -> AssistantVH(inf.inflate(R.layout.item_message_assistant, parent, false))
            else -> AssistantVH(inf.inflate(R.layout.item_message_assistant, parent, false)) // SYSTEMはひとまず同UI
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is UserVH -> holder.bind(msg, position, listener)
            is AssistantVH -> holder.bind(msg, position, listener)
        }
    }

    class UserVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv: TextView = itemView.findViewById(R.id.messageTextView)
        fun bind(m: Message, pos: Int, listener: MessageActionListener) {
            tv.text = m.content
            itemView.setOnLongClickListener { v ->
                showPopup(v, m, pos, listener); true
            }
        }
    }

    class AssistantVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv: TextView = itemView.findViewById(R.id.messageTextView)
        fun bind(m: Message, pos: Int, listener: MessageActionListener) {
            tv.text = m.content
            itemView.setOnLongClickListener { v ->
                showPopup(v, m, pos, listener); true
            }
        }
    }
}

private fun showPopup(anchor: View, msg: Message, pos: Int, listener: MessageActionListener) {
    val popup = PopupMenu(anchor.context, anchor)
    popup.menuInflater.inflate(R.menu.message_item_menu, popup.menu)

    // アシスタントメッセージのときだけ「再生成」を表示
    popup.menu.findItem(R.id.action_regen)?.isVisible = (msg.role == Role.ASSISTANT)

    popup.setOnMenuItemClickListener {
        when (it.itemId) {
            R.id.action_copy -> listener.onCopy(msg, pos)
            R.id.action_share -> listener.onShare(msg, pos)
            R.id.action_delete -> listener.onDelete(msg, pos)
            R.id.action_regen -> listener.onRegenerate(msg, pos)
        }
        true
    }
    popup.show()
}
