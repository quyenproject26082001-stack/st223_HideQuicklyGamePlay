package com.wanted.poster.maker.activity_app.intro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.wanted.poster.maker.core.base.BaseActivity
import com.wanted.poster.maker.core.utils.DataLocal
import com.wanted.poster.maker.databinding.ActivityIntroBinding
import com.wanted.poster.maker.activity_app.main.MainActivity
import com.wanted.poster.maker.activity_app.permission.PermissionActivity
import com.wanted.poster.maker.core.extensions.hideNavigation
import com.wanted.poster.maker.core.extensions.setOnSingleClick
//quyen
import com.lvt.ads.util.Admob
import com.wanted.poster.maker.R
import com.wanted.poster.maker.core.extensions.visible
import com.wanted.poster.maker.core.extensions.gone
import androidx.viewpager2.widget.ViewPager2
import kotlin.apply
//quyen
import kotlin.system.exitProcess

class IntroActivity : BaseActivity<ActivityIntroBinding>() {
    private val introAdapter by lazy { IntroAdapter(this) }
    private lateinit var dots: Array<ImageView>

    var listFragment = 3

    override fun setViewBinding(): ActivityIntroBinding {
        return ActivityIntroBinding.inflate(LayoutInflater.from(this))
    }

    override fun initView() {
        initVpg()
    }

    override fun onResume() {
        super.onResume()
        hideNavigation(false)
    }

    override fun viewListener() {
        binding.btnNext.setOnSingleClick { handleNext() }
    }

    override fun initText() {}

    override fun initActionBar() {}

    //quyen
    override fun initAds() {
        // Load native ad
//        Admob.getInstance().loadNativeAd(
//            this,
//            getString(R.string.native_intro),
//            binding.nativeIntro,
//            R.layout.ads_native_avg2_btn_bottom
//        )
    }
    //quyen
    fun dpToPx(dp: Float, context: Context): Float {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addBottomDots(currentPage: Int) {
        binding.apply {
            linearDots.removeAllViews()
            dots = Array(3) { ImageView(applicationContext) }
            for (i in 0..<listFragment) {
                dots[i] = ImageView(applicationContext)
                if (i == currentPage) {
                    dots[i]
                        .setImageDrawable(resources.getDrawable(R.drawable.ic_bg_select))
                    val params = LinearLayout.LayoutParams(
                        dpToPx(
                            20f,
                            applicationContext
                        ).toInt(),
                        dpToPx(8f, applicationContext)
                            .toInt()
                    )
                    params.setMargins(4, 0, 4, 0)
                    linearDots.addView(dots[i], params)
                } else {
                    dots[i]
                        .setImageDrawable(
                            resources.getDrawable(R.drawable.ic_bg_not_select)
                        )
                    val params = LinearLayout.LayoutParams(
                        dpToPx(8f, applicationContext)
                            .toInt(),
                        dpToPx(8f, applicationContext)
                            .toInt()
                    )
                    params.setMargins(4, 0, 4, 0)
                    linearDots.addView(dots[i], params)
                }
            }
        }

    }

    private fun initVpg() {
        binding.apply {
            binding.vpgTutorial.adapter = introAdapter
            introAdapter.submitList(DataLocal.itemIntroList)

            //quyen
            // Show/hide native ad based on current page (show only on page 0 and 2)
            binding.vpgTutorial.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    Handler(Looper.myLooper()!!).postDelayed({  addBottomDots(position) },100)
                }
            })
            //quyen
        }
    }

    private fun handleNext() {
        binding.apply {
            val nextItem = binding.vpgTutorial.currentItem + 1
            if (nextItem < DataLocal.itemIntroList.size) {
                vpgTutorial.setCurrentItem(nextItem, true)
            } else {
                val intent =
                    if (sharePreference.getIsFirstPermission()) {
                        Intent(this@IntroActivity, PermissionActivity::class.java)
                    } else {
                        Intent(this@IntroActivity, MainActivity::class.java)
                    }
                startActivity(intent)
                finish()
            }
        }
    }

    @SuppressLint("MissingSuperCall", "GestureBackNavigation")
    override fun onBackPressed() { exitProcess(0) }
}
