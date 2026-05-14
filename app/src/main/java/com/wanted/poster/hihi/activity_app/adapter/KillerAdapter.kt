package com.wanted.poster.hihi.activity_app.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.hihi.core.extensions.setOnSingleClick
import com.wanted.poster.hihi.data.model.KillerModel
import com.wanted.poster.hihi.databinding.ItemKillerBinding

class KillerAdapter(
    private val list: MutableList<KillerModel>,
    private val onItemClick: (KillerModel) -> Unit
) : RecyclerView.Adapter<KillerAdapter.KillerViewHolder>() {


    private var selectedPosition = 0

    inner class KillerViewHolder(private val binding: ItemKillerBinding) : RecyclerView.ViewHolder(binding.root) {

        private fun renderSelection(isSelected: Boolean){
            binding.viewOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        }



        fun onBind(killerItem: KillerModel, position: Int) {

            binding.tvKiller.text = killerItem.name

            itemView.context.assets.open(killerItem.imageAssetPath).use { inputStream ->
                val bitmap =
                    BitmapFactory.decodeStream(inputStream)
                binding.ivKiller.setImageBitmap(bitmap)
            }

            renderSelection(position == selectedPosition)

            itemView.setOnSingleClick {
               val currentPosition = bindingAdapterPosition
                if(currentPosition== RecyclerView.NO_POSITION)
                    return@setOnSingleClick
                val oldPosition = selectedPosition
                selectedPosition = currentPosition

                if(oldPosition != RecyclerView.NO_POSITION){
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(selectedPosition)

                onItemClick(killerItem)
            }

        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): KillerViewHolder {

        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemKillerBinding.inflate(inflater,parent,false)

        return KillerViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: KillerViewHolder,
        position: Int
    ) {
        holder.onBind(list[position], position)

    }

    override fun getItemCount(): Int {
        return list.size
    }

}
