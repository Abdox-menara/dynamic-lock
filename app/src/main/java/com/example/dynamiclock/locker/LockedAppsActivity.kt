package com.example.dynamiclock.locker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivityLockedAppsBinding
import com.example.dynamiclock.databinding.ItemAppBinding

class LockedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockedAppsBinding
    private val adapter = AppsAdapter()

    private data class AppRow(val pkg: String, val label: String, val icon: Drawable)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshButtons() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        binding.btnUsage.setOnClickListener {
            safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            safeStart(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch("android.permission.POST_NOTIFICATIONS")
            else openAppNotificationSettings()
        }
        binding.btnBattery.setOnClickListener { requestIgnoreBattery() }
        binding.btnToggle.setOnClickListener { toggleProtection() }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        refreshButtons()
    }

    private fun toggleProtection() {
        if (LockManager.isEnabled(this)) {
            AppLockService.stop(this)
        } else {
            if (!hasUsageAccess()) { toast("Grant Usage Access first"); return }
            if (!Settings.canDrawOverlays(this)) { toast("Grant Display-over-apps first"); return }
            AppLockService.start(this)
        }
        refreshButtons()
    }

    private fun refreshButtons() {
        binding.btnUsage.text = mark(getString(R.string.grant_usage), hasUsageAccess())
        binding.btnOverlay.text = mark(getString(R.string.grant_overlay), Settings.canDrawOverlays(this))
        binding.btnNotif.text = mark(getString(R.string.grant_notifications), hasNotifications())
        binding.btnBattery.text = mark(getString(R.string.grant_battery), ignoringBattery())
        binding.btnToggle.text = getString(
            if (LockManager.isEnabled(this)) R.string.stop_service
            else R.string.start_service
        )
    }

    private fun mark(text: String, granted: Boolean) = if (granted) "$text  ✓" else text

    private fun ignoringBattery(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBattery() {
        if (ignoringBattery()) { toast("Already exempt from battery optimization"); return }
        safeStart(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$packageName")))
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED
        else true

    private fun openAppNotificationSettings() {
        safeStart(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { toast("Couldn't open that settings screen on this device") }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val rows = pm.queryIntentActivities(main, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != packageName }
                .mapNotNull { pkg ->
                    runCatching {
                        val ai: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                        AppRow(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
                    }.getOrNull()
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                binding.tvLoading.visibility = if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                adapter.submit(rows)
            }
        }.start()
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppsAdapter.VH>() {
        private val items = mutableListOf<AppRow>()

        fun submit(list: List<AppRow>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

        inner class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            holder.b.ivIcon.setImageDrawable(row.icon)
            holder.b.tvLabel.text = row.label
            holder.b.cb.setOnCheckedChangeListener(null)
            holder.b.cb.isChecked = LockManager.isLocked(this@LockedAppsActivity, row.pkg)
            holder.b.cb.setOnCheckedChangeListener { _, checked ->
                LockManager.setLocked(this@LockedAppsActivity, row.pkg, checked)
            }
            holder.b.root.setOnClickListener { holder.b.cb.toggle() }

            // v5: Schedule button - long press to configure schedule
            holder.b.root.setOnLongClickListener {
                val intent = Intent(this@LockedAppsActivity, ScheduleConfigActivity::class.java)
                intent.putExtra("package", row.pkg)
                startActivity(intent)
                true
            }
        }
    }
}
