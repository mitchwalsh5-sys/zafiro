package com.niki914.zafiro.app.ui.content

/**
 * Parses a batched Shizuku shell read of /proc/meminfo, /proc/vmstat and /proc/loadavg.
 * Swap usage is not necessarily ZRAM usage unless the active swap devices are verified.
 * vmstat swap counters are cumulative pages since boot, not bytes or rates.
 */
internal data class MemoryDetailSnapshot(
    val swapUsedKb: Long,
    val swapTotalKb: Long,
    val swapInKb: Long,
    val swapOutKb: Long,
    val majorFaults: Long,
    val load1m: Float,
    val load5m: Float,
    val load15m: Float,
    val runningTasks: Int,
    val totalTasks: Int,
    val sampledAtMs: Long,
)

internal object MemoryDetailParser {
    fun parse(
        output: String,
        sampledAtMs: Long = System.currentTimeMillis(),
        pageSizeKb: Long = 4L,
    ): MemoryDetailSnapshot? {
        if (pageSizeKb <= 0) return null
        val sections = output.split("__VM__", "__LOAD__")
        if (sections.size != 3) return null
        val swapTotal = sections[0].kbValue("SwapTotal") ?: return null
        val swapFree = sections[0].kbValue("SwapFree") ?: return null
        if (swapTotal < 0 || swapFree < 0 || swapFree > swapTotal) return null
        val swapInPages = sections[1].longValue("pswpin") ?: return null
        val swapOutPages = sections[1].longValue("pswpout") ?: return null
        val majorFaults = sections[1].longValue("pgmajfault") ?: return null
        if (swapInPages < 0 || swapOutPages < 0 || majorFaults < 0) return null
        val swapInKb = try { Math.multiplyExact(swapInPages, pageSizeKb) } catch (_: ArithmeticException) { return null }
        val swapOutKb = try { Math.multiplyExact(swapOutPages, pageSizeKb) } catch (_: ArithmeticException) { return null }

        val load = sections[2].trim().split(Regex("""\s+"""))
        if (load.size < 4) return null
        val load1 = load[0].toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val load5 = load[1].toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val load15 = load[2].toFloatOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val tasks = load[3].split("/")
        if (tasks.size != 2) return null
        val running = tasks[0].toIntOrNull() ?: return null
        val total = tasks[1].toIntOrNull() ?: return null
        if (running < 0 || total < 0 || running > total) return null

        return MemoryDetailSnapshot(
            swapUsedKb = swapTotal - swapFree,
            swapTotalKb = swapTotal,
            swapInKb = swapInKb,
            swapOutKb = swapOutKb,
            majorFaults = majorFaults,
            load1m = load1,
            load5m = load5,
            load15m = load15,
            runningTasks = running,
            totalTasks = total,
            sampledAtMs = sampledAtMs,
        )
    }

    private fun String.kbValue(key: String): Long? =
        lineSequence()
            .firstOrNull { it.trim().startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()

    private fun String.longValue(key: String): Long? =
        lineSequence()
            .firstOrNull { it.trim().startsWith("$key ") }
            ?.trim()
            ?.substringAfter(' ')
            ?.trim()
            ?.toLongOrNull()
}
