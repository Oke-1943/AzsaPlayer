package io.github.oke1943.azsaplayer.tag

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class TagDumpTest {

    private fun encode(vararg fields: String): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            for (f in fields) {
                val b = f.toByteArray(Charsets.UTF_8)
                out.writeInt(b.size)
                out.write(b)
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun parsesMultipleValuesInOrder() {
        val bytes = encode(
            "PropertyMap", "ARTIST", "0", "米津玄師",
            "PropertyMap", "ARTIST", "1", "菅田将暉",
            "概要", "形式", "0", "FLAC",
        )
        val dump = TagDump.parse("/x.flac", bytes)

        assertEquals(3, dump.records.size)
        val artist = dump.sections.first { it.name == "PropertyMap" }.fields.single()
        assertEquals(listOf("米津玄師", "菅田将暉"), artist.values.map { it.value })
        assertEquals(listOf(0, 1), artist.values.map { it.index })
    }

    @Test
    fun handlesSupplementaryCharactersAndEmptyIndex() {
        val dump = TagDump.parse("/x", encode("PropertyMap未対応", "XYZ", "", "😀"))
        assertEquals(null, dump.records.single().index)
        assertEquals("😀", dump.records.single().value)
    }

    @Test
    fun formatValueLineShowsBoundariesAndControlChars() {
        val r = TagDumpRecord("s", "k", 1, " A\u0000B ")
        assertEquals("[1] ‹ A\\u0000B ›", TagDump.formatValueLine(r))
    }
}
