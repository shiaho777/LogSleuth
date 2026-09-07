package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryAssemblerTest {

    @Test
    fun `multiline messages are glued onto the previous entry`() {
        val assembler = EntryAssembler()
        assertEquals(0, assembler.onLine("06-15 11:59:58.123  1  1 E AndroidRuntime: FATAL EXCEPTION: main").size)

        // Stack frames are continuations — nothing emitted yet.
        assertEquals(0, assembler.onLine("    at com.example.Foo.bar(Foo.kt:12)").size)
        assertEquals(0, assembler.onLine("    at com.example.Main.onCreate(Main.kt:40)").size)

        // Next entry flushes the assembled one.
        val flushed = assembler.onLine("06-15 11:59:58.999  2  2 D OtherTag: next")
        assertEquals(1, flushed.size)
        assertEquals(
            "FATAL EXCEPTION: main\n    at com.example.Foo.bar(Foo.kt:12)\n    at com.example.Main.onCreate(Main.kt:40)",
            flushed[0].message,
        )
    }

    @Test
    fun `flush emits the pending entry`() {
        val assembler = EntryAssembler()
        assembler.onLine("06-15 11:59:58.123  1  1 D Tag: first")
        val flushed = assembler.flush()
        assertEquals("first", flushed?.message)
        assertEquals(null, assembler.flush())
    }

    @Test
    fun `leading continuation lines are dropped`() {
        val assembler = EntryAssembler()
        assertEquals(0, assembler.onLine("    at something.without.entry").size)
        val emitted = assembler.onLine("06-15 11:59:58.123  1  1 D Tag: real")
        assertEquals(0, emitted.size) // still pending
        assertEquals("real", assembler.flush()?.message)
    }
}
