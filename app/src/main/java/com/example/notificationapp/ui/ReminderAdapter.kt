package com.example.notificationapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.notificationapp.R
import com.example.notificationapp.data.ReminderEntity

class ReminderAdapter(private val onClick: (ReminderEntity) -> Unit) :
    ListAdapter<ReminderEntity, ReminderAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReminderEntity>() {
            override fun areItemsTheSame(a: ReminderEntity, b: ReminderEntity) = a.id == b.id
            override fun areContentsTheSame(a: ReminderEntity, b: ReminderEntity) = a == b
        }
        private val sdf = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvIcon)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val detail: TextView = v.findViewById(R.id.tvDetail)
        val time: TextView = v.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.icon.text = when (item.type) {
            ReminderEntity.TYPE_ALARM -> "🔔"
            ReminderEntity.TYPE_CALENDAR -> "📅"
            else -> "📧"
        }
        holder.title.text = item.title
        holder.detail.text = item.message
        holder.time.text = sdf.format(Date(item.time))
        holder.itemView.setOnClickListener { onClick(item) }
    }

    /** Called from ItemTouchHelper.onMove while dragging */
    fun moveItem(from: Int, to: Int) {
        val list = currentList.toMutableList()
        list.add(to, list.removeAt(from))
        submitList(list)
    }
}
