package io.lifeos.focuslab

/** A single current-app candidate, never a history. Gaps or wall-clock changes discard evidence. */
class ForegroundContinuity {
    private var state = ResumedAppState()
    private var wall = 0L
    private var elapsed = 0L
    val candidate: String? get() = state.candidate
    fun cached(nowElapsed: Long) = elapsed != 0L && nowElapsed >= elapsed && nowElapsed - elapsed < 1000
    fun begin(nowWall: Long, nowElapsed: Long): Long {
        if (wall == 0L || nowWall < wall || nowElapsed < elapsed || nowElapsed - elapsed > 5000 || nowWall - wall > 5000) {
            state = ResumedAppState(); wall = nowWall - 5000
        }
        return (wall - 500).coerceAtLeast(nowWall - 5500)
    }
    fun resumed(packageName: String) = state.resumed(packageName)
    fun paused(packageName: String) = state.paused(packageName)
    fun reset() { state = ResumedAppState(); wall = 0; elapsed = 0 }
    fun finish(nowWall: Long, nowElapsed: Long) { wall = nowWall; elapsed = nowElapsed }
}
