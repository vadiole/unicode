package vadiole.unicode.ui.common

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringTest {

    private class TestSpring(
        stiffness: Float,
        dampingRatio: Float,
        minVisibleChange: Float = 1f,
        value: Float = 0f,
    ) : Spring(stiffness, dampingRatio, minVisibleChange, value, null, null) {
        var pending = false
        var posts = 0
        val updates = ArrayList<Float>()
        val ends = ArrayList<Boolean>()

        init {
            onUpdate = UpdateListener { updates += it }
            onEnd = EndListener { ends += it }
        }

        override fun postFrame() {
            assertFalse("frame posted twice", pending)
            pending = true
            posts++
        }

        fun deliver(frameTimeNanos: Long, durationScale: Float = 1f): Boolean {
            if (!pending) return false
            pending = false
            frame(frameTimeNanos, durationScale)
            return true
        }

        fun runToEnd(frameNanos: Long, durationScale: Float = 1f, start: Long = 0L, limit: Int = 100_000): Int {
            var t = start
            var frames = 0
            while (deliver(t, durationScale)) {
                t += frameNanos
                frames++
                check(frames < limit) { "spring did not settle" }
            }
            return frames
        }
    }

    private fun frameTime(index: Int, hz: Double): Long = (index * 1e9 / hz).roundToLong()

    @Test
    fun followsDampedHarmonicMotion() {
        val critical = TestSpring(400f, 1f, 0.0001f, value = 0f).apply { target = 1f }
        critical.advance(0.05)
        val w = 20.0
        assertEquals(1 - (1 + w * 0.05) * exp(-w * 0.05), critical.value.toDouble(), 1e-6)

        val bouncy = TestSpring(400f, 0.3f, 0.0001f, value = 0f).apply { target = 1f }
        var peak = 0f
        repeat(2000) {
            bouncy.advance(0.0005)
            peak = maxOf(peak, bouncy.value)
        }
        val expectedOvershoot = exp(-0.3 * PI / sqrt(1 - 0.09))
        assertEquals(expectedOvershoot, (peak - 1f).toDouble(), 1e-3)

        for (damping in floatArrayOf(1f, 1.5f, 3f)) {
            val calm = TestSpring(400f, damping, 0.0001f, value = 0f).apply { target = 1f }
            var previous = 0f
            repeat(2000) {
                calm.advance(0.0005)
                assertTrue("damping $damping overshoots", calm.value <= 1f)
                assertTrue("damping $damping goes back", calm.value >= previous)
                previous = calm.value
            }
        }
    }

    @Test
    fun slowAnimationScaleAdvancesEveryFrameAt120Hz() {
        val spring = TestSpring(600f, 1f, 0.001f, value = 0f)
        val reference = TestSpring(600f, 1f, 0.001f, value = 0f).apply { target = 1f }
        spring.animateTo(1f)
        spring.deliver(frameTime(0, 120.0), 10f)
        var previous = spring.value
        for (i in 1..144) {
            spring.deliver(frameTime(i, 120.0), 10f)
            assertTrue("stalled at frame $i", spring.value > previous)
            previous = spring.value
        }
        reference.advance(1.2 / 10)
        assertEquals(reference.value, spring.value, 1e-5f)
        assertEquals(reference.velocity, spring.velocity, 1e-3f)
    }

    @Test
    fun fractionalDurationScaleDoesNotDrift() {
        for (scale in floatArrayOf(0.5f, 1.5f, 3f, 7.3f)) {
            val spring = TestSpring(1500f, 0.65f, 0.0001f, value = 0f)
            val reference = TestSpring(1500f, 0.65f, 0.0001f, value = 0f).apply { target = 1f }
            spring.animateTo(1f)
            var t = 0L
            var i = 0
            while (t < 400_000_000L) {
                spring.deliver(t, scale)
                t += if (i++ % 2 == 0) 11_111_111L else 11_111_112L
            }
            spring.deliver(t, scale)
            reference.advance(t / 1e9 / scale)
            assertEquals("scale $scale", reference.value, spring.value, 1e-4f)
        }
    }

    @Test
    fun sameWallTimeGivesSameStateAtAnyFrameRate() {
        val values = doubleArrayOf(60.0, 90.0, 120.0, 144.0).map { hz ->
            val spring = TestSpring(700f, 0.35f, 0.0001f, value = 0f).apply { velocity = 5f }
            spring.animateTo(1f)
            for (i in 0..(hz / 2).toInt()) spring.deliver(frameTime(i, hz))
            spring.value
        }
        for (value in values) assertEquals(values[0], value, 1e-5f)
    }

    @Test
    fun zeroDurationScaleFinishesOnFirstFrame() {
        val spring = TestSpring(600f, 1f, value = 3f)
        spring.animateTo(40f)
        spring.deliver(5_000_000L, 0f)
        assertEquals(listOf(40f), spring.updates)
        assertEquals(listOf(false), spring.ends)
        assertFalse(spring.isRunning)
        assertFalse(spring.pending)
    }

    @Test
    fun firstFrameEmitsStartValueWithoutMoving() {
        val spring = TestSpring(600f, 1f, value = 10f)
        spring.animateTo(100f)
        assertTrue(spring.isRunning)
        assertTrue(spring.pending)
        assertTrue(spring.updates.isEmpty())
        spring.deliver(123_456_789L)
        assertEquals(listOf(10f), spring.updates)
        spring.deliver(123_456_789L + 8_333_333L)
        assertTrue(spring.value > 10f)
    }

    @Test
    fun naturalEndSnapsToTargetOnce() {
        val spring = TestSpring(1500f, 0.65f, value = 0f)
        spring.animateTo(100f)
        spring.runToEnd(16_666_667L)
        assertEquals(100f, spring.value)
        assertEquals(0f, spring.velocity)
        assertEquals(100f, spring.updates.last())
        assertEquals(listOf(false), spring.ends)
        assertFalse(spring.isRunning)
        assertFalse(spring.pending)
    }

    @Test
    fun cancelStopsInPlaceOnce() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.cancel()
        assertTrue(spring.ends.isEmpty())
        spring.animateTo(100f)
        spring.deliver(0L)
        spring.deliver(16_000_000L)
        val stoppedAt = spring.value
        spring.cancel()
        spring.cancel()
        assertEquals(listOf(true), spring.ends)
        assertEquals(stoppedAt, spring.value)
        assertEquals(0f, spring.velocity)
        assertEquals(100f, spring.target)
        assertFalse(spring.isRunning)
        val updates = spring.updates.size
        spring.deliver(32_000_000L)
        assertEquals(updates, spring.updates.size)
        assertFalse(spring.pending)
    }

    @Test
    fun restartAfterCancelReusesThePostedFrame() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.animateTo(100f)
        spring.deliver(0L)
        spring.deliver(16_000_000L)
        val from = spring.value
        val posts = spring.posts
        spring.cancel()
        spring.animateTo(0f)
        assertEquals(posts, spring.posts)
        spring.deliver(32_000_000L)
        assertEquals(from, spring.updates.last())
        assertTrue(spring.pending)
        spring.deliver(48_000_000L)
        assertTrue(spring.value < from)
    }

    @Test
    fun parametersChangeWhileRunning() {
        val spring = TestSpring(600f, 1f, 0.0005f, value = 0f)
        spring.animateTo(0.8f)
        for (i in 0..4) spring.deliver(i * 8_000_000L)
        val value = spring.value
        val velocity = spring.velocity
        spring.stiffness = 900f
        spring.dampingRatio = 0.85f
        spring.value = value
        spring.animateTo(1f)
        assertEquals(velocity, spring.velocity)
        val reference = TestSpring(900f, 0.85f, 0.0005f, value = value).apply {
            this.velocity = velocity
            target = 1f
            advance(0.008)
        }
        spring.deliver(5 * 8_000_000L)
        assertEquals(reference.value, spring.value, 1e-6f)
        assertEquals(reference.velocity, spring.velocity, 1e-4f)
        spring.runToEnd(8_000_000L, start = 6 * 8_000_000L)
        assertEquals(1f, spring.value)
        assertEquals(listOf(false), spring.ends)
        assertTrue(spring.updates.max() > 1f)
    }

    @Test
    fun retargetWhileRunningKeepsVelocity() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.animateTo(100f)
        for (i in 0..5) spring.deliver(i * 16_000_000L)
        val velocity = spring.velocity
        val value = spring.value
        val posts = spring.posts
        assertTrue(velocity > 0f)
        spring.animateTo(-50f)
        assertEquals(velocity, spring.velocity)
        assertEquals(value, spring.value)
        assertTrue(spring.isRunning)
        assertTrue(spring.pending)
        assertEquals(posts, spring.posts)
        spring.deliver(5 * 16_000_000L + 2_000_000L)
        assertTrue("momentum lost", spring.value > value)
        spring.runToEnd(16_000_000L, start = 6 * 16_000_000L + 2_000_000L)
        assertEquals(-50f, spring.value)
        assertEquals(listOf(false), spring.ends)
    }

    @Test
    fun repeatedAnimateToPostsOneFrame() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.animateTo(1f)
        spring.animateTo(2f)
        spring.animateTo(3f)
        assertEquals(1, spring.posts)
    }

    @Test
    fun startVelocityIsHonored() {
        val spring = TestSpring(700f, 0.35f, 0.001f, value = 1f)
        spring.velocity = 6f
        spring.animateTo(1f)
        spring.runToEnd(8_333_333L)
        assertTrue(spring.updates.max() > 1.1f)
        assertEquals(1f, spring.value)
        assertEquals(listOf(false), spring.ends)
    }

    @Test
    fun valueSetWhileIdleIsTheStart() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.value = 50f
        spring.animateTo(100f)
        spring.deliver(0L)
        assertEquals(50f, spring.updates.single())
    }

    @Test
    fun restartAfterCancelStartsFromRest() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.animateTo(100f)
        for (i in 0..5) spring.deliver(i * 16_000_000L)
        spring.cancel()
        val from = spring.value
        spring.animateTo(from)
        spring.runToEnd(16_000_000L, start = 10 * 16_000_000L)
        for (value in spring.updates.takeLast(spring.updates.size - 6)) assertEquals(from, value)
    }

    @Test
    fun cancelFromUpdateListener() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.onUpdate = Spring.UpdateListener {
            spring.updates += it
            if (it > 50f) spring.cancel()
        }
        spring.animateTo(100f)
        spring.runToEnd(16_000_000L)
        assertTrue(spring.updates.last() > 50f)
        assertEquals(1, spring.updates.count { it > 50f })
        assertEquals(listOf(true), spring.ends)
        assertFalse(spring.isRunning)
    }

    @Test
    fun restartFromUpdateListenerStartsANewRun() {
        val spring = TestSpring(140f, 0.85f, 0.001f, value = 0f)
        var landings = 0
        spring.onUpdate = Spring.UpdateListener {
            spring.updates += it
            if (it >= 0.9f && landings++ == 0) {
                spring.cancel()
                spring.value = 0f
                spring.animateTo(1f)
            }
        }
        spring.animateTo(1f)
        spring.runToEnd(16_666_667L)
        assertEquals(listOf(true, false), spring.ends)
        val restart = spring.updates.indexOfFirst { it >= 0.9f } + 1
        assertEquals(0f, spring.updates[restart])
        assertTrue(spring.updates[restart + 1] > 0f)
    }

    @Test
    fun restartFromEndListener() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.onEnd = Spring.EndListener {
            spring.ends += it
            if (spring.ends.size == 1) spring.animateTo(0f)
        }
        spring.animateTo(10f)
        spring.runToEnd(16_000_000L)
        assertEquals(listOf(false, false), spring.ends)
        assertEquals(0f, spring.value)
    }

    @Test
    fun retargetFromFinalUpdateKeepsRunning() {
        val spring = TestSpring(600f, 1f, value = 0f)
        spring.onUpdate = Spring.UpdateListener {
            spring.updates += it
            if (it == 10f && spring.target == 10f) spring.animateTo(20f)
        }
        spring.animateTo(10f)
        spring.runToEnd(16_000_000L)
        assertEquals(listOf(false), spring.ends)
        assertEquals(20f, spring.value)
    }

    @Test
    fun clampsToBounds() {
        val spring = TestSpring(1500f, 0.3f, value = 0f)
        spring.min = -10f
        spring.max = 10f
        spring.velocity = 2000f
        spring.animateTo(0f)
        spring.runToEnd(8_000_000L)
        assertTrue(spring.updates.all { it in -10f..10f })
        assertTrue(spring.updates.any { it == 10f })
        assertEquals(0f, spring.value)
    }

    @Test
    fun settleThresholdFollowsMinimumVisibleChange() {
        val coarse = TestSpring(600f, 1f, 1f, value = 0f).apply { animateTo(100f) }.runToEnd(16_000_000L)
        val fine = TestSpring(600f, 1f, 0.001f, value = 0f).apply { animateTo(100f) }.runToEnd(16_000_000L)
        assertTrue("$fine vs $coarse", fine > coarse)
        val spring = TestSpring(600f, 1f, 1f, value = 0f)
        spring.animateTo(100f)
        var t = 0L
        while (spring.isRunning) {
            spring.deliver(t)
            if (spring.isRunning) assertTrue(abs(spring.value - 100f) >= 0.75f || abs(spring.velocity) >= 0.75f * 62.5f)
            t += 16_000_000L
        }
    }

    @Test
    fun parametersChangeBetweenRuns() {
        val spring = TestSpring(70f, 0.85f, 0.001f, value = 0f)
        val soft = spring.apply { animateTo(1f) }.runToEnd(16_000_000L)
        spring.stiffness = 1000f
        spring.value = 0f
        val stiff = spring.apply { animateTo(1f) }.runToEnd(16_000_000L)
        assertTrue("$stiff vs $soft", stiff < soft)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveStiffness() {
        TestSpring(0f, 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDamping() {
        TestSpring(100f, 0f)
    }
}
