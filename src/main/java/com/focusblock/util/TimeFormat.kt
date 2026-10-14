package com.focusblock.util

/** 754_000 ms -> "12:34", 3_723_000 ms -> "1:02:03". */
fun formatCountdown(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000 // round up so "0:00" only shows at the very end
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
