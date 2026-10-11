package com.example.notificationapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationapp.R
import com.example.notificationapp.data.ReminderEntity

class ReminderTouchCallback(
    context: Context,
    private val adapter: ReminderAdapter,
    private val onDragEnded: () -> Unit,
    private val onSwiped: (ReminderEntity, Int) -> Unit
) : ItemTouchHelper.Callback() {

    private val background = ColorDrawable()
    private val backgroundColor = android.graphics.Color.parseColor("#E53935")
    private val deleteIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_delete)
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private var dragMoved = false

    override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
        makeMovementFlags(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,      // ↕ drag
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT    // ↔ swipe delete
        )

    override fun isLongPressDragEnabled() = true

    override fun onMove(
        rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
    ): Boolean {
        val from = vh.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        adapter.moveItem(from, to)
        dragMoved = true
        return true
    }

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        val pos = vh.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) onSwiped(adapter.currentList[pos], pos)
    }

    override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            vh?.itemView?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.alpha(0.85f)
                ?.setDuration(120)?.start()
        }
        super.onSelectedChanged(vh, actionState)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        vh.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
        if (dragMoved) {
            dragMoved = false
            onDragEnded()
        }
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            if (dX == 0f && !isCurrentlyActive) {
                c.drawRect(itemView.right + dX, itemView.top.toFloat(),
                    itemView.right.toFloat(), itemView.bottom.toFloat(), clearPaint)
            } else {
                background.color = backgroundColor
                if (dX > 0) {
                    background.setBounds(itemView.left, itemView.top,
                        itemView.left + dX.toInt(), itemView.bottom)
                } else {
                    background.setBounds(itemView.right + dX.toInt(), itemView.top,
                        itemView.right, itemView.bottom)
                }
                background.draw(c)

                deleteIcon?.let { icon ->
                    val margin = (itemView.height - icon.intrinsicHeight) / 2
                    val top = itemView.top + margin
                    val bottom = top + icon.intrinsicHeight
                    if (dX > 0) {
                        icon.setBounds(itemView.left + margin, top,
                            itemView.left + margin + icon.intrinsicWidth, bottom)
                    } else {
                        icon.setBounds(itemView.right - margin - icon.intrinsicWidth, top,
                            itemView.right - margin, bottom)
                    }
                    icon.draw(c)
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
