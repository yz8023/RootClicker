package com.roottap.clicker.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** How the next point gets picked. */
@Serializable
enum class ClickMode { SEQUENTIAL, SIMULTANEOUS, RANDOM }

/** What has to be true before a click cycle runs. */
@Serializable
enum class ConditionKind { NONE, COLOR }

/** One click position. [pressMs] 0 means "use the profile default". */
@Serializable
data class ClickPoint(
    val id: Long = 0L,
    val x: Int = 0,
    val y: Int = 0,
    val pressMs: Int = 0,
) {
    val label: String get() = "($x, $y)"
}

/** Wait until the pixel at ([x], [y]) matches [color] before clicking. */
@Serializable
data class ColorCondition(
    val x: Int = 0,
    val y: Int = 0,
    val color: Int = 0,
    val tolerance: Int = 24,
    val pollMs: Int = 300,
    /** 0 waits forever. */
    val timeoutMs: Int = 0,
)

/** Everything the click loop needs. Coordinates are absolute pixels at [baseWidth] x [baseHeight]. */
@Serializable
data class ClickProfile(
    val points: List<ClickPoint> = emptyList(),
    val mode: ClickMode = ClickMode.SEQUENTIAL,
    /** Delay between two cycles. */
    val intervalMs: Int = 100,
    /** Delay between two points inside one cycle. */
    val pointGapMs: Int = 40,
    /** Press duration of a normal click; 600 ms and up reads as a long press to the app. */
    val pressMs: Int = 40,
    /** 0 repeats forever. */
    val repeatCount: Int = 0,
    val startDelayMs: Int = 0,
    /** Random offset added to every coordinate, keeps anti bot checks calm. */
    val jitterPx: Int = 0,
    val randomOrder: Boolean = false,
    val condition: ConditionKind = ConditionKind.NONE,
    val color: ColorCondition = ColorCondition(),
    val baseWidth: Int = 0,
    val baseHeight: Int = 0,
)

/** One recorded gesture. Coordinates are normalized to 0..1 so they survive a rotation. */
@Serializable
data class RecordedAction(
    val x1: Float = 0f,
    val y1: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    /** Silent gap before this gesture starts. */
    val delayMs: Int = 0,
    /** How long the finger stays down while moving from the first to the second point. */
    val durationMs: Int = 40,
)

/** A recorded gesture sequence. */
@Serializable
data class RecordedScript(
    val id: Long = 0L,
    val name: String = "",
    val createdAt: Long = 0L,
    val actions: List<RecordedAction> = emptyList(),
) {
    val totalMs: Int get() = actions.sumOf { it.delayMs + it.durationMs }
}

/** User options that do not belong to a single profile. */
@Serializable
data class EngineOptions(
    val showMarkers: Boolean = true,
    val vibrateOnClick: Boolean = false,
    val overlayEnabled: Boolean = true,
    val scriptSpeed: Float = 1f,
    /** 0 repeats forever. */
    val scriptRepeat: Int = 0,
)

/** Persisted state of the whole app. */
@Serializable
data class ClickerData(
    val profile: ClickProfile = ClickProfile(),
    val scripts: List<RecordedScript> = emptyList(),
    val options: EngineOptions = EngineOptions(),
    val activeScriptId: Long = 0L,
)

/** JSON backed store for profiles and scripts. */
object ClickerStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var file: File? = null
    private var saveJob: kotlinx.coroutines.Job? = null

    private val _data = MutableStateFlow(ClickerData())
    val data: StateFlow<ClickerData> = _data.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        val target = File(context.filesDir, "clicker.json")
        file = target
        runCatching {
            if (target.exists()) {
                _data.value = json.decodeFromString(ClickerData.serializer(), target.readText())
            }
        }
    }

    fun update(transform: (ClickerData) -> ClickerData) {
        _data.update(transform)
        scheduleSave()
    }

    fun updateProfile(transform: (ClickProfile) -> ClickProfile) =
        update { it.copy(profile = transform(it.profile)) }

    fun updateOptions(transform: (EngineOptions) -> EngineOptions) =
        update { it.copy(options = transform(it.options)) }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            // Sliders fire on every pixel; only the last value needs to hit the disk.
            kotlinx.coroutines.delay(250)
            val target = file ?: return@launch
            val snapshot = _data.value
            runCatching {
                target.writeText(json.encodeToString(ClickerData.serializer(), snapshot))
            }
        }
    }

    fun addScript(script: RecordedScript) = update { current ->
        current.copy(
            scripts = (current.scripts + script).takeLast(40),
            activeScriptId = script.id,
        )
    }

    fun removeScript(id: Long) = update { current ->
        current.copy(
            scripts = current.scripts.filterNot { it.id == id },
            activeScriptId = if (current.activeScriptId == id) 0L else current.activeScriptId,
        )
    }

    fun setActiveScript(id: Long) = update { it.copy(activeScriptId = id) }

    fun addPoint(point: ClickPoint) = updateProfile { profile ->
        val next = point.copy(id = (profile.points.maxOfOrNull { it.id } ?: 0L) + 1L)
        profile.copy(points = profile.points + next)
    }

    fun removePoint(id: Long) = updateProfile { profile ->
        profile.copy(points = profile.points.filterNot { it.id == id })
    }

    fun updatePoint(point: ClickPoint) = updateProfile { profile ->
        profile.copy(points = profile.points.map { if (it.id == point.id) point else it })
    }

    fun clearPoints() = updateProfile { it.copy(points = emptyList()) }

    fun normalizeTo(width: Int, height: Int) = updateProfile { profile ->
        if (profile.baseWidth == width && profile.baseHeight == height) {
            profile
        } else {
            profile.copy(baseWidth = width, baseHeight = height)
        }
    }
}
