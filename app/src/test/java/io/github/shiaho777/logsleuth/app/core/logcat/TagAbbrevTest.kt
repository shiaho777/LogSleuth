package io.github.shiaho777.logsleuth.app.core.logcat

import org.junit.Assert.assertEquals
import org.junit.Test

class TagAbbrevTest {

    @Test
    fun `keeps the tail of reverse-domain tags`() {
        assertEquals("ugc.aweme", abbreviateTag("com.ss.android.ugc.aweme"))
        assertEquals("android.gms", abbreviateTag("com.google.android.gms"))
        assertEquals("tencent.mm", abbreviateTag("com.tencent.mm"))
    }

    @Test
    fun `plain tags stay unchanged`() {
        assertEquals("ActivityManager", abbreviateTag("ActivityManager"))
        assertEquals("chromium", abbreviateTag("chromium"))
        assertEquals("my.custom.tag", abbreviateTag("my.custom.tag"))
    }

    @Test
    fun `two-segment tags stay unchanged`() {
        assertEquals("com.example", abbreviateTag("com.example"))
    }

    @Test
    fun `malformed dotted strings stay unchanged`() {
        assertEquals(".hidden.tag", abbreviateTag(".hidden.tag"))
        assertEquals("com..x", abbreviateTag("com..x"))
        assertEquals("com.x.", abbreviateTag("com.x."))
    }

    @Test
    fun `long tails are capped`() {
        val tag = "com.example.averylongsegment.anotherverylongsegment"
        val abbreviated = abbreviateTag(tag)
        assertEquals(ABBREV_TAG_MAX + 1, abbreviated.length)
        assertEquals("averylongsegment.ano…", abbreviated)
    }

    @Test
    fun `tail at the cap is not truncated`() {
        val tail = "a".repeat(9) + "." + "b".repeat(10)
        assertEquals(tail, abbreviateTag("com.x.$tail"))
    }
}
