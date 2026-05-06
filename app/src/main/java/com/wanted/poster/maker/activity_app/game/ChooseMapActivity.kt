package com.wanted.poster.maker.activity_app.game

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.maker.R
import com.wanted.poster.maker.databinding.ActivityChooseMapBinding

class ChooseMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseMapBinding

    private val mapOptions: List<MapOption> by lazy {
        (1..47).map { i -> MapOption(mapIndex = i, pathMode = PATH_MODE_ASTAR, label = "Map $i") }
    }

    private var selectedOption: MapOption? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        val adapter = MapOptionAdapter(this, mapOptions) { option ->
            selectedOption = option
            binding.btnSelect.isEnabled = true
            binding.btnSelect.alpha = 1f
            binding.btnSelect.text = "SELECT  ${option.label}"
        }

        binding.rvMaps.layoutManager = GridLayoutManager(this, 2)
        binding.rvMaps.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSelect.setOnClickListener {
            val opt = selectedOption ?: return@setOnClickListener
            startActivity(Intent(this, ChooseNumberActivity::class.java).apply {
                putExtra(ChooseNumberActivity.EXTRA_MAP, opt.mapIndex)
                putExtra(ChooseNumberActivity.EXTRA_KILLER, 1)
                putExtra(EXTRA_PATH_MODE, opt.pathMode)
            })
        }
    }

    private fun makeFullscreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullscreen()
    }

    data class MapOption(
        val mapIndex: Int,
        val pathMode: String,
        val label: String
    )

    class MapOptionAdapter(
        private val ctx: Context,
        private val items: List<MapOption>,
        private val onSelected: (MapOption) -> Unit
    ) : RecyclerView.Adapter<MapOptionAdapter.VH>() {

        private var selectedPos = -1

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgMap)
            val tvLabel: TextView = view.findViewById(R.id.tvMapLabel)
            val tvApproach: TextView = view.findViewById(R.id.tvApproach)
            val viewSelected: View = view.findViewById(R.id.viewSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(ctx).inflate(R.layout.item_map, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val opt = items[position]
            try {
                val bmp = ctx.assets.open("Map/${opt.mapIndex}.jpg")
                    .use { BitmapFactory.decodeStream(it) }
                holder.img.setImageBitmap(bmp)
            } catch (_: Exception) {}

            holder.tvLabel.text = opt.label
            holder.tvApproach.visibility = View.GONE

            val isSelected = selectedPos == position
            holder.viewSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                val prev = selectedPos
                selectedPos = holder.adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onSelected(opt)
            }
        }
    }

    companion object {
        const val EXTRA_PATH_MODE = "extra_path_mode"
        const val PATH_MODE_ASTAR = "astar"
        const val PATH_MODE_WAYPOINTS = "waypoints"
    }
}
