package com.v2ray.ang.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.handler.MmkvManager

class MainRecyclerAdapter(val mActivity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.MainViewHolder>() {

    // این متغیر برای هماهنگی با MainActivity الزامی است
    var isRunning: Boolean = false

    override fun getItemCount() = mActivity.mainViewModel.serversCache.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerMainBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val serverData = mActivity.mainViewModel.serversCache.getOrNull(position)
        val serverGuid = serverData?.guid ?: ""
        val isSelected = serverGuid == (MmkvManager.getSelectServer() ?: "")

        val binding = holder.itemMainBinding
        binding.tvName.text = serverData?.profile?.remarks ?: "Unknown"

        // مدیریت ظاهر کارت
        if (isSelected) {
            binding.cardMaster.setCardBackgroundColor(Color.parseColor("#3300D2FF"))
            binding.cardMaster.strokeColor = Color.parseColor("#00D2FF")
            binding.cardMaster.strokeWidth = 4
            binding.tvName.setTextColor(Color.parseColor("#00D2FF"))
            
            binding.cardMaster.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(OvershootInterpolator()).start()
            
            val pulse = AlphaAnimation(0.6f, 1.0f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            binding.cardMaster.startAnimation(pulse)
        } else {
            binding.cardMaster.setCardBackgroundColor(Color.parseColor("#1AFFFFFF"))
            binding.cardMaster.strokeColor = Color.parseColor("#1AFFFFFF")
            binding.cardMaster.strokeWidth = 2
            binding.tvName.setTextColor(Color.WHITE)
            binding.cardMaster.clearAnimation()
            binding.cardMaster.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }

        holder.itemView.setOnClickListener {
            if (serverGuid.isNotEmpty() && serverGuid != MmkvManager.getSelectServer()) {
                setSelectServer(serverGuid)
            }
        }
    }

    // اضافه کردن متد setSelectServer برای فراخوانی از MainActivity (اسکرول)
    fun setSelectServer(guid: String) {
        val currentSelect = MmkvManager.getSelectServer() ?: ""
        if (guid == currentSelect) return

        val oldPos = mActivity.mainViewModel.getPosition(currentSelect)
        val newPos = mActivity.mainViewModel.getPosition(guid)
        
        MmkvManager.setSelectServer(guid)
        
        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (newPos >= 0) notifyItemChanged(newPos)
        
        if (newPos >= 0) {
            mActivity.scrollToPositionCentered(newPos)
        }

        // اگر VPN روشن است، با تغییر سرور ری‌استارت شود
        if (mActivity.mainViewModel.isRunning.value == true) {
            mActivity.restartV2Ray()
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) : RecyclerView.ViewHolder(itemMainBinding.root)
}
