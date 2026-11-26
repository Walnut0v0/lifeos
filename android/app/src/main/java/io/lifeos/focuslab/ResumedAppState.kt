package io.lifeos.focuslab

/** Ephemeral foreground evidence; a pause invalidates that app even without a home-resume event. */
class ResumedAppState {
    var candidate: String? = null
        private set
    fun resumed(packageName: String) { candidate = packageName }
    fun paused(packageName: String) { if (candidate == packageName) candidate = null }
    fun isTarget(targets: Set<String>) = candidate != null && candidate in targets
}
