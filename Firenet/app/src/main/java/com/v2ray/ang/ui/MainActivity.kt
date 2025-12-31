package com.v2ray.ang.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.data.auth.AuthRepository
import com.v2ray.ang.data.auth.TokenStore
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MigrateManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.net.StatusResponse
import com.v2ray.ang.ui.main.StatusFormatter
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.hypot

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mainViewModel: MainViewModel by viewModels()
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val repo by lazy { AuthRepository(this) }

    private var ring1Animator: ObjectAnimator? = null
    private var ring2Animator: ObjectAnimator? = null
    private var ring3Animator: ObjectAnimator? = null
    private var isConnectingAnimationRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // تنظیم زبان
        val locale = Locale("fa")
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupHorizontalRecyclerView()
        setupViewModel()
        
        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(this)
            } else {
                val intent = VpnService.prepare(this)
                if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
            }
        }

        binding.btnMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
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
                    
                    // اصلاح خطا: استفاده از serversCache به جای servers و دسترسی مستقیم به adapter
                    if (pos != null && pos != -1) {
                        val serverData = mainViewModel.serversCache.getOrNull(pos)
                        val serverGuid = serverData?.guid ?: ""
                        if (serverGuid.isNotEmpty()) {
                            adapter.setSelectServer(serverGuid)
                        }
                    }
                }
            }
        })
    }
    private fun setupViewModel() {
        mainViewModel.isRunning.observe(this) { isRunningValue -> // تغییر نام برای جلوگیری از تداخل
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

    fun restartV2Ray() {
        V2RayServiceManager.stopVService(this)
        lifecycleScope.launch { delay(500); startV2Ray() }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) return
        startConnectingAnimation()
        V2RayServiceManager.startVService(this)
    }

    private fun startConnectingAnimation() {
        if (isConnectingAnimationRunning) return
        isConnectingAnimationRunning = true
        ring1Animator = ObjectAnimator.ofFloat(binding.ring1, "rotation", 0f, 360f).apply { duration = 2000; repeatCount = -1; interpolator = LinearInterpolator(); start() }
        ring2Animator = ObjectAnimator.ofFloat(binding.ring2, "rotation", 0f, -360f).apply { duration = 1500; repeatCount = -1; interpolator = LinearInterpolator(); start() }
    }

    private fun startConnectedAnimation() {
        isConnectingAnimationRunning = false
        ring1Animator?.cancel(); ring2Animator?.cancel()
        binding.bgActive.visibility = View.VISIBLE
        // افکت Reveal انیمیشن
    }

    private fun startDisconnectAnimation() {
        binding.bgActive.visibility = View.INVISIBLE
    }

    fun scrollToPositionCentered(position: Int) {
        val scroller = object : LinearSmoothScroller(this) {
            override fun calculateDtToFit(vs: Int, ve: Int, bs: Int, be: Int, sp: Int): Int = (bs + (be - bs) / 2) - (vs + (ve - vs) / 2)
        }
        scroller.targetPosition = position
        binding.recyclerView.layoutManager?.startSmoothScroll(scroller)
    }

    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean = true
}
