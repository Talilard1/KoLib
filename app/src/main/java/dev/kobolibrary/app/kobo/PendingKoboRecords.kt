package dev.kobolibrary.app.kobo

/**
 * Changes to the Kobo's records of books just sent again, waiting for the next write of its database:
 * each book's new file size (so the Kobo doesn't take the new file for a new book), and the usual name
 * for its cover pictures where the record had another.
 *
 * They belong to the connection the books were sent in. Any left over when the Kobo was unplugged were
 * never made, so the Kobo will have taken those books in as new ones, without their collections.
 */
class PendingKoboRecords {

    data class Pending(val bookId: String, val connection: Long, val columns: Map<String, Any?>)

    private val byContentId = LinkedHashMap<String, Pending>()

    @Synchronized
    fun put(contentId: String, pending: Pending) {
        byContentId[contentId] = pending
    }

    /** The changes waiting for the connection made at [connection] (its connectedAtMillis). */
    @Synchronized
    fun forConnection(connection: Long): Map<String, Pending> = byContentId.filterValues { it.connection == connection }

    /** Takes away the changes of other connections: they weren't made before the Kobo was unplugged. */
    @Synchronized
    fun takeStale(connection: Long): List<Pending> {
        val stale = byContentId.filterValues { it.connection != connection }
        stale.keys.forEach { byContentId.remove(it) }
        return stale.values.toList()
    }

    /** These were written to the Kobo (or can't be any more). */
    @Synchronized
    fun done(contentIds: Collection<String>) {
        contentIds.forEach { byContentId.remove(it) }
    }

    @get:Synchronized
    val isEmpty: Boolean get() = byContentId.isEmpty()
}
