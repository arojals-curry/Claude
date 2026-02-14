package com.minimalist.launcher.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimalist.launcher.R
import com.minimalist.launcher.adapter.FavoritesAdapter
import com.minimalist.launcher.databinding.ActivityHomeBinding
import com.minimalist.launcher.model.AppInfo
import com.minimalist.launcher.tracking.ActivityTracker
import com.minimalist.launcher.util.AppUtils
import com.minimalist.launcher.util.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefs: PrefsManager
    private lateinit var tracker: ActivityTracker
    private lateinit var favoritesAdapter: FavoritesAdapter
    private lateinit var gestureDetector: GestureDetectorCompat

    private var clockTimer: Timer? = null
    private var allApps: List<AppInfo> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        tracker = ActivityTracker.getInstance(this)
        applyTheme()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFavorites()
        setupGestures()
        registerPackageReceiver()
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        startClock()
        updateBattery()
    }

    override fun onPause() {
        super.onPause()
        stopClock()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {}
    }

    private fun applyTheme() {
        val bgColor = when (prefs.theme) {
            PrefsManager.THEME_LIGHT -> getColor(R.color.light_bg)
            PrefsManager.THEME_AMOLED -> getColor(R.color.amoled_bg)
            else -> getColor(R.color.dark_bg)
        }
        window.decorView.setBackgroundColor(bgColor)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        if (prefs.theme == PrefsManager.THEME_LIGHT) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun applyTextColors() {
        val primaryColor: Int
        val secondaryColor: Int

        when (prefs.theme) {
            PrefsManager.THEME_LIGHT -> {
                primaryColor = getColor(R.color.text_primary_light)
                secondaryColor = getColor(R.color.text_secondary_light)
            }
            else -> {
                primaryColor = getColor(R.color.text_primary_dark)
                secondaryColor = getColor(R.color.text_secondary_dark)
            }
        }

        binding.clockText.setTextColor(primaryColor)
        binding.dateText.setTextColor(secondaryColor)
        binding.batteryText.setTextColor(secondaryColor)
        binding.swipeHint.setTextColor(secondaryColor)
    }

    private fun setupFavorites() {
        favoritesAdapter = FavoritesAdapter(
            onClick = { app ->
                tracker.logAppLaunch(app, ActivityTracker.LaunchSource.FAVORITES)
                AppUtils.launchApp(this, app)
            },
            onLongClick = { app -> showFavoriteContextMenu(app) }
        )
        binding.favoritesRecycler.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = favoritesAdapter
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e1.y - e2.y
                if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY) {
                    openAppDrawer()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                openSettings()
            }
        })

        binding.homeRoot.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun startClock() {
        stopClock()
        updateClock()
        clockTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread { updateClock() }
                }
            }, 1000, 1000)
        }
    }

    private fun stopClock() {
        clockTimer?.cancel()
        clockTimer = null
    }

    private fun updateClock() {
        val now = Date()

        val clockFormat = if (prefs.use24HourClock) "HH:mm" else "h:mm"
        binding.clockText.text = SimpleDateFormat(clockFormat, Locale.getDefault()).format(now)

        if (prefs.showDate) {
            binding.dateText.visibility = View.VISIBLE
            binding.dateText.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        } else {
            binding.dateText.visibility = View.GONE
        }

        applyTextColors()
    }

    private fun updateBattery() {
        if (prefs.showBattery) {
            binding.batteryText.visibility = View.VISIBLE
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            binding.batteryText.text = "$level% battery"
        } else {
            binding.batteryText.visibility = View.GONE
        }
    }

    private fun refreshApps() {
        allApps = AppUtils.getInstalledApps(this)
        loadFavorites()
    }

    private fun loadFavorites() {
        val favoriteKeys = prefs.getFavorites()
        val favorites = favoriteKeys.mapNotNull { key ->
            allApps.find { it.key == key }
        }
        favoritesAdapter.submitList(favorites)
    }

    private fun showFavoriteContextMenu(app: AppInfo) {
        val popup = PopupMenu(this, binding.favoritesRecycler)
        popup.menu.add(0, 1, 0, R.string.open)
        popup.menu.add(0, 2, 1, R.string.rename)
        popup.menu.add(0, 3, 2, R.string.remove_favorite)
        popup.menu.add(0, 4, 3, R.string.app_info)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    tracker.logAppLaunch(app, ActivityTracker.LaunchSource.CONTEXT_MENU)
                    AppUtils.launchApp(this, app)
                    true
                }
                2 -> {
                    showRenameDialog(app)
                    true
                }
                3 -> {
                    prefs.removeFavorite(app.key)
                    refreshApps()
                    true
                }
                4 -> {
                    openAppInfo(app.packageName)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(app: AppInfo) {
        val editText = android.widget.EditText(this).apply {
            setText(app.displayName)
            hint = getString(R.string.enter_custom_name)
            setPadding(48, 32, 48, 32)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.rename_app)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != app.label) {
                    prefs.setCustomLabel(app.key, newName)
                } else {
                    prefs.setCustomLabel(app.key, null)
                }
                refreshApps()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openAppInfo(packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun openAppDrawer() {
        startActivity(Intent(this, AppDrawerActivity::class.java))
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    override fun onBackPressed() {
        // Do nothing - we are the home screen
    }

    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY = 100
    }
}
