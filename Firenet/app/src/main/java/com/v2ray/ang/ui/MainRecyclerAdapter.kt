package com.v2ray.ang.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>() {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
    }

    private var mActivity: MainActivity = activity
    var isRunning = false
    private var switchJob: Job? = null

    override fun getItemCount() = mActivity.mainViewModel.serversCache.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val serverData = mActivity.mainViewModel.serversCache.getOrNull(position) ?: return
            val guid = serverData.guid
            val profile = serverData.profile
            
            holder.itemMainBinding.tvName.text = profile.remarks
            val isSelected = (guid == MmkvManager.getSelectServer())

            updateUI(holder, isSelected)

            holder.itemView.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                setSelectServer(guid, position)
                mActivity.scrollToPositionCentered(position)
            }
        }
    }

private fun updateUI(holder: MainViewHolder, isSelected: Boolean) {
    val binding = holder.itemMainBinding
    val serverData = mActivity.mainViewModel.serversCache.getOrNull(holder.layoutPosition) ?: return
    val remarks = serverData.profile.remarks // نام سرور

    binding.layoutIndicator.clearAnimation()

    // --- منطق تشخیص پرچم ---
    val countryCode = getCountryCode(remarks)
    if (countryCode.isNotEmpty()) {
        val flagResId = com.blongho.country_data.World.getFlagOf(countryCode)
        binding.ivStatusIcon.setImageResource(flagResId)
        binding.ivStatusIcon.colorFilter = null // حذف فیلتر رنگ برای نمایش رنگ واقعی پرچم
    } else {
        binding.ivStatusIcon.setImageResource(R.drawable.ic_server_idle)
        binding.ivStatusIcon.setColorFilter(if (isSelected) Color.WHITE else Color.LTGRAY)
    }

    if (isSelected) {
        // حالت انتخاب شده
        binding.layoutIndicator.setBackgroundResource(R.drawable.bg_server_active)
        binding.layoutIndicator.backgroundTintList = null 
        binding.tvName.setTextColor(Color.parseColor("#00E5FF")) 
        binding.tvName.maxLines = 2

        // انیمیشن پاپ و نبض
        binding.layoutIndicator.animate().scaleX(1.15f).scaleY(1.15f).setDuration(300)
            .setInterpolator(OvershootInterpolator()).start()

        val pulseAnim = AlphaAnimation(0.7f, 1.0f).apply {
            duration = 800
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.layoutIndicator.startAnimation(pulseAnim)

        // نمایش هاله نوری (اگر در XML اضافه کردیم)
        binding.layoutIndicator.alpha = 1.0f

    } else {
        // حالت عادی (شیشه‌ای)
        binding.layoutIndicator.setBackgroundResource(R.drawable.bg_glass_input)
        binding.layoutIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        binding.tvName.setTextColor(Color.WHITE)
        binding.tvName.maxLines = 1

        binding.layoutIndicator.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
    }
}

// تابع کمکی برای تشخیص کد کشور از روی نام سرور
private fun getCountryCode(remarks: String): String {
    val r = remarks.lowercase()
    return when {
        r.contains("germany") || r.contains("آلمان") || r.contains(" de ") || r.startsWith("de-") -> "de"
        r.contains("usa") || r.contains("united states") || r.contains("آمریکا") || r.contains(" us ") -> "us"
        r.contains("turkey") || r.contains("ترکیه") || r.contains(" tr ") -> "tr"
        r.contains("united kingdom") || r.contains("انگلیس") || r.contains(" uk ") || r.contains(" gb ") -> "gb"
        r.contains("france") || r.contains("فرانسه") || r.contains(" fr ") -> "fr"
        r.contains("netherlands") || r.contains("هلند") || r.contains(" nl ") -> "nl"
        r.contains("canada") || r.contains("کانادا") || r.contains(" ca ") -> "ca"
        r.contains("finland") || r.contains("فنلاند") || r.contains(" fi ") -> "fi"
        r.contains("singapore") || r.contains("سنگاپور") || r.contains(" sg ") -> "sg"
        else -> ""
    }
}

    fun setSelectServer(guid: String, position: Int = -1) {
        val lastSelected = MmkvManager.getSelectServer()
        if (guid != lastSelected) {
            MmkvManager.setSelectServer(guid)
            
            if (!lastSelected.isNullOrEmpty()) {
                val oldPos = mActivity.mainViewModel.getPosition(lastSelected)
                if (oldPos != -1) notifyItemChanged(oldPos)
            }

            val newPos = if (position != -1) position else mActivity.mainViewModel.getPosition(guid)
            if (newPos != -1) notifyItemChanged(newPos)

            if (isRunning) {
                switchJob?.cancel()
                switchJob = mActivity.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        V2RayServiceManager.stopVService(mActivity)
                        delay(500)
                        V2RayServiceManager.startVService(mActivity)
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "Restart Error", e)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
         return MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemViewType(position: Int): Int = VIEW_TYPE_ITEM
    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) : BaseViewHolder(itemMainBinding.root)
}
