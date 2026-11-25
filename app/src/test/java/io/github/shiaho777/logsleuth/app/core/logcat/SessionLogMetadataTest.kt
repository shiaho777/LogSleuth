package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogMetadataTest {
    @Test
    fun `remote attribution and gaps survive the text session format`() {
        val metadata = SessionLogMetadata()
        metadata.consume("# access: REMOTE_ADB")
        SessionLogMetadata.sourceHeader("host:5555", mapOf(10123 to "remote.app"))
            .trimEnd().lineSequence().forEach(metadata::consume)
        metadata.consume("captured log entry")
        metadata.consume(SessionLogMetadata.gap(2L))
        metadata.consume("# LogSleuth package: 10123 other.app")
        metadata.consume(SessionLogMetadata.packageLine(10234, "recorded.later"))
        metadata.consume(SessionLogMetadata.gap(-2L))
        metadata.consume(SessionLogMetadata.gap(4L))
        metadata.consume(SessionLogMetadata.SOURCE_CHANGED)
        assertEquals("host:5555", metadata.remoteEndpoint)
        assertEquals(mapOf(10123 to "remote.app", 10234 to "recorded.later"), metadata.remotePackages)
        assertEquals(6L, metadata.missedEntries)
        assertTrue(metadata.sourceChanged)
    }

    @Test
    fun `legacy remote sessions have no local package assignments`() {
        val metadata = SessionLogMetadata()
        metadata.consume("# access: REMOTE_ADB")
        assertEquals("", metadata.remoteEndpoint)
        assertTrue(metadata.remotePackages.isEmpty())
    }
}
