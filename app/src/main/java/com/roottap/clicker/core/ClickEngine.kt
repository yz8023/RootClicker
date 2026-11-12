package com.roottap.clicker.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/** What the engine is doing right now. */
data class EngineState(
    val running: Boolean = false,
    val playingScript: Boolean = false,
    val cycles: Long = 0,
    val clicks: Long = 0,
    val message: String? = null,
)

/**
 * Runs click cycles and recorded scripts. All timing lives here; injection itself is delegated
 * to a [TouchSink] so the engine also works through the slow `input` fallback.
 */
class ClickEngine(private val sinkProvider: suspend () -> TouchSink?) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var job: Job? = null

    /** Called after every injected touch; used for haptic feedback. */
    var onClick: (() -> Unit)? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun stop(message: String? = null) {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, playingScript = false, message = message) }
    }

    fun reset() {
        stop()
        _state.value = EngineState()
    }

    fun startProfile(profile: ClickProfile, screenWidth: Int, screenHeight: Int) {
        if (isRunning) return
        if (profile.points.isEmpty()) {
            _state.update { it.copy(running = false, message = "还没有点位，请先添加") }
            return
        }
        _state.value = EngineState(running = true)
        job = scope.launch {
            var sink: TouchSink? = null
            try {
                sink = sinkProvider()
                if (sink == null) {
                    _state.update { it.copy(running = false, message = "无法获得注入通道，请检查 root 授权") }
                    return@launch
                }
                if (profile.startDelayMs > 0) delay(profile.startDelayMs.toLong())
                var cycle = 0L
                while (isActive) {
                    if (profile.condition == ConditionKind.COLOR) {
                        val ready = awaitColor(profile.color)
                        if (!ready) {
                            _state.update { it.copy(running = false, message = "等待颜色条件超时") }
                            return@launch
                        }
                    }
                    val points = orderedPoints(profile)
                    when (profile.mode) {
                        ClickMode.SEQUENTIAL -> {
                            for (point in points) {
                                if (!isActive) return@launch
                                tapPoint(sink, profile, point, screenWidth, screenHeight)
                                _state.update { it.copy(clicks = it.clicks + 1) }
                                onClick?.invoke()
                                if (profile.pointGapMs > 0) delay(profile.pointGapMs.toLong())
                            }
                        }
                        ClickMode.SIMULTANEOUS -> {
                            multiTapPoints(sink, profile, points, screenWidth, screenHeight)
                            _state.update { it.copy(clicks = it.clicks + points.size) }
                            onClick?.invoke()
                        }
                        ClickMode.RANDOM -> {
                            val point = points[Random.nextInt(points.size)]
                            tapPoint(sink, profile, point, screenWidth, screenHeight)
                            _state.update { it.copy(clicks = it.clicks + 1) }
                            onClick?.invoke()
                        }
                    }
                    cycle++
                    _state.update { it.copy(cycles = cycle) }
                    if (profile.repeatCount > 0 && cycle >= profile.repeatCount) {
                        _state.update { it.copy(running = false, message = "已完成的循环次数") }
                        return@launch
                    }
                    if (profile.intervalMs > 0) delay(profile.intervalMs.toLong())
                }
            } catch (t: Throwable) {
                _state.update { it.copy(running = false, message = t.message ?: "运行失败") }
            } finally {
                if (_state.value.running) {
                    _state.update { it.copy(running = false) }
                }
            }
        }
    }

    fun startScript(
        script: RecordedScript,
        speed: Float,
        repeat: Int,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        if (isRunning) return
        if (script.actions.isEmpty()) {
            _state.update { it.copy(message = "脚本没有动作") }
            return
        }
        _state.value = EngineState(running = true, playingScript = true)
        job = scope.launch {
            try {
                val sink = sinkProvider()
                if (sink == null) {
                    _state.update { it.copy(running = false, playingScript = false, message = "无法获得注入通道") }
                    return@launch
                }
                val rate = speed.coerceIn(0.1f, 5f)
                var round = 0L
                while (isActive) {
                    for (action in script.actions) {
                        if (!isActive) return@launch
                        val waitMs = (action.delayMs / rate).roundToInt().coerceAtLeast(0)
                        if (waitMs > 0) delay(waitMs.toLong())
                        val x1 = (action.x1 * (screenWidth - 1)).roundToInt().coerceIn(0, screenWidth - 1)
                        val y1 = (action.y1 * (screenHeight - 1)).roundToInt().coerceIn(0, screenHeight - 1)
                        val x2 = (action.x2 * (screenWidth - 1)).roundToInt().coerceIn(0, screenWidth - 1)
                        val y2 = (action.y2 * (screenHeight - 1)).roundToInt().coerceIn(0, screenHeight - 1)
                        val duration = (action.durationMs / rate).roundToInt().coerceIn(1, 60_000)
                        if (x1 == x2 && y1 == y2) {
                            sink.tap(x1, y1, duration)
                        } else {
                            sink.stroke(x1, y1, x2, y2, duration)
                        }
                        _state.update { it.copy(clicks = it.clicks + 1) }
                        onClick?.invoke()
                        delay(duration.toLong())
                    }
                    round++
                    _state.update { it.copy(cycles = round) }
                    if (repeat > 0 && round >= repeat) {
                        _state.update { it.copy(running = false, playingScript = false, message = "播放完成") }
                        return@launch
                    }
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(running = false, playingScript = false, message = t.message ?: "播放失败")
                }
            } finally {
                if (_state.value.running) {
                    _state.update { it.copy(running = false, playingScript = false) }
                }
            }
        }
    }

    private fun orderedPoints(profile: ClickProfile): List<ClickPoint> =
        if (profile.randomOrder) profile.points.shuffled() else profile.points

    private suspend fun tapPoint(
        sink: TouchSink,
        profile: ClickProfile,
        point: ClickPoint,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        val (x, y) = resolve(point.x, point.y, profile, screenWidth, screenHeight)
        val press = (if (point.pressMs > 0) point.pressMs else profile.pressMs).coerceIn(1, 60_000)
        sink.tap(x, y, press)
        // The injector blocks for the whole press; waiting here keeps the rhythm honest.
        delay(press.toLong())
    }

    private suspend fun multiTapPoints(
        sink: TouchSink,
        profile: ClickProfile,
        points: List<ClickPoint>,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        val xs = IntArray(points.size)
        val ys = IntArray(points.size)
        points.forEachIndexed { index, point ->
            val (x, y) = resolve(point.x, point.y, profile, screenWidth, screenHeight)
            xs[index] = x
            ys[index] = y
        }
        val press = profile.pressMs.coerceIn(1, 60_000)
        sink.multiTap(xs, ys, press)
        delay(press.toLong())
    }

    private fun resolve(
        x: Int,
        y: Int,
        profile: ClickProfile,
        screenWidth: Int,
        screenHeight: Int,
    ): Pair<Int, Int> {
        // Points are stored at the resolution they were captured at; scale them when the
        // device rotated or the density changed.
        val scaleX = if (profile.baseWidth > 0) screenWidth.toFloat() / profile.baseWidth else 1f
        val scaleY = if (profile.baseHeight > 0) screenHeight.toFloat() / profile.baseHeight else 1f
        val jitter = profile.jitterPx
        val jx = if (jitter > 0) Random.nextInt(-jitter, jitter + 1) else 0
        val jy = if (jitter > 0) Random.nextInt(-jitter, jitter + 1) else 0
        val px = (x * scaleX).roundToInt() + jx
        val py = (y * scaleY).roundToInt() + jy
        return px.coerceIn(0, (screenWidth - 1).coerceAtLeast(0)) to
            py.coerceIn(0, (screenHeight - 1).coerceAtLeast(0))
    }

    private suspend fun awaitColor(condition: ColorCondition): Boolean {
        val deadline = if (condition.timeoutMs > 0) {
            System.currentTimeMillis() + condition.timeoutMs
        } else {
            Long.MAX_VALUE
        }
        while (currentCoroutineContext().isActive) {
            val pixel = ScreenSampler.sample(condition.x, condition.y)
            if (pixel != null && ScreenSampler.matches(pixel, condition.color, condition.tolerance)) {
                return true
            }
            if (System.currentTimeMillis() >= deadline) return false
            delay(condition.pollMs.toLong().coerceIn(80, 5_000))
        }
        return false
    }
}
