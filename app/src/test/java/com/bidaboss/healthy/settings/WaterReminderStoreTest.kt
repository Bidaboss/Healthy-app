package com.bidaboss.healthy.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.settings.data.WaterReminderStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rappels pour boire : désactivés par défaut, le choix est gardé. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WaterReminderStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `desactives par defaut puis choix garde`() {
        assertFalse(WaterReminderStore(context).enabled.value)
        WaterReminderStore(context).setEnabled(true)
        assertTrue(WaterReminderStore(context).enabled.value)
    }
}
