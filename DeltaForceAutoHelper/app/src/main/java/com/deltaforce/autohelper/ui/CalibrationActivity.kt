package com.deltaforce.autohelper.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deltaforce.autohelper.databinding.ActivityCalibrationBinding
import com.deltaforce.autohelper.model.AutomationConfig
import com.deltaforce.autohelper.model.ConfigRepository

class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadValues()

        binding.btnSaveCalibration.setOnClickListener {
            val current = ConfigRepository.load(this)
            val updated = current.copy(
                gamePackageName = binding.etPackageName.text.toString().trim()
                    .ifBlank { "com.tencent.tmgp.dfm" },
                launchWaitSeconds = binding.etLaunchWait.text.toString().toIntOrNull()
                    ?.coerceIn(5, 90) ?: 22,
                popupCloseCount = binding.etPopupCloseCount.text.toString().toIntOrNull()
                    ?.coerceIn(0, 3) ?: 1,
                offsetX = binding.etOffsetX.text.toString().toFloatOrNull()
                    ?.coerceIn(-0.15f, 0.15f) ?: 0f,
                offsetY = binding.etOffsetY.text.toString().toFloatOrNull()
                    ?.coerceIn(-0.15f, 0.15f) ?: 0f
            )
            ConfigRepository.save(this, updated)
            Toast.makeText(this, "高级参数已保存", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnResetDefaults.setOnClickListener {
            val defaults = AutomationConfig()
            val current = ConfigRepository.load(this).copy(
                gamePackageName = defaults.gamePackageName,
                launchWaitSeconds = defaults.launchWaitSeconds,
                popupCloseCount = defaults.popupCloseCount,
                offsetX = 0f,
                offsetY = 0f
            )
            ConfigRepository.save(this, current)
            loadValues()
            Toast.makeText(this, "已恢复默认参数", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadValues() {
        val config = ConfigRepository.load(this)
        binding.etPackageName.setText(config.gamePackageName)
        binding.etLaunchWait.setText(config.launchWaitSeconds.toString())
        binding.etPopupCloseCount.setText(config.popupCloseCount.toString())
        binding.etOffsetX.setText(config.offsetX.toString())
        binding.etOffsetY.setText(config.offsetY.toString())
    }
}
