package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DayZTimeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HOD Pulse", appName)
    }

    @Test
    fun `test DayZ restart countdown within 3 hour window`() {
        val countdown = DayZTimeHelper.getRestartCountdownMs()
        assertTrue("Countdown should be >= 0", countdown >= 0)
        assertTrue("Countdown should be <= 3 hours", countdown <= 3L * 3600L * 1000L)
    }

    @Test
    fun `test parse DayZ etm keywords`() {
        val keywords = "battleye,no3rd,external,privHive,shardABC123,lqs0,etm8.000000,entm8.000000,all"
        val (time, phase) = DayZTimeHelper.parseInGameTime(keywords)
        assertEquals("08:00", time)
        assertEquals("Day", phase)
    }
}
