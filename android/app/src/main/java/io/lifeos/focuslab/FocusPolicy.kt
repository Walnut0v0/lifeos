package io.lifeos.focuslab

enum class Phase { IDLE, SCHEDULED, ACTIVE, FAULT }
data class Session(
    val start: Long, val end: Long, val manual: Boolean = false,
    val elapsedEnd: Long = 0, val boot: Int = -1, val duration: Long = end - start
)
object FocusPolicy {
    const val MAX_DURATION = 30 * 60_000L
    fun phase(s: Session?, wall: Long, elapsed: Long, boot: Int): Phase {
        if (s == null) return Phase.IDLE
        if (s.start <= 0 || s.end <= s.start || s.duration !in 1..MAX_DURATION || s.end - s.start != s.duration) return Phase.FAULT
        if (s.manual) {
            if (s.boot < 0 || s.boot != boot || s.elapsedEnd <= 0 || s.elapsedEnd - elapsed > s.duration) return Phase.FAULT
            return if (elapsed < s.elapsedEnd) Phase.ACTIVE else Phase.IDLE
        }
        return when { wall < s.start -> Phase.SCHEDULED; wall < s.end -> Phase.ACTIVE; else -> Phase.IDLE }
    }
    fun shouldBlock(active: Boolean, selected: Boolean, protected: Boolean, unlocked: Boolean) =
        active && selected && !protected && unlocked
}
