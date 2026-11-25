package io.github.shiaho777.logsleuth.app.core.remoteadb

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteLogDataTest {
    @Test
    fun `a UTF-8 character can span ADB payloads`() {
        val lines = mutableListOf<String>()
        val remote = RemoteLogLines(lines::add)
        val data = "汉字🙂\r\nnext\nlast".toByteArray(Charsets.UTF_8)
        for (byte in data) remote.append(byteArrayOf(byte))
        remote.flush()
        assertEquals(listOf("汉字🙂", "next", "last"), lines)
    }

    @Test
    fun `package attribution requires a unique known package for the UID`() {
        val output = sequenceOf(
            "package:remote.first uid:10123",
            "package:remote.first uid:10123",
            "package:remote.shared.a uid:10234",
            "package:remote.shared.b uid:10234",
            "package:profile.app uid:1010234\r",
            "package:invalid uid:2147483648",
            "logsleuth-ok",
        )
        assertEquals(mapOf(10123 to "remote.first", 1010234 to "profile.app"), RemotePackages.parse(output))
    }
}
