package dev.localant.android.approval

class TestClock(private var timeMs: Long) : Clock {
    override fun nowMs(): Long = timeMs

    fun advance(deltaMs: Long) {
        timeMs += deltaMs
    }

    fun setTime(newTimeMs: Long) {
        timeMs = newTimeMs
    }
}
