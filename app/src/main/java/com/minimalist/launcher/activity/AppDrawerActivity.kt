package com.minimalist.launcher.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.minimalist.launcher.R
import com.minimalist.launcher.adapter.AppDrawerAdapter
import com.minimalist.launcher.databinding.ActivityAppDrawerBinding
import com.minimalist.launcher.model.AppInfo
import com.minimalist.launcher.tracking.ActivityTracker
import com.minimalist.launcher.util.AppUtils
import com.minimalist.launcher.util.PrefsManager

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var prefs: PrefsManager
    private lateinit var tracker: ActivityTracker
    private lateinit var adapter: AppDrawerAdapter
    private var allApps: List<AppInfo> = emptyList()
    private var currentSearchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        tracker = ActivityTracker.getInstance(this)
        applyTheme()

        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppList()
        setupSearch()
        loadApps()
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

    private fun setupAppList() {
        adapter = AppDrawerAdapter(
            onClick = { app ->
                val source = if (currentSearchQuery.isNotEmpty())
                    ActivityTracker.LaunchSource.SEARCH
                else
                    ActivityTracker.LaunchSource.DRAWER
                tracker.logAppLaunch(app, source)
                AppUtils.launchApp(this, app)
                finish()
            },
            onLongClick = { app -> showAppContextMenu(app) }
        )
        binding.appsRecycler.apply {
            layoutManager = LinearLayoutManager(this@AppDrawerActivity)
            adapter = this@AppDrawerActivity.adapter
        }
    }

    private fun setupSearch() {
        val textColor: Int
        val hintColor: Int

        when (prefs.theme) {
            PrefsManager.THEME_LIGHT -> {
                textColor = getColor(R.color.text_primary_light)
                hintColor = getColor(R.color.text_secondary_light)
                binding.searchEditText.setBackgroundResource(R.drawable.search_bg_light)
            }
            else -> {
                textColor = getColor(R.color.text_primary_dark)
                hintColor = getColor(R.color.text_secondary_dark)
            }
        }
        binding.searchEditText.setTextColor(textColor)
        binding.searchEditText.setHintTextColor(hintColor)

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                filterApps(currentSearchQuery)
            }
        })
    }

    private fun loadApps() {
        allApps = AppUtils.getInstalledApps(this)
        adapter.submitList(allApps)
    }

    private fun filterApps(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(allApps)
        } else {
            val filtered = allApps.filter {
                it.displayName.contains(query, ignoreCase = true)
            }
            adapter.submitList(filtered)
        }
    }

    private fun showAppContextMenu(app: AppInfo) {
        val popup = PopupMenu(this, binding.appsRecycler)
        popup.menu.add(0, 1, 0, R.string.open)

        if (prefs.isFavorite(app.key)) {
            popup.menu.add(0, 2, 1, R.string.remove_favorite)
        } else {
            popup.menu.add(0, 3, 1, R.string.add_favorite)
        }

        popup.menu.add(0, 4, 2, R.string.rename)
        popup.menu.add(0, 5, 3, R.string.app_info)
        popup.menu.add(0, 6, 4, R.string.uninstall)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    tracker.logAppLaunch(app, ActivityTracker.LaunchSource.CONTEXT_MENU)
                    AppUtils.launchApp(this, app)
                    finish()
                    true
                }
                2 -> {
                    prefs.removeFavorite(app.key)
                    true
                }
                3 -> {
                    val added = prefs.addFavorite(app.key)
                    if (!added) {
                        Toast.makeText(this, R.string.favorites_full, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                4 -> {
                    showRenameDialog(app)
                    true
                }
                5 -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                    true
                }
                6 -> {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
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
                loadApps()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }
}
