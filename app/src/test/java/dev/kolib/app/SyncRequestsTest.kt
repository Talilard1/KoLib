package dev.kolib.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Sending waits for the Kobo's database to be saved before it says "Sent": that wait must always end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncRequestsTest {

    @Test
    fun aWaitingSendIsLetGoWhenThereIsNoKoboToSaveTo() {
        val app = ApplicationProvider.getApplicationContext<KoLibApp>()
        MainViewModel(app)
        val saved = CompletableDeferred<Unit>()
        app.koboSyncRequests.tryEmit(saved)
        val main = shadowOf(Looper.getMainLooper())
        main.idleFor(Duration.ofMillis(100))
        // Requests close together make one save, so it waits a moment for more first.
        assertFalse(saved.isCompleted)
        main.idleFor(Duration.ofMillis(600))
        assertTrue(saved.isCompleted)
    }

    @Test
    fun requestsWithoutAWaitStillWork() {
        val app = ApplicationProvider.getApplicationContext<KoLibApp>()
        MainViewModel(app)
        val saved = CompletableDeferred<Unit>()
        app.koboSyncRequests.tryEmit(null)
        app.koboSyncRequests.tryEmit(saved)
        app.koboSyncRequests.tryEmit(null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(saved.isCompleted)
    }
}
