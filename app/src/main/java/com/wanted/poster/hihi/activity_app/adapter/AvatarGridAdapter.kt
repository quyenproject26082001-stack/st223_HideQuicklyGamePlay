package com.wanted.poster.hihi.activity_app.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.databinding.ItemAvatarGridBinding

class AvatarGridAdapter(
    private val items: List<AvatarItem>,
    private val onItemClick: (AvatarItem, Int) -> Unit
) : RecyclerView.Adapter<AvatarGridAdapter.ViewHolder>() {

    var selectedPosition = -1

    sealed class AvatarItem {
        data class Asset(val path: String) : AvatarItem()
        data class Flag(val drawableRes: Int) : AvatarItem()
    }

    inner class ViewHolder(val binding: ItemAvatarGridBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAvatarGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        when (item) {
            is AvatarItem.Asset -> {
                try {
                    ctx.assets.open(item.path).use { stream ->
                        holder.binding.ivAvatarItem.setImageBitmap(BitmapFactory.decodeStream(stream))
                    }
                } catch (_: Exception) {
                    holder.binding.ivAvatarItem.setImageBitmap(null)
                }
            }
            is AvatarItem.Flag -> {
                holder.binding.ivAvatarItem.setImageResource(item.drawableRes)
            }
        }

        holder.binding.viewOverlay.visibility =
            if (position == selectedPosition) View.VISIBLE else View.GONE

        holder.itemView.setOnSingleClick {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnSingleClick
            val prev = selectedPosition
            selectedPosition = pos
            if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
            notifyItemChanged(pos)
            onItemClick(item, pos)
        }
    }
}
