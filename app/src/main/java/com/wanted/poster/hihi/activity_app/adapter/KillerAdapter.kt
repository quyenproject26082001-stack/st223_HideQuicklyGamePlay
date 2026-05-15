package com.wanted.poster.hihi.activity_app.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.data.model.KillerModel
import com.wanted.poster.hihi.databinding.ItemKillerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KillerAdapter(
    private val list: MutableList<KillerModel>,
    private val onItemClick: (KillerModel) -> Unit
) : RecyclerView.Adapter<KillerAdapter.KillerViewHolder>() {

    private var selectedPosition = 0
    private val bitmapCache = LruCache<String, Bitmap>(30)

    inner class KillerViewHolder(private val binding: ItemKillerBinding) : RecyclerView.ViewHolder(binding.root) {
        var loadJob: Job? = null

        fun onBind(killerItem: KillerModel, position: Int) {
            binding.tvKiller.text = killerItem.name

            loadJob?.cancel()
            binding.ivKiller.setImageBitmap(null)

            val cached = bitmapCache.get(killerItem.imageAssetPath)
            if (cached != null) {
                binding.ivKiller.setImageBitmap(cached)
            } else {
                loadJob = CoroutineScope(Dispatchers.Main).launch {
                    val bmp = withContext(Dispatchers.IO) {
                        try {
                            itemView.context.assets.open(killerItem.imageAssetPath)
                                .use { BitmapFactory.decodeStream(it) }
                        } catch (_: Exception) { null }
                    }
                    if (bmp != null) bitmapCache.put(killerItem.imageAssetPath, bmp)
                    if (bindingAdapterPosition == position) {
                        binding.ivKiller.setImageBitmap(bmp)
                    }
                }
            }

            binding.viewOverlay.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

            itemView.setOnSingleClick {
                val currentPosition = bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnSingleClick
                val oldPosition = selectedPosition
                selectedPosition = currentPosition
                if (oldPosition != RecyclerView.NO_POSITION) notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
                onItemClick(killerItem)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KillerViewHolder {
        val binding = ItemKillerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KillerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KillerViewHolder, position: Int) {
        holder.onBind(list[position], position)
    }

    override fun onViewRecycled(holder: KillerViewHolder) {
        super.onViewRecycled(holder)
        holder.loadJob?.cancel()
    }

    override fun getItemCount() = list.size
}
