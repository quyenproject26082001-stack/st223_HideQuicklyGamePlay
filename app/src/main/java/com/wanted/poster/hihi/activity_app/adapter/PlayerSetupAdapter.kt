package com.wanted.poster.hihi.activity_app.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.activity_app.game.PlayerSetupModel
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.databinding.ItemPlayerSetupBinding

class PlayerSetupAdapter(
    private val players: MutableList<PlayerSetupModel>,
    private val onAvatarClick: (Int) -> Unit,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<PlayerSetupAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPlayerSetupBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlayerSetupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = players.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = players[position]
        val ctx = holder.itemView.context

        holder.binding.tvPlayerNumber.text = (position + 1).toString()
        holder.binding.tvPlayerName.text = player.name

        loadAvatar(holder, player.avatarPath)

        holder.binding.flAvatar.setOnSingleClick {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onAvatarClick(pos)
        }
        holder.binding.ivEditAvatar.setOnSingleClick {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onAvatarClick(pos)
        }
        holder.binding.btnEditPlayer.setOnSingleClick {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onEditClick(pos)
        }
        holder.binding.btnDeletePlayer.setOnSingleClick {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDeleteClick(pos)
        }
    }

    private fun loadAvatar(holder: ViewHolder, avatarPath: String?) {
        val ctx = holder.itemView.context
        when {
            avatarPath == null -> holder.binding.ivAvatar.setImageBitmap(null)
            avatarPath.startsWith("flag:") -> {
                val resId = avatarPath.removePrefix("flag:").toIntOrNull()
                if (resId != null) holder.binding.ivAvatar.setImageResource(resId)
            }
            else -> {
                try {
                    ctx.assets.open(avatarPath).use { stream ->
                        holder.binding.ivAvatar.setImageBitmap(BitmapFactory.decodeStream(stream))
                    }
                } catch (_: Exception) {
                    holder.binding.ivAvatar.setImageBitmap(null)
                }
            }
        }
    }
}
