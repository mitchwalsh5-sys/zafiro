package com.niki914.zafiro.app.ui.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryDetailParserTest {
    private val sample = """
        SwapTotal:       6291452 kB
        SwapFree:        3678544 kB
        __VM__
        pswpin 22537470
        pswpout 44217850
        pgmajfault 23837143
        __LOAD__
        4.84 4.61 5.49 9/6434 22432
    """.trimIndent()

    @Test fun parsesSwap() {
        val x = MemoryDetailParser.parse(sample, 123L)
        assertNotNull(x)
        assertEquals(2612908L, x!!.swapUsedKb)
        assertEquals(6291452L, x.swapTotalKb)
        assertEquals(123L, x.sampledAtMs)
    }

    @Test fun convertsPagesToKiB() {
        val x = MemoryDetailParser.parse(sample, pageSizeKb = 4L)!!
        assertEquals(22537470L * 4L, x.swapInKb)
        assertEquals(44217850L * 4L, x.swapOutKb)
        assertEquals(23837143L, x.majorFaults)
    }

    @Test fun supportsOtherPageSizes() {
        assertEquals(22537470L * 16L, MemoryDetailParser.parse(sample, pageSizeKb = 16L)!!.swapInKb)
    }

    @Test fun parsesLoadAndRunnableTasks() {
        val x = MemoryDetailParser.parse(sample)!!
        assertEquals(4.84f, x.load1m, 0.001f)
        assertEquals(4.61f, x.load5m, 0.001f)
        assertEquals(5.49f, x.load15m, 0.001f)
        assertEquals(9, x.runningTasks)
        assertEquals(6434, x.totalTasks)
    }

    @Test fun rejectsMissingData() {
        assertNull(MemoryDetailParser.parse(sample.replace("pswpin 22537470", "")))
        assertNull(MemoryDetailParser.parse(sample.replace("SwapTotal:       6291452 kB", "")))
    }

    @Test fun rejectsMalformedValues() {
        assertNull(MemoryDetailParser.parse("garbage"))
        assertNull(MemoryDetailParser.parse(""))
        assertNull(MemoryDetailParser.parse(sample.replace("4.84", "NaN")))
        assertNull(MemoryDetailParser.parse(sample.replace("9/6434", "oops")))
    }

    @Test fun rejectsInvalidRanges() {
        assertNull(MemoryDetailParser.parse(sample.replace("3678544", "9999999")))
        assertNull(MemoryDetailParser.parse(sample, pageSizeKb = 0L))
    }

    @Test fun rejectsOverflow() {
        assertNull(MemoryDetailParser.parse(sample, pageSizeKb = Long.MAX_VALUE))
    }
}
