package com.roottap.clicker.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what the engine asks the sink to do. */
private class FakeSink : TouchSink {
    val taps = mutableListOf<Triple<Int, Int, Int>>()
    val multi = mutableListOf<List<Pair<Int, Int>>>()
    val strokes = mutableListOf<List<Int>>()

    override val description: String = "fake"

    override fun tap(x: Int, y: Int, durationMs: Int): Boolean {
        synchronized(taps) { taps += Triple(x, y, durationMs) }
        return true
    }

    override fun multiTap(xs: IntArray, ys: IntArray, durationMs: Int): Boolean {
        synchronized(multi) {
            multi += xs.indices.map { xs[it] to ys[it] }
        }
        return true
    }

    override fun stroke(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        synchronized(strokes) { strokes += listOf(x1, y1, x2, y2, durationMs) }
        return true
    }
}

private fun awaitIdle(engine: ClickEngine, timeoutMs: Long = 8_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (engine.state.value.running && System.currentTimeMillis() < deadline) {
        Thread.sleep(10)
    }
    check(!engine.state.value.running) { "engine did not stop in time" }
}

class ClickEngineTest {

    private fun profile(
        points: List<ClickPoint>,
        mode: ClickMode = ClickMode.SEQUENTIAL,
        repeat: Int = 1,
    ) = ClickProfile(
        points = points,
        mode = mode,
        intervalMs = 5,
        pointGapMs = 2,
        pressMs = 1,
        repeatCount = repeat,
        baseWidth = 1080,
        baseHeight = 2400,
    )

    @Test
    fun sequentialProfileClicksEveryPointInOrder() {
        val sink = FakeSink()
        val engine = ClickEngine { sink }
        val points = listOf(ClickPoint(1, 10, 20), ClickPoint(2, 30, 40))
        engine.startProfile(profile(points), 1080, 2400)
        awaitIdle(engine)
        assertEquals(2, sink.taps.size)
        assertEquals(10, sink.taps[0].first)
        assertEquals(20, sink.taps[0].second)
        assertEquals(30, sink.taps[1].first)
        assertTrue(engine.state.value.clicks >= 2)
    }

    @Test
    fun repeatCountStopsTheLoop() {
        val sink = FakeSink()
        val engine = ClickEngine { sink }
        val points = listOf(ClickPoint(1, 100, 100))
        engine.startProfile(profile(points, repeat = 3), 1080, 2400)
        awaitIdle(engine)
        assertEquals(3, sink.taps.size)
        assertEquals(3, engine.state.value.cycles)
    }

    @Test
    fun simultaneousModeSendsOneMultiTouchFrame() {
        val sink = FakeSink()
        val engine = ClickEngine { sink }
        val points = listOf(
            ClickPoint(1, 100, 200),
            ClickPoint(2, 300, 400),
            ClickPoint(3, 500, 600),
        )
        engine.startProfile(profile(points, mode = ClickMode.SIMULTANEOUS), 1080, 2400)
        awaitIdle(engine)
        assertEquals(1, sink.multi.size)
        assertEquals(3, sink.multi.first().size)
        assertEquals(300 to 400, sink.multi.first()[1])
    }

    @Test
    fun coordinatesAreScaledToTheCurrentScreen() {
        val sink = FakeSink()
        val engine = ClickEngine { sink }
        val points = listOf(ClickPoint(1, 540, 1200))
        // Captured on a 1080x2400 screen, replayed on a 2160x4800 one.
        engine.startProfile(profile(points), 2160, 4800)
        awaitIdle(engine)
        assertEquals(1080, sink.taps.first().first)
        assertEquals(2400, sink.taps.first().second)
    }

    @Test
    fun emptyProfileReportsAMessage() {
        val engine = ClickEngine { FakeSink() }
        engine.startProfile(profile(emptyList()), 1080, 2400)
        assertNotNull(engine.state.value.message)
        assertTrue(!engine.state.value.running)
    }

    @Test
    fun recordedScriptPlaysWithSpeedAndStrokes() {
        val sink = FakeSink()
        val engine = ClickEngine { sink }
        val script = RecordedScript(
            id = 1,
            name = "test",
            actions = listOf(
                RecordedAction(x1 = 0.5f, y1 = 0.5f, x2 = 0.5f, y2 = 0.5f, delayMs = 0, durationMs = 10),
                RecordedAction(x1 = 0.1f, y1 = 0.1f, x2 = 0.9f, y2 = 0.9f, delayMs = 0, durationMs = 20),
            ),
        )
        engine.startScript(script, speed = 1f, repeat = 1, screenWidth = 1000, screenHeight = 2000)
        awaitIdle(engine)
        assertEquals(1, sink.taps.size)
        assertEquals(1, sink.strokes.size)
        assertEquals(1, engine.state.value.cycles)
    }

    @Test
    fun profileSurvivesAJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val original = ClickerData(
            profile = profile(
                listOf(ClickPoint(1, 5, 6, pressMs = 700)),
                mode = ClickMode.RANDOM,
                repeat = 7,
            ),
            scripts = listOf(
                RecordedScript(1, "s", 0, listOf(RecordedAction(0.1f, 0.2f, 0.3f, 0.4f, 5, 30))),
            ),
        )
        val text = json.encodeToString(ClickerData.serializer(), original)
        val restored = json.decodeFromString(ClickerData.serializer(), text)
        assertEquals(original, restored)
    }
}
