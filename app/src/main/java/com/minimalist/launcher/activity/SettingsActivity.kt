package com.minimalist.launcher.activity

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.minimalist.launcher.BuildConfig
import com.minimalist.launcher.R
import com.minimalist.launcher.databinding.ActivitySettingsBinding
import com.minimalist.launcher.tracking.ScreenTimeCollector
import com.minimalist.launcher.tracking.SyncWorker
import com.minimalist.launcher.util.PrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        applyTheme()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateUsagePermissionStatus()
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

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }

        // Theme
        updateThemeLabel()
        binding.themeOption.setOnClickListener { showThemeDialog() }

        // Switches
        binding.switch24h.isChecked = prefs.use24HourClock
        binding.switch24h.setOnCheckedChangeListener { _, isChecked ->
            prefs.use24HourClock = isChecked
        }

        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowDate.setOnCheckedChangeListener { _, isChecked ->
            prefs.showDate = isChecked
        }

        binding.switchShowBattery.isChecked = prefs.showBattery
        binding.switchShowBattery.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBattery = isChecked
        }

        binding.switchShowAvatar.isChecked = prefs.showAvatar
        binding.switchShowAvatar.setOnCheckedChangeListener { _, isChecked ->
            prefs.showAvatar = isChecked
        }

        // Max favorites
        updateMaxFavoritesLabel()
        binding.maxFavoritesOption.setOnClickListener { showMaxFavoritesDialog() }

        // Tracking: usage access permission
        updateUsagePermissionStatus()
        binding.usagePermissionOption.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Tracking: force sync now
        binding.forceSyncOption.setOnClickListener {
            WorkManager.getInstance(this).enqueueUniqueWork(
                "manual_sync",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().build()
            )
            Toast.makeText(this, R.string.sync_started, Toast.LENGTH_SHORT).show()
        }

        // Version
        binding.versionText.text = "${getString(R.string.version)} ${BuildConfig.VERSION_NAME}"
    }

    private fun updateUsagePermissionStatus() {
        val collector = ScreenTimeCollector(this)
        val hasPermission = collector.hasUsagePermission()
        binding.usagePermissionValue.text = if (hasPermission)
            getString(R.string.permission_granted)
        else
            getString(R.string.permission_not_granted)
    }

    private fun updateThemeLabel() {
        binding.themeValue.text = when (prefs.theme) {
            PrefsManager.THEME_LIGHT -> getString(R.string.theme_light)
            PrefsManager.THEME_AMOLED -> getString(R.string.theme_amoled)
            else -> getString(R.string.theme_dark)
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_dark),
            getString(R.string.theme_light),
            getString(R.string.theme_amoled)
        )
        val themeValues = arrayOf(PrefsManager.THEME_DARK, PrefsManager.THEME_LIGHT, PrefsManager.THEME_AMOLED)
        val currentIndex = themeValues.indexOf(prefs.theme)

        AlertDialog.Builder(this)
            .setTitle(R.string.theme)
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                prefs.theme = themeValues[which]
                updateThemeLabel()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateMaxFavoritesLabel() {
        binding.maxFavoritesValue.text = prefs.maxFavorites.toString()
    }

    private fun showMaxFavoritesDialog() {
        val options = arrayOf("4", "6", "8", "10", "12")
        val currentIndex = options.indexOf(prefs.maxFavorites.toString())

        AlertDialog.Builder(this)
            .setTitle(R.string.max_favorites)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.maxFavorites = options[which].toInt()
                updateMaxFavoritesLabel()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
