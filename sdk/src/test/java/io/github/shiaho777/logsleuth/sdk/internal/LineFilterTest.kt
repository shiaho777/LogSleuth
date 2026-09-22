package io.github.shiaho777.logsleuth.sdk.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineFilterTest {

    private fun line(level: Char = 'I', tag: String = "MyTag", msg: String = "hello") =
        "01-15 10:30:45.123 1234 5678 $level $tag: $msg"

    @Test
    fun `no filter accepts everything`() {
        val f = LineFilter(null)
        assertTrue(f.accepts(line(tag = "Anything")))
        assertTrue(f.accepts("not a logcat line"))
    }

    @Test
    fun `tag filter matches the tag field`() {
        val f = LineFilter("MyTag")
        assertTrue(f.accepts(line(tag = "MyTag")))
        assertTrue(f.accepts(line(tag = "MyTagSub")))
        assertFalse(f.accepts(line(tag = "Other")))
    }

    @Test
    fun `tag filter does not match message substrings`() {
        val f = LineFilter("MyTag")
        // The message mentions the filter text but the tag doesn't.
        assertFalse(f.accepts(line(tag = "Other", msg = "talks about MyTag here")))
    }

    @Test
    fun `errors always pass the filter`() {
        val f = LineFilter("MyTag")
        assertTrue(f.accepts(line(level = 'E', tag = "AndroidRuntime")))
        assertTrue(f.accepts(line(level = 'F', tag = "Whatever")))
    }

    @Test
    fun `continuation lines follow the previous entry's verdict`() {
        val f = LineFilter("MyTag")
        assertFalse(f.accepts("    at com.example.Foo.bar(Foo.java:1)")) // nothing accepted yet
        assertTrue(f.accepts(line(tag = "MyTag")))
        assertTrue(f.accepts("    at com.example.Foo.bar(Foo.java:1)")) // glued continuation kept
        assertFalse(f.accepts(line(tag = "Other")))
        assertFalse(f.accepts("    continuation of a rejected entry"))
    }

    @Test
    fun `continuation latch survives error entries`() {
        val f = LineFilter("MyTag")
        assertTrue(f.accepts(line(level = 'E', tag = "AndroidRuntime")))
        assertTrue(f.accepts("    at foo.Bar.x(Bar.java:9)"))
        assertFalse(f.accepts(line(level = 'D', tag = "Noise")))
    }
}
