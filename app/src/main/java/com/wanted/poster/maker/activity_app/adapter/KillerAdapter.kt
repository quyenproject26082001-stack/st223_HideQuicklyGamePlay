package com.wanted.poster.maker.activity_app.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.maker.R
import com.wanted.poster.maker.data.model.KillerModel

class KillerAdapter(private val list : MutableList<KillerModel>) : RecyclerView.Adapter<KillerAdapter.KillerViewHolder>() {

    inner class KillerViewHolder(view : View) : RecyclerView.ViewHolder(view){

        val tvName = view.findViewById<TextView>(R.id.tvKiller)
        val ivImage = view.findViewById<ImageView>(R.id.ivKiller)
        fun onBind(killerItem: KillerModel){

            tvName.text = killerItem.name

            itemView.context.assets.open(killerItem.imageAssetPath).use {
                inputStream ->
                val bitmap =
                    BitmapFactory.decodeStream(inputStream)
                ivImage.setImageBitmap(bitmap)
            }

        }

    }



    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): KillerViewHolder {

        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_killer,parent,false)
        return KillerViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: KillerViewHolder,
        position: Int
    ) {
        holder.onBind(list[position])

    }

    override fun getItemCount(): Int {
        return list.size
    }


}