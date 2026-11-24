package com.deltaforce.autohelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.deltaforce.autohelper.model.AutomationConfig
import com.deltaforce.autohelper.model.ConfigRepository
import com.deltaforce.autohelper.model.DefaultCoordinates
import com.deltaforce.autohelper.model.NormPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DeltaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runningJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ConfigRepository.appendLog(this, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本助手采用定时 + 归一化坐标手势执行，无需持续抓取界面节点树
    }

    override fun onInterrupt() {
        stopAutomation("系统中断无障碍服务")
    }

    override fun onDestroy() {
        stopAutomation("服务已停止")
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun isRunning(): Boolean = runningJob?.isActive == true

    fun startAutomation(onStatusUpdate: (String) -> Unit = {}, onFinished: () -> Unit = {}) {
        if (isRunning()) {
            ConfigRepository.appendLog(this, "任务已在运行中，忽略重复触发")
            return
        }
        val config = ConfigRepository.load(this)
        runningJob = serviceScope.launch {
            try {
                runWorkflow(config, onStatusUpdate)
                ConfigRepository.appendLog(this@DeltaAccessibilityService, "本轮特勤处自动化执行完成")
            } catch (_: CancellationException) {
                ConfigRepository.appendLog(this@DeltaAccessibilityService, "任务已手动停止")
            } catch (e: Exception) {
                ConfigRepository.appendLog(this@DeltaAccessibilityService, "执行异常: ${e.message}")
            } finally {
                onFinished()
            }
        }
    }

    fun stopAutomation(reason: String = "用户取消") {
        if (runningJob?.isActive == true) {
            runningJob?.cancel()
            ConfigRepository.appendLog(this, "已终止任务：$reason")
        }
    }

    private suspend fun runWorkflow(
        config: AutomationConfig,
        onStatusUpdate: (String) -> Unit
    ) {
        val enabledStations = config.stations.filter { it.enabled }
        if (enabledStations.isEmpty()) {
            ConfigRepository.appendLog(this, "未勾选任何制造台，跳过执行")
            return
        }

        // 1. 启动游戏
        onStatusUpdate("正在启动《三角洲行动》...")
        ConfigRepository.appendLog(this, "启动游戏包：${config.gamePackageName}")
        val launchIntent = packageManager.getLaunchIntentForPackage(config.gamePackageName)
            ?: throw IllegalStateException("未安装目标游戏（${config.gamePackageName}）")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(launchIntent)
        delay(config.launchWaitSeconds * 1000L)

        // 2. 模式选择界面点击「前往游玩 >>」
        onStatusUpdate("点击前往游玩...")
        tapNormalized(DefaultCoordinates.MODE_PLAY_BUTTON, config)
        delay(config.lobbyWaitSeconds * 1000L)

        // 3. 关闭弹窗广告（右上角 X）
        if (config.popupCloseCount > 0) {
            onStatusUpdate("关闭弹窗广告...")
            repeat(config.popupCloseCount) {
                tapNormalized(DefaultCoordinates.POPUP_CLOSE_X, config)
                delay(1500L)
            }
        }

        // 4. 点击主大厅底部「特勤处」
        onStatusUpdate("进入特勤处...")
        tapNormalized(DefaultCoordinates.LOBBY_BLACK_SITE, config)
        delay(2200L)

        // 5. 领取所有已启用制造台的已完成物品
        for (station in enabledStations) {
            onStatusUpdate("领取 ${station.id.displayName} 成品...")
            tapNormalized(NormPoint(station.id.normX, station.id.normY), config)
            delay(1600L)
            // 若弹出「获得奖励」，点击底部空白跳过
            tapNormalized(DefaultCoordinates.REWARD_SKIP_BLANK, config)
            delay(1300L)
        }

        // 6. 依次进入各制造台并开始新一轮制造（含自动一键补齐材料）
        for (station in enabledStations) {
            onStatusUpdate("配置 ${station.id.displayName} 新制造...")
            // 进入制造台
            tapNormalized(NormPoint(station.id.normX, station.id.normY), config)
            delay(1800L)

            // 选中左侧列表中的目标物品
            val itemPoint = DefaultCoordinates.listItemPoint(station.itemSlotIndex)
            tapNormalized(itemPoint, config)
            delay(1100L)

            // 第一次点击右下角「生产」：若缺少材料会弹出「一键补齐清单」
            tapNormalized(DefaultCoordinates.CRAFT_PRODUCE_BUTTON, config)
            delay(1600L)

            // 点击「一键补齐清单」底部绿色购买按钮（若未弹窗，此位置为空白背景，点击无副作用）
            tapNormalized(DefaultCoordinates.BUY_MISSING_CONFIRM, config)
            delay(2200L)

            // 再次点击右下角「生产」确认开始制造
            tapNormalized(DefaultCoordinates.CRAFT_PRODUCE_BUTTON, config)
            delay(1500L)

            // 点击左上角返回特勤处总览
            tapNormalized(DefaultCoordinates.BACK_BUTTON, config)
            delay(1600L)
        }

        // 7. 返回主大厅
        onStatusUpdate("返回主大厅...")
        tapNormalized(DefaultCoordinates.BACK_BUTTON, config)
        delay(1200L)
    }

    private suspend fun tapNormalized(point: NormPoint, config: AutomationConfig) {
        val (landscapeW, landscapeH) = getLandscapeScreenSize()
        val normX = (point.x + config.offsetX).coerceIn(0.01f, 0.99f)
        val normY = (point.y + config.offsetY).coerceIn(0.01f, 0.99f)

        val jitter = config.randomJitterPx
        val dx = if (jitter > 0) Random.nextInt(-jitter, jitter + 1) else 0
        val dy = if (jitter > 0) Random.nextInt(-jitter, jitter + 1) else 0

        val px = (normX * landscapeW + dx).coerceIn(1f, (landscapeW - 1).toFloat())
        val py = (normY * landscapeH + dy).coerceIn(1f, (landscapeH - 1).toFloat())
        val durationMs = Random.nextLong(65L, 115L)

        dispatchTap(px, py, durationMs)
    }

    private suspend fun dispatchTap(x: Float, y: Float, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }

    private fun getLandscapeScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Pair(max(bounds.width(), bounds.height()), min(bounds.width(), bounds.height()))
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(max(dm.widthPixels, dm.heightPixels), min(dm.widthPixels, dm.heightPixels))
        }
    }

    companion object {
        @Volatile
        var instance: DeltaAccessibilityService? = null
            private set
    }
}
