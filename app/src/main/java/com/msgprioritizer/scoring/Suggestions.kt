package com.msgprioritizer.scoring

/** A rule the app proposes from what it has learned; the user accepts or dismisses it. */
data class Suggestion(val chat: String, val kind: Kind, val why: String) {
    enum class Kind { MAKE_VIP, MUTE }

    /** Stable key, so a dismissed suggestion never comes back. */
    val key get() = "${kind.name}:$chat"
}

/**
 * Turns learned behaviour into suggestions the user can read and approve, instead of silently
 * changing rankings. Pure Kotlin, so it can be unit tested on a laptop.
 */
object Suggestions {
    private const val MIN_EVENTS = 5
    private const val FAST_MS = 10 * 60_000L
    private const val MAX = 5

    fun from(
        learned: Map<String, ContactHistory>,
        vips: Set<String>,
        muted: Set<String>,
        dismissed: Set<String>,
    ): List<Suggestion> {
        fun Set<String>.has(name: String) = any { it.trim().equals(name.trim(), ignoreCase = true) }

        val out = mutableListOf<Pair<Int, Suggestion>>()
        for ((chat, h) in learned) {
            if (vips.has(chat) || muted.has(chat)) continue
            if (h.opens >= MIN_EVENTS && h.ignores * 5 <= h.opens && h.avgOpenMs <= FAST_MS) {
                val mins = maxOf(1, (h.avgOpenMs / 60_000).toInt())
                out += h.opens to Suggestion(chat, Suggestion.Kind.MAKE_VIP,
                    "You opened ${h.opens} of their messages, usually within $mins min.")
            } else if (h.ignores >= MIN_EVENTS && h.opens == 0) {
                out += h.ignores to Suggestion(chat, Suggestion.Kind.MUTE,
                    "You swiped away all ${h.ignores} of their notifications without opening one.")
            }
        }
        return out.filter { it.second.key !in dismissed }
            .sortedByDescending { it.first }
            .map { it.second }
            .take(MAX)
    }
}
