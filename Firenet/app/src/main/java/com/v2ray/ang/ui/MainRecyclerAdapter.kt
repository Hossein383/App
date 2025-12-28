package com.v2ray.ang.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.withContext

class MainRecyclerAdapter(val activity: MainActivity) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 1
        // جلوگیری از کلیک‌های سریع (نیم ثانیه)
        private const val CLICK_DELAY = 500L
    }

    private var mActivity: MainActivity = activity
    var isRunning = false
    
    // ذخیره زمان آخرین کلیک برای جلوگیری از باگ سوییچ
    private var lastClickTime = 0L
    private var switchJob: Job? = null

    // کش کردن رنگ‌ها برای پرفورمنس بهتر
    private val colorAccent by lazy { ContextCompat.getColor(mActivity, R.color.colorAccent) }
    private val colorWhite by lazy { Color.WHITE }
    private val colorGray by lazy { Color.LTGRAY }
    private val colorTransparentWhite by lazy { Color.parseColor("#33FFFFFF") }

    override fun getItemCount() = mActivity.mainViewModel.serversCache.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val serverConfig = mActivity.mainViewModel.serversCache.getOrNull(position) ?: return
            val guid = serverConfig.guid
            val profile = serverConfig.profile
            
            holder.itemMainBinding.tvName.text = profile.remarks

            val isSelected = (guid == MmkvManager.getSelectServer())

            // اعمال تغییرات ظاهری
            updateUIState(holder, isSelected)

            holder.itemView.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                // اگر از آخرین کلیک کمتر از 500 میلی ثانیه گذشته، کلیک را نادیده بگیر
                if (currentTime - lastClickTime > CLICK_DELAY) {
                    lastClickTime = currentTime
                    setSelectServer(guid, position)
                }
            }
        }
    }

    /**
     * جدا کردن لاجیک ظاهر برای خوانایی بهتر و پرفورمنس
     */
    private fun updateUIState(holder: MainViewHolder, isSelected: Boolean) {
        val binding = holder.itemMainBinding

        if (isSelected) {
            // حالت انتخاب شده (Active)
            binding.layoutIndicator.setBackgroundResource(R.drawable.bg_glass_input)
            binding.layoutIndicator.backgroundTintList = ColorStateList.valueOf(colorAccent)
            
            binding.ivStatusIcon.setImageResource(R.drawable.ic_server_active)
            binding.ivStatusIcon.setColorFilter(colorWhite)

            binding.tvName.maxLines = 2
            binding.tvName.setTextColor(colorAccent)
            binding.tvName.ellipsize = null
            
            // اضافه کردن کمی سایه یا تغییر ارتفاع برای زیبایی (اختیاری)
            binding.root.elevation = 4f
        } else {
            // حالت عادی (Idle)
            binding.layoutIndicator.setBackgroundResource(R.drawable.bg_glass_input)
            binding.layoutIndicator.backgroundTintList = ColorStateList.valueOf(colorTransparentWhite)

            binding.ivStatusIcon.setImageResource(R.drawable.ic_server_idle)
            binding.ivStatusIcon.setColorFilter(colorGray)

            binding.tvName.maxLines = 1
            binding.tvName.setTextColor(colorWhite)
            binding.tvName.ellipsize = TextUtils.TruncateAt.END
            
            binding.root.elevation = 0f
        }
    }

    fun setSelectServer(guid: String, newPosition: Int) {
        val currentSelectedGuid = MmkvManager.getSelectServer()
        
        // اگر روی همان سرور فعلی کلیک شده، کاری نکن
        if (guid == currentSelectedGuid) return

        // 1. ذخیره سرور جدید
        MmkvManager.setSelectServer(guid)

        // 2. آپدیت کردن آیتم قبلی (برای اینکه از حالت انتخاب در بیاید)
        if (!TextUtils.isEmpty(currentSelectedGuid)) {
            val oldPos = mActivity.mainViewModel.getPosition(currentSelectedGuid.orEmpty())
            if (oldPos != -1) {
                notifyItemChanged(oldPos)
            }
        }

        // 3. آپدیت آیتم جدید (برای اینکه رنگی شود)
        // استفاده از پوزیشن پاس داده شده مطمئن‌تر از getPosition است
        notifyItemChanged(newPosition) 
        
        // 4. هندل کردن ریستارت سرویس (بخش حیاتی)
        if (isRunning) {
            handleServiceRestart()
        }
    }

    private fun handleServiceRestart() {
        // کنسل کردن جاب قبلی اگر وجود دارد (برای جلوگیری از تداخل)
        switchJob?.cancel()
        
        switchJob = mActivity.lifecycleScope.launch(Dispatchers.Main) {
            try {
                // استاپ کردن سرویس
                V2RayServiceManager.stopVService(mActivity)
                
                // انتقال وقفه به IO برای جلوگیری از فریز شدن UI
                withContext(Dispatchers.IO) {
                    delay(500) 
                }
                
                // استارت مجدد
                V2RayServiceManager.startVService(mActivity)
                
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Error in restarting V2Ray service: ${e.message}", e)
                // اینجا می‌توانید یک Toast نمایش دهید که خطا رخ داده
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
         return MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemViewType(position: Int): Int {
        return VIEW_TYPE_ITEM
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root)
}
