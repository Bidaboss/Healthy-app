package com.bidaboss.healthy.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.settings.data.PousseStageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Dernière étape de Pousse vue : vide au premier lancement, puis gardée. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PousseStageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `vide au premier lancement puis etape gardee`() {
        assertNull(PousseStageStore(context).lastSeen())
        PousseStageStore(context).setLastSeen(Growth.SHRUB)
        assertEquals(Growth.SHRUB, PousseStageStore(context).lastSeen())
    }
}
