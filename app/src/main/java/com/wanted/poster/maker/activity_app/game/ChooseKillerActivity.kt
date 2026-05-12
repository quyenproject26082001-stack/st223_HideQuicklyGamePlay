package com.wanted.poster.maker.activity_app.game

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.wanted.poster.maker.R
import com.wanted.poster.maker.activity_app.adapter.KillerAdapter
import com.wanted.poster.maker.data.model.KillerModel

class ChooseKillerActivity : AppCompatActivity() {





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_killer)

        val rvKiller = findViewById<RecyclerView>(R.id.rvKiller)

        val files = assets.list("killer_removebg") ?: emptyArray()

        val killerList = files.map {fileName ->

            KillerModel(
                name = fileName.substringBeforeLast("."),
                imageAssetPath = "killer_removebg/$fileName"
            )

        }


        val adapter = KillerAdapter(killerList.toMutableList())
        rvKiller.adapter = adapter
    }
}