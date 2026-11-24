package com.deltaforce.autohelper.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ScheduleMode {
    INTERVAL,    // 每隔 N 分钟执行一次（默认 7小时30分 = 450分钟）
    DAILY_FIXED  // 每天固定时刻执行（默认 07:30）
}

enum class StationId(val displayName: String, val normX: Float, val normY: Float) {
    TECH_CENTER("技术中心", 0.205f, 0.365f),
    WORKBENCH("工作台", 0.365f, 0.365f),
    PHARMACY("制药台", 0.205f, 0.720f),
    ARMORY("防具台", 0.365f, 0.720f)
}

data class StationTask(
    val id: StationId,
    val enabled: Boolean,
    val itemSlotIndex: Int // 1-based index in left item list (1..5)
)

data class NormPoint(val x: Float, val y: Float)

/**
 * 所有归一化坐标均基于用户上传的 2376x1080 横屏录屏精确标定。
 */
object DefaultCoordinates {
    val MODE_PLAY_BUTTON = NormPoint(0.160f, 0.880f)       // 烽火地带「前往游玩 >>」
    val POPUP_CLOSE_X = NormPoint(0.913f, 0.068f)          // 弹窗广告右上角 X
    val LOBBY_BLACK_SITE = NormPoint(0.400f, 0.922f)       // 主大厅底部「特勤处」
    val REWARD_SKIP_BLANK = NormPoint(0.500f, 0.880f)      // 获得奖励「点击空白跳过」
    val BACK_BUTTON = NormPoint(0.055f, 0.065f)            // 左上角返回箭头 <
    val CRAFT_PRODUCE_BUTTON = NormPoint(0.865f, 0.915f)   // 制造台右下角「生产」
    val BUY_MISSING_CONFIRM = NormPoint(0.500f, 0.820f)    // 一键补齐清单底部绿色购买按钮

    private val LIST_ITEM_Y = floatArrayOf(0.295f, 0.430f, 0.575f, 0.715f, 0.860f)

    fun listItemPoint(slotIndex1Based: Int): NormPoint {
        val idx = (slotIndex1Based - 1).coerceIn(0, LIST_ITEM_Y.lastIndex)
        return NormPoint(0.170f, LIST_ITEM_Y[idx])
    }
}

data class AutomationConfig(
    val gamePackageName: String = "com.tencent.tmgp.dfm",
    val scheduleEnabled: Boolean = false,
    val scheduleMode: ScheduleMode = ScheduleMode.INTERVAL,
    val dailyHour: Int = 7,
    val dailyMinute: Int = 30,
    val intervalMinutes: Int = 450, // 7 小时 30 分钟
    val launchWaitSeconds: Int = 22,
    val lobbyWaitSeconds: Int = 8,
    val popupCloseCount: Int = 1,
    val offsetX: Float = 0.0f,
    val offsetY: Float = 0.0f,
    val randomJitterPx: Int = 10,
    val stations: List<StationTask> = listOf(
        StationTask(StationId.TECH_CENTER, enabled = false, itemSlotIndex = 1),
        StationTask(StationId.WORKBENCH, enabled = true, itemSlotIndex = 2),
        StationTask(StationId.PHARMACY, enabled = true, itemSlotIndex = 1),
        StationTask(StationId.ARMORY, enabled = true, itemSlotIndex = 1)
    )
)

object ConfigRepository {
    private const val PREF_NAME = "delta_auto_helper_prefs"
    private const val KEY_CONFIG = "config_json"
    private const val KEY_LOGS = "execution_logs"
    private const val KEY_NEXT_RUN = "next_run_millis"

    fun load(context: Context): AutomationConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AutomationConfig()
        return try {
            val obj = JSONObject(raw)
            val stationsArr = obj.optJSONArray("stations")
            val stations = if (stationsArr != null && stationsArr.length() > 0) {
                (0 until stationsArr.length()).mapNotNull { i ->
                    val s = stationsArr.getJSONObject(i)
                    val id = runCatching { StationId.valueOf(s.getString("id")) }.getOrNull()
                        ?: return@mapNotNull null
                    StationTask(
                        id = id,
                        enabled = s.optBoolean("enabled", false),
                        itemSlotIndex = s.optInt("itemSlotIndex", 1).coerceIn(1, 5)
                    )
                }
            } else {
                AutomationConfig().stations
            }
            AutomationConfig(
                gamePackageName = obj.optString("gamePackageName", "com.tencent.tmgp.dfm"),
                scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
                scheduleMode = runCatching {
                    ScheduleMode.valueOf(obj.optString("scheduleMode", ScheduleMode.INTERVAL.name))
                }.getOrDefault(ScheduleMode.INTERVAL),
                dailyHour = obj.optInt("dailyHour", 7).coerceIn(0, 23),
                dailyMinute = obj.optInt("dailyMinute", 30).coerceIn(0, 59),
                intervalMinutes = obj.optInt("intervalMinutes", 450).coerceAtLeast(10),
                launchWaitSeconds = obj.optInt("launchWaitSeconds", 22).coerceIn(5, 90),
                lobbyWaitSeconds = obj.optInt("lobbyWaitSeconds", 8).coerceIn(3, 40),
                popupCloseCount = obj.optInt("popupCloseCount", 1).coerceIn(0, 3),
                offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                offsetY = obj.optDouble("offsetY", 0.0).toFloat(),
                randomJitterPx = obj.optInt("randomJitterPx", 10).coerceIn(0, 30),
                stations = stations
            )
        } catch (_: Exception) {
            AutomationConfig()
        }
    }

    fun save(context: Context, config: AutomationConfig) {
        val stationsArr = JSONArray()
        config.stations.forEach { st ->
            stationsArr.put(
                JSONObject()
                    .put("id", st.id.name)
                    .put("enabled", st.enabled)
                    .put("itemSlotIndex", st.itemSlotIndex)
            )
        }
        val obj = JSONObject()
            .put("gamePackageName", config.gamePackageName)
            .put("scheduleEnabled", config.scheduleEnabled)
            .put("scheduleMode", config.scheduleMode.name)
            .put("dailyHour", config.dailyHour)
            .put("dailyMinute", config.dailyMinute)
            .put("intervalMinutes", config.intervalMinutes)
            .put("launchWaitSeconds", config.launchWaitSeconds)
            .put("lobbyWaitSeconds", config.lobbyWaitSeconds)
            .put("popupCloseCount", config.popupCloseCount)
            .put("offsetX", config.offsetX.toDouble())
            .put("offsetY", config.offsetY.toDouble())
            .put("randomJitterPx", config.randomJitterPx)
            .put("stations", stationsArr)

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG, obj.toString())
            .apply()
    }

    fun appendLog(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val existing = prefs.getString(KEY_LOGS, "") ?: ""
        val lines = ("[$ts] $message
$existing").lines().take(25).joinToString("
")
        prefs.edit().putString(KEY_LOGS, lines).apply()
    }

    fun getLogs(context: Context): String {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOGS, "") ?: ""
        return raw.ifBlank { "暂无执行记录" }
    }

    fun setNextRunMillis(context: Context, millis: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_RUN, millis)
            .apply()
    }

    fun getNextRunMillis(context: Context): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_RUN, 0L)
    }
}
