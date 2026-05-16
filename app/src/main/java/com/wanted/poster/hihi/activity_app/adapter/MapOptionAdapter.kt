package com.wanted.poster.hihi.activity_app.adapter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.data.model.MapOption
import com.wanted.poster.hihi.databinding.ItemMapBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapOptionAdapter(
    private val ctx: Context,
    private val items: List<MapOption>,
    initialSelectedPosition: Int = 0,
    private val onSelected: (MapOption) -> Unit
) : RecyclerView.Adapter<MapOptionAdapter.MapViewHolder>() {

    private var selectedPos = if (items.isEmpty()) {
        RecyclerView.NO_POSITION
    } else {
        initialSelectedPosition.coerceIn(0, items.lastIndex)
    }

    private val bitmapCache = LruCache<Int, Bitmap>(20)

    inner class MapViewHolder(val binding: ItemMapBinding) : RecyclerView.ViewHolder(binding.root) {
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapViewHolder {
        val binding = ItemMapBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MapViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: MapViewHolder, position: Int) {
        val opt = items[position]

        holder.loadJob?.cancel()
        holder.binding.viewMaskedMap.setMapBitmap(null)

        val cached = bitmapCache.get(opt.mapIndex)
        if (cached != null) {
            holder.binding.viewMaskedMap.setMapBitmap(cached)
        } else {
            holder.loadJob = CoroutineScope(Dispatchers.Main).launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        ctx.assets.open("Map/${opt.mapIndex}.jpg").use { BitmapFactory.decodeStream(it) }
                    } catch (_: Exception) { null }
                }
                if (bmp != null) bitmapCache.put(opt.mapIndex, bmp)
                if (holder.bindingAdapterPosition == position) {
                    holder.binding.viewMaskedMap.setMapBitmap(bmp)
                }
            }
        }

        holder.binding.tvMapLabel.text = opt.label

        val isSelected = selectedPos == position
        holder.binding.viewSelectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.binding.imgSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            val prev = selectedPos
            selectedPos = currentPosition
            if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
            notifyItemChanged(selectedPos)
            onSelected(items[currentPosition])
        }
    }

    override fun onViewRecycled(holder: MapViewHolder) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
    }
}
