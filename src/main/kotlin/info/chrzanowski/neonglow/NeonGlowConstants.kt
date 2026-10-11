package info.chrzanowski.neonglow

import kotlin.time.Duration.Companion.days

object NeonGlowConstants {

    const val SPONSOR_GITHUB_URL = "https://github.com/sponsors/hsz"
    const val SPONSOR_KOFI_URL = "https://ko-fi.com/hszanowski"
    const val SPONSOR_PAYPAL_URL = "https://paypal.me/hsz"

    /**
     * Minimum number of project sessions with glow enabled, and the minimum time since the first of them,
     * before the sponsorship notification is shown for the first time.
     */
    const val SPONSOR_MIN_SESSIONS = 10
    val SPONSOR_MIN_USAGE = 14.days

    /** How long the sponsorship notification stays hidden after `Remind me later` or after being shown. */
    val SPONSOR_SNOOZE = 90.days
}
