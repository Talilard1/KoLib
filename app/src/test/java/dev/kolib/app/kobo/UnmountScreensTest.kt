package dev.kolib.app.kobo

import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnmountScreensTest {

    @Test
    fun theDrivesRootComesFromTheFolderKoLibWasGiven() {
        val top = Uri.parse("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A")
        assertEquals("content://com.android.externalstorage.documents/root/1A2B-3C4D", UnmountScreens.rootUri(top).toString())
        // A folder inside it gives the same drive.
        val inside = Uri.parse("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3ABooks%2FNew")
        assertEquals("content://com.android.externalstorage.documents/root/1A2B-3C4D", UnmountScreens.rootUri(inside).toString())
        assertNull(UnmountScreens.rootUri(Uri.parse("content://com.android.externalstorage.documents/document/1A2B-3C4D%3A")))
    }

    @Test
    fun settingsAndFilesAreAskedForTheDriveItself() {
        val root = UnmountScreens.rootUri(Uri.parse("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A"))!!
        val settings = UnmountScreens.settingsIntent(root)
        assertEquals("android.provider.action.DOCUMENT_ROOT_SETTINGS", settings.action)
        assertEquals(root, settings.data)
        assertEquals(DocumentsContract.Root.MIME_TYPE_ITEM, settings.type)
        val files = UnmountScreens.filesIntent(root)
        assertEquals("android.intent.action.VIEW", files.action)
        assertEquals(DocumentsContract.Root.MIME_TYPE_ITEM, files.type)
    }
}
