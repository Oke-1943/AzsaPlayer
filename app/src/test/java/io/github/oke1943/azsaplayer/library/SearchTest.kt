package io.github.oke1943.azsaplayer.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {

    @Test
    fun normalizesWidthCaseKanaAndSpaces() {
        assertEquals("ろくでなし", SearchText.normalize("ロクデナシ"))
        assertEquals("aimer", SearchText.normalize("ＡＩＭＥＲ"))
        assertEquals("earth,wind&fire", SearchText.normalize("Earth, Wind & Fire"))
        assertEquals("がっこう", SearchText.normalize("ｶﾞｯｺｳ"))
    }

    @Test
    fun splitsTermsOnAnyWhitespace() {
        assertEquals(listOf("aimer", "dawn"), SearchText.terms(" Aimer　DAWN "))
        assertEquals(emptyList<String>(), SearchText.terms("   "))
    }
}
