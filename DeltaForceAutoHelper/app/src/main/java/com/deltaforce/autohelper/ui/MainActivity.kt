package com.deltaforce.autohelper.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.deltaforce.autohelper.R
import com.deltaforce.autohelper.databinding.ActivityMainBinding
import com.deltaforce.autohelper.model.ConfigRepository
import com.deltaforce.autohelper.model.ScheduleMode
import com.deltaforce.autohelper.model.StationId
import com.deltaforce.autohelper.model.StationTask
import com.deltaforce.autohelper.scheduler.ScheduleManager
import com.deltaforce.autohelper.service.AutomationForegroundService
import com.deltaforce.autohelper.service.DeltaAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        bindUiEvents()
        loadConfigIntoUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    private fun bindUiEvents() {
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnBatteryOptimize.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        binding.switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            val current = readConfigFromUi().copy(scheduleEnabled = isChecked)
            ConfigRepository.save(this, current)
            if (isChecked) {
                ScheduleManager.scheduleNext(this)
                Toast.makeText(this, "已开启定时任务", Toast.LENGTH_SHORT).show()
            } else {
                ScheduleManager.cancel(this)
                Toast.makeText(this, "已关闭定时任务", Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }

        binding.rgScheduleMode.setOnCheckedChangeListener { _, _ ->
            val current = readConfigFromUi()
            ConfigRepository.save(this, current)
            if (current.scheduleEnabled) {
                ScheduleManager.scheduleNext(this)
            }
            refreshStatus()
        }

        binding.btnEditTime.setOnClickListener {
            val config = ConfigRepository.load(this)
            if (config.scheduleMode == ScheduleMode.DAILY_FIXED) {
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val updated = readConfigFromUi().copy(dailyHour = hour, dailyMinute = minute)
                        ConfigRepository.save(this, updated)
                        if (updated.scheduleEnabled) ScheduleManager.scheduleNext(this)
                        loadConfigIntoUi()
                        refreshStatus()
                    },
                    config.dailyHour,
                    config.dailyMinute,
                    true
                ).show()
            } else {
                showIntervalDialog(config.intervalMinutes)
            }
        }

        binding.btnSaveConfig.setOnClickListener {
            val updated = readConfigFromUi()
            ConfigRepository.save(this, updated)
            if (updated.scheduleEnabled) {
                ScheduleManager.scheduleNext(this)
            }
            refreshStatus()
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        }

        binding.btnCalibration.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        binding.btnRunNow.setOnClickListener {
            val updated = readConfigFromUi()
            ConfigRepository.save(this, updated)
            if (DeltaAccessibilityService.instance == null) {
                Toast.makeText(this, "请先开启「无障碍服务」", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            AutomationForegroundService.start(this, fromSchedule = false)
            Toast.makeText(this, "已开始执行自动化流程", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopNow.setOnClickListener {
            AutomationForegroundService.stop(this)
            refreshStatus()
        }
    }

    private fun showIntervalDialog(currentMinutes: Int) {
        val options = arrayOf("每 7 小时 30 分钟（450分钟）", "每 8 小时（480分钟）", "每 6 小时（360分钟）", "每 12 小时（720分钟）")
        val values = intArrayOf(450, 480, 360, 720)
        val checked = values.indexOf(currentMinutes).takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle("选择循环间隔")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val updated = readConfigFromUi().copy(intervalMinutes = values[which])
                ConfigRepository.save(this, updated)
                if (updated.scheduleEnabled) ScheduleManager.scheduleNext(this)
                loadConfigIntoUi()
                refreshStatus()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadConfigIntoUi() {
        val config = ConfigRepository.load(this)
        binding.switchSchedule.isChecked = config.scheduleEnabled
        if (config.scheduleMode == ScheduleMode.INTERVAL) {
            binding.rbInterval.isChecked = true
        } else {
            binding.rbDaily.isChecked = true
        }

        val hours = config.intervalMinutes / 60
        val mins = config.intervalMinutes % 60
        binding.rbInterval.text = "按间隔循环执行（每 ${hours} 小时 ${mins} 分钟一次）"
        binding.rbDaily.text = String.format(
            Locale.getDefault(),
            "每日固定时间执行（每天 %02d:%02d）",
            config.dailyHour,
            config.dailyMinute
        )

        val map = config.stations.associateBy { it.id }
        binding.cbTechCenter.isChecked = map[StationId.TECH_CENTER]?.enabled == true
        binding.etTechSlot.setText((map[StationId.TECH_CENTER]?.itemSlotIndex ?: 1).toString())

        binding.cbWorkbench.isChecked = map[StationId.WORKBENCH]?.enabled != false
        binding.etWorkbenchSlot.setText((map[StationId.WORKBENCH]?.itemSlotIndex ?: 2).toString())

        binding.cbPharmacy.isChecked = map[StationId.PHARMACY]?.enabled != false
        binding.etPharmacySlot.setText((map[StationId.PHARMACY]?.itemSlotIndex ?: 1).toString())

        binding.cbArmory.isChecked = map[StationId.ARMORY]?.enabled != false
        binding.etArmorySlot.setText((map[StationId.ARMORY]?.itemSlotIndex ?: 1).toString())
    }

    private fun readConfigFromUi() = ConfigRepository.load(this).copy(
        scheduleEnabled = binding.switchSchedule.isChecked,
        scheduleMode = if (binding.rbInterval.isChecked) ScheduleMode.INTERVAL else ScheduleMode.DAILY_FIXED,
        stations = listOf(
            StationTask(
                StationId.TECH_CENTER,
                binding.cbTechCenter.isChecked,
                binding.etTechSlot.text.toString().toIntOrNull()?.coerceIn(1, 5) ?: 1
            ),
            StationTask(
                StationId.WORKBENCH,
                binding.cbWorkbench.isChecked,
                binding.etWorkbenchSlot.text.toString().toIntOrNull()?.coerceIn(1, 5) ?: 2
            ),
            StationTask(
                StationId.PHARMACY,
                binding.cbPharmacy.isChecked,
                binding.etPharmacySlot.text.toString().toIntOrNull()?.coerceIn(1, 5) ?: 1
            ),
            StationTask(
                StationId.ARMORY,
                binding.cbArmory.isChecked,
                binding.etArmorySlot.text.toString().toIntOrNull()?.coerceIn(1, 5) ?: 1
            )
        )
    )

    private fun refreshStatus() {
        val enabled = DeltaAccessibilityService.instance != null
        binding.tvAccessibilityStatus.text = if (enabled) {
            "无障碍服务：已开启（就绪）"
        } else {
            "无障碍服务：未开启（点击下方按钮开启）"
        }
        binding.tvAccessibilityStatus.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.accent_green else R.color.accent_amber)
        )

        val nextRun = ConfigRepository.getNextRunMillis(this)
        binding.tvScheduleSummary.text = if (nextRun > System.currentTimeMillis()) {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            "下次执行：${fmt.format(Date(nextRun))}"
        } else {
            "下次执行：未开启"
        }

        binding.tvLogs.text = ConfigRepository.getLogs(this)
    }
}
