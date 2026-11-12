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

/**
 * One click position.
 *
 * @param pressMs press duration; 0 means "use the profile default".
 * @param delayMs extra wait before this point inside a cycle, so each point can have its own pace.
 * @param enabled false keeps the point on screen but the engine skips it.
 */
@Serializable
data class ClickPoint(
    val id: Long = 0L,
    val x: Int = 0,
    val y: Int = 0,
    val pressMs: Int = 0,
    val delayMs: Int = 0,
    val enabled: Boolean = true,
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
    /** 0..this many extra milliseconds are added to every wait, anti rhythm detection. */
    val randomDelayMs: Int = 0,
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
    /** Volume up / down slide the floating settings panel in and out. */
    val volumeKeysToggle: Boolean = true,
    /** Stop the loop when another app comes to the front. */
    val stopOnAppSwitch: Boolean = false,
    /** Where the user parked the control bar; -1 means "pick a default spot". */
    val barX: Int = -1,
    val barY: Int = -1,
    val vibrateOnClick: Boolean = false,
    val overlayEnabled: Boolean = true,
    val scriptSpeed: Float = 1f,
    /** 0 repeats forever. */
    val scriptRepeat: Int = 0,
)

/** A saved set of click parameters. Points are deliberately not part of a template. */
@Serializable
data class ClickTemplate(
    val id: Long = 0L,
    val name: String = "",
    val profile: ClickProfile = ClickProfile(),
) {
    /** Template with the layout dependent parts stripped, so it can be applied anywhere. */
    fun parameters(): ClickProfile = profile.copy(points = emptyList(), baseWidth = 0, baseHeight = 0)
}

/**
 * A saved setup: the points themselves plus every parameter. Templates only carry parameters,
 * a plan is the whole thing, so switching plans switches what is clicked and how.
 */
@Serializable
data class ClickPlan(
    val id: Long = 0L,
    val name: String = "",
    val profile: ClickProfile = ClickProfile(),
    val createdAt: Long = 0L,
) {
    val pointCount: Int get() = profile.points.size
}

/** Persisted state of the whole app. */
@Serializable
data class ClickerData(
    val profile: ClickProfile = ClickProfile(),
    val scripts: List<RecordedScript> = emptyList(),
    val templates: List<ClickTemplate> = emptyList(),
    val options: EngineOptions = EngineOptions(),
    val plans: List<ClickPlan> = emptyList(),
    val activeScriptId: Long = 0L,
    val activeTemplateId: Long = 0L,
    val activePlanId: Long = 0L,
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

    /** One undoable change to the point list. Kept in memory only. */
    private sealed interface Undo {
        data class Added(val id: Long) : Undo
        data class Removed(val point: ClickPoint, val index: Int) : Undo
        data class Moved(val before: ClickPoint) : Undo
        data class Cleared(val points: List<ClickPoint>) : Undo
    }

    private val undoStack = ArrayDeque<Undo>()

    private val _undoCount = MutableStateFlow(0)

    /** Number of undoable steps, the UI hides the undo button at zero. */
    val undoCount: StateFlow<Int> = _undoCount.asStateFlow()

    private fun push(undo: Undo) {
        undoStack.addLast(undo)
        while (undoStack.size > 50) undoStack.removeFirst()
        _undoCount.value = undoStack.size
    }

    /** Reverts the last point change; returns a short description or null. */
    fun undo(): String? {
        val last = undoStack.removeLastOrNull() ?: return null
        _undoCount.value = undoStack.size
        return when (last) {
            is Undo.Added -> {
                updateProfile { it.copy(points = it.points.filterNot { p -> p.id == last.id }) }
                "已撤销添加"
            }
            is Undo.Removed -> {
                updateProfile { profile ->
                    val restored = profile.points.toMutableList()
                    restored.add(last.index.coerceIn(0, restored.size), last.point)
                    profile.copy(points = restored)
                }
                "已恢复点位 (${last.point.x}, ${last.point.y})"
            }
            is Undo.Moved -> {
                updatePoint(last.before)
                "已撤销移动"
            }
            is Undo.Cleared -> {
                updateProfile { it.copy(points = last.points) }
                "已恢复 ${last.points.size} 个点位"
            }
        }
    }

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

    /** Clamps a single number without touching the point list. */
    fun bumpInterval(delta: Int) = updateProfile {
        it.copy(intervalMs = (it.intervalMs + delta).coerceIn(1, 60_000))
    }

    fun bumpPress(delta: Int) = updateProfile {
        it.copy(pressMs = (it.pressMs + delta).coerceIn(1, 60_000))
    }

    fun bumpRandomDelay(delta: Int) = updateProfile {
        it.copy(randomDelayMs = (it.randomDelayMs + delta).coerceIn(0, 5_000))
    }

    fun bumpRepeat(delta: Int) = updateProfile {
        it.copy(repeatCount = (it.repeatCount + delta).coerceIn(0, 1_000_000))
    }

    fun bumpStartDelay(delta: Int) = updateProfile {
        it.copy(startDelayMs = (it.startDelayMs + delta).coerceIn(0, 60_000))
    }

    fun setMode(mode: ClickMode) = updateProfile { it.copy(mode = mode) }

    fun setCondition(kind: ConditionKind) = updateProfile { it.copy(condition = kind) }

    fun updateOptions(transform: (EngineOptions) -> EngineOptions) =
        update { it.copy(options = transform(it.options)) }

    /** Test hook: forgets everything and clears the undo history. */
    internal fun resetForTest() {
        undoStack.clear()
        _undoCount.value = 0
        _data.value = ClickerData()
    }

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

    fun addPoint(point: ClickPoint): ClickPoint {
        val next = point.copy(id = (_data.value.profile.points.maxOfOrNull { it.id } ?: 0L) + 1L)
        updateProfile { it.copy(points = it.points + next) }
        push(Undo.Added(next.id))
        return next
    }

    fun removePoint(id: Long) {
        val current = _data.value.profile.points
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = current[index]
        updateProfile { it.copy(points = it.points.filterNot { p -> p.id == id }) }
        push(Undo.Removed(removed, index))
    }

    fun updatePoint(point: ClickPoint, recordUndo: Boolean = true) {
        val before = _data.value.profile.points.firstOrNull { it.id == point.id } ?: return
        if (before == point) return
        updateProfile { profile ->
            profile.copy(points = profile.points.map { if (it.id == point.id) point else it })
        }
        if (recordUndo) push(Undo.Moved(before))
    }

    fun clearPoints() {
        val current = _data.value.profile.points
        if (current.isEmpty()) return
        updateProfile { it.copy(points = emptyList()) }
        push(Undo.Cleared(current))
    }

    // ---- templates ----

    fun saveTemplate(name: String): ClickTemplate {
        val template = ClickTemplate(
            id = System.currentTimeMillis(),
            name = name,
            profile = _data.value.profile,
        )
        update { current ->
            current.copy(
                templates = (current.templates + template).takeLast(24),
                activeTemplateId = template.id,
            )
        }
        return template
    }

    /** Saves the current profile, points included, as a reusable plan. */
    fun savePlan(name: String): ClickPlan {
        val plan = ClickPlan(
            id = System.currentTimeMillis(),
            name = name,
            profile = _data.value.profile,
            createdAt = System.currentTimeMillis(),
        )
        update { current ->
            current.copy(
                plans = (current.plans + plan).takeLast(40),
                activePlanId = plan.id,
            )
        }
        return plan
    }

    /** Overwrites a saved plan with what is on screen right now. */
    fun updatePlanFromCurrent(id: Long) = update { current ->
        current.copy(
            plans = current.plans.map { if (it.id == id) it.copy(profile = current.profile) else it },
            activePlanId = id,
        )
    }

    fun renamePlan(id: Long, name: String) = update { current ->
        current.copy(plans = current.plans.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deletePlan(id: Long) = update { current ->
        current.copy(
            plans = current.plans.filterNot { it.id == id },
            activePlanId = if (current.activePlanId == id) 0L else current.activePlanId,
        )
    }

    /** Switches to a plan: points and parameters are both replaced. */
    fun applyPlan(plan: ClickPlan) = update { current ->
        current.copy(profile = plan.profile, activePlanId = plan.id)
    }

    fun deleteTemplate(id: Long) = update { current ->
        current.copy(
            templates = current.templates.filterNot { it.id == id },
            activeTemplateId = if (current.activeTemplateId == id) 0L else current.activeTemplateId,
        )
    }

    /** Applies a template: parameters change, the points on screen stay where they are. */
    fun applyTemplate(template: ClickTemplate) {
        val parameters = template.parameters()
        update { current ->
            val points = current.profile.points
            current.copy(
                profile = parameters.copy(
                    points = points,
                    baseWidth = current.profile.baseWidth,
                    baseHeight = current.profile.baseHeight,
                    color = current.profile.color,
                ),
                activeTemplateId = template.id,
            )
        }
    }

    fun normalizeTo(width: Int, height: Int) = updateProfile { profile ->
        if (profile.baseWidth == width && profile.baseHeight == height) {
            profile
        } else {
            profile.copy(baseWidth = width, baseHeight = height)
        }
    }
}
