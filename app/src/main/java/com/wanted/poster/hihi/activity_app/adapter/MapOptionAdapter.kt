package com.wanted.poster.hihi.activity_app.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.data.model.MapOption
import com.wanted.poster.hihi.databinding.ItemMapBinding

class MapOptionAdapter(
    private val ctx: Context,
    private val items: List<MapOption>,
    private val onSelected: (MapOption) -> Unit
) : RecyclerView.Adapter<MapOptionAdapter.MapViewHolder>() {

    private var selectedPos = 0

    inner class MapViewHolder(val binding: ItemMapBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapViewHolder
    {

        val inflater = LayoutInflater.from(parent.context)

        val binding = ItemMapBinding.inflate(inflater,parent,false)

        return MapViewHolder(binding)


    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: MapViewHolder, position: Int) {
        val opt = items[position]
        try {
            val bmp = ctx.assets.open("Map/${opt.mapIndex}.jpg")
                .use { BitmapFactory.decodeStream(it) }
            holder.binding.viewMaskedMap.setMapBitmap(bmp)
        } catch (_: Exception) {
            holder.binding.viewMaskedMap.setMapBitmap(null)
        }

        holder.binding.tvMapLabel.text = opt.label

        val isSelected = selectedPos == position
        holder.binding.viewSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.binding.imgSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val prev = selectedPos
            selectedPos = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onSelected(opt)
        }

    }
}
