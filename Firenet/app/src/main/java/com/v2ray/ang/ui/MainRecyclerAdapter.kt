package com.v2ray.ang.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.handler.MmkvManager

class MainRecyclerAdapter(val mActivity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder>() {

    var isRunning = false

    override fun getItemCount() = mActivity.mainViewModel.serversCache.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val guid = mActivity.mainViewModel.serversCache[position].guid
        val isSelected = guid == MmkvManager.getSelectServer()
        updateUI(holder, isSelected)

        holder.itemView.setOnClickListener {
            setSelectServer(guid)
        }
    }

    fun setSelectServer(guid: String) {
        val oldPos = mActivity.mainViewModel.getPosition(MmkvManager.getSelectServer())
        val newPos = mActivity.mainViewModel.getPosition(guid)
        MmkvManager.setSelectServer(guid)
        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (newPos >= 0) notifyItemChanged(newPos)
        mActivity.scrollToPositionCentered(newPos)
    }

    private fun updateUI(holder: MainViewHolder, isSelected: Boolean) {
        val binding = holder.itemMainBinding
        val serverData = mActivity.mainViewModel.serversCache.getOrNull(holder.layoutPosition) ?: return
        val remarks = serverData.profile.remarks

        binding.layoutIndicator.clearAnimation()

        // منطق تشخیص و نمایش پرچم دستی
        val countryCode = getCountryCode(remarks)
        if (countryCode.isNotEmpty()) {
            val resName = "flag_$countryCode"
            val resId = mActivity.resources.getIdentifier(resName, "drawable", mActivity.packageName)
            if (resId != 0) {
                binding.ivStatusIcon.setImageResource(resId)
                binding.ivStatusIcon.colorFilter = null
            } else {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_server_idle)
            }
        } else {
            binding.ivStatusIcon.setImageResource(R.drawable.ic_server_idle)
            binding.ivStatusIcon.setColorFilter(if (isSelected) Color.WHITE else Color.LTGRAY)
        }

        if (isSelected) {
            binding.layoutIndicator.setBackgroundResource(R.drawable.bg_server_active)
            binding.layoutIndicator.backgroundTintList = null
            binding.tvName.setTextColor(Color.parseColor("#00E5FF"))
            binding.tvName.maxLines = 2

            binding.layoutIndicator.animate().scaleX(1.15f).scaleY(1.15f).setDuration(300)
                .setInterpolator(OvershootInterpolator()).start()

            val pulseAnim = AlphaAnimation(0.7f, 1.0f).apply {
                duration = 800
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            binding.layoutIndicator.startAnimation(pulseAnim)
        } else {
            binding.layoutIndicator.setBackgroundResource(R.drawable.bg_glass_input)
            binding.layoutIndicator.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            binding.tvName.setTextColor(Color.WHITE)
            binding.tvName.maxLines = 1
            binding.layoutIndicator.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    private fun getCountryCode(remarks: String): String {
        val r = remarks.lowercase()
        return when {
            r.contains("germany") || r.contains("آلمان") || r.contains(" de ") || r.startsWith("de-") -> "de"
            r.contains("usa") || r.contains("united states") || r.contains("آمریکا") || r.contains(" us ") -> "us"
            r.contains("turkey") || r.contains("ترکیه") || r.contains(" tr ") -> "tr"
            r.contains("united kingdom") || r.contains("انگلیس") || r.contains(" uk ") || r.contains(" gb ") -> "gb"
            r.contains("france") || r.contains("فرانسه") || r.contains(" fr ") -> "fr"
            r.contains("netherlands") || r.contains("هلند") || r.contains(" nl ") -> "nl"
            else -> ""
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) : RecyclerView.ViewHolder(itemMainBinding.root)
}
