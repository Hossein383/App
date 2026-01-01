package com.v2ray.ang.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.v2ray.ang.R
import com.v2ray.ang.data.auth.AuthRepository
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.view.GravityCompat

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    // استفاده از lateinit برای جلوگیری از کرش در زمان مقداردهی lazy
    private lateinit var binding: ActivityMainBinding
    
    // متغیر ViewModel باید برای Adapter قابل مشاهده (public) باشد
    val mainViewModel: MainViewModel by viewModels()
    
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val repo by lazy { AuthRepository(this) }

    private var ring1Animator: ObjectAnimator? = null
    private var ring2Animator: ObjectAnimator? = null
    private var isConnectingAnimationRunning = false

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startV2Ray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ۱. تنظیم زبان قبل از Super
        val locale = Locale("fa")
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        
        // ۲. مقداردهی Binding به روش ایمن
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ۳. اجرای تنظیمات UI
        setupHorizontalRecyclerView()
        setupViewModel()
        
        // ۴. دکمه اتصال
        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(this)
            } else {
                val intent = VpnService.prepare(this)
                if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
            }
        }

        binding.btnMenu.setOnClickListener { 
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private fun setupHorizontalRecyclerView() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerView.layoutManager = layoutManager
        
        val snapHelper = LinearSnapHelper()
        binding.recyclerView.onFlingListener = null
        snapHelper.attachToRecyclerView(binding.recyclerView)
        binding.recyclerView.adapter = adapter

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(layoutManager)
                    val pos = centerView?.let { layoutManager.getPosition(it) }
                    if (pos != null && pos != -1) {
                        mainViewModel.serversCache.getOrNull(pos)?.guid?.let { 
                            adapter.setSelectServer(it) 
                        }
                    }
                }
            }
        })
    }

    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunningValue ->
            adapter.isRunning = isRunningValue
            if (isRunningValue) {
                startConnectedAnimation()
                binding.fab.setImageResource(R.drawable.disconnect_button)
                binding.tvConnectionStatus.setText(R.string.connected)
            } else {
                startDisconnectAnimation()
                binding.fab.setImageResource(R.drawable.connect_button)
                binding.tvConnectionStatus.setText(R.string.not_connected)
            }
        }
        mainViewModel.reloadServerList()
    }

    fun restartV2Ray() {
        V2RayServiceManager.stopVService(this)
        lifecycleScope.launch { 
            delay(500)
            startV2Ray() 
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) return
        startConnectingAnimation()
        V2RayServiceManager.startVService(this)
    }

    private fun startConnectingAnimation() {
        if (isConnectingAnimationRunning) return
        isConnectingAnimationRunning = true
        
        // نمایش رینگ‌ها (مطمئن شوید در XML وجود دارند)
        binding.ring1.visibility = View.VISIBLE
        binding.ring2.visibility = View.VISIBLE

        ring1Animator = ObjectAnimator.ofFloat(binding.ring1, "rotation", 0f, 360f).apply {
            duration = 2000; repeatCount = -1; interpolator = LinearInterpolator(); start()
        }
        ring2Animator = ObjectAnimator.ofFloat(binding.ring2, "rotation", 0f, -360f).apply {
            duration = 1500; repeatCount = -1; interpolator = LinearInterpolator(); start()
        }
    }

    private fun startConnectedAnimation() {
        isConnectingAnimationRunning = false
        ring1Animator?.cancel()
        ring2Animator?.cancel()
        binding.ring1.visibility = View.GONE
        binding.ring2.visibility = View.GONE
        binding.bgActive.visibility = View.VISIBLE
    }

    private fun startDisconnectAnimation() {
        isConnectingAnimationRunning = false
        ring1Animator?.cancel()
        ring2Animator?.cancel()
        binding.ring1.visibility = View.GONE
        binding.ring2.visibility = View.GONE
        binding.bgActive.visibility = View.INVISIBLE
    }

    fun scrollToPositionCentered(position: Int) {
        val scroller = object : LinearSmoothScroller(this) {
            override fun calculateDtToFit(vs: Int, ve: Int, bs: Int, be: Int, sp: Int): Int =
                (bs + (be - bs) / 2) - (vs + (ve - vs) / 2)
        }
        scroller.targetPosition = position
        binding.recyclerView.layoutManager?.startSmoothScroll(scroller)
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
