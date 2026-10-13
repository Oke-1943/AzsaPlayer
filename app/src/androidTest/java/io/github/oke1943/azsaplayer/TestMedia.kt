package io.github.oke1943.azsaplayer

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * テスト用の音声ファイルを生成する（受け入れテスト T1〜T6 用、9章）。
 * 実ファイルの形式をMusicBeeの書き方に合わせて手で組み立てる。
 */
object TestMedia {

    // ---- MP3（ID3v2.3 / v2.4 ＋ 無音のMPEGフレーム） ----

    /** ID3v2.3：TPE1などの複数値はUTF-16のNULL区切り（MusicBeeと同じ）。 */
    fun mp3v23(file: File, frames: List<Pair<String, List<String>>>, txxx: Map<String, String> = emptyMap()) {
        val body = ByteArrayOutputStream()
        for ((id, values) in frames) {
            val data = byteArrayOf(1) + values.map { utf16(it) }.reduce { a, b -> a + byteArrayOf(0, 0) + b }
            body.write(id.toByteArray(Charsets.ISO_8859_1) + int32(data.size) + byteArrayOf(0, 0) + data)
        }
        for ((desc, value) in txxx) {
            val data = byteArrayOf(1) + utf16(desc) + byteArrayOf(0, 0) + utf16(value)
            body.write("TXXX".toByteArray() + int32(data.size) + byteArrayOf(0, 0) + data)
        }
        file.writeBytes(id3Header(3, body.size()) + body.toByteArray() + mpegFrames())
    }

    /** ID3v2.4：UTF-8のNULL区切り。フレームの大きさは同期安全整数。 */
    fun mp3v24(file: File, frames: List<Pair<String, List<String>>>) {
        val body = ByteArrayOutputStream()
        for ((id, values) in frames) {
            val data = byteArrayOf(3) + values.joinToString("\u0000").toByteArray(Charsets.UTF_8)
            body.write(id.toByteArray(Charsets.ISO_8859_1) + syncsafe(data.size) + byteArrayOf(0, 0) + data)
        }
        file.writeBytes(id3Header(4, body.size()) + body.toByteArray() + mpegFrames())
    }

    private fun id3Header(version: Int, size: Int) = "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + syncsafe(size)

    private fun mpegFrames(): ByteArray {
        // MPEG1 Layer III 128kbps 44.1kHz の無音フレーム（417バイト）を20個
        val frame = ByteArray(417).also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte(); it[2] = 0x90.toByte(); it[3] = 0x00 }
        return ByteArray(417 * 20).also { out -> repeat(20) { System.arraycopy(frame, 0, out, it * 417, 417) } }
    }

    // ---- FLAC（同名フィールドを複数個） ----

    fun flac(file: File, comments: List<String>) {
        val si = ByteBuffer.allocate(34).order(ByteOrder.BIG_ENDIAN)
        si.putShort(4096).putShort(4096)
        si.put(byteArrayOf(0, 0, 0, 0, 0, 0))
        val packed = (44100L shl 44) or (1L shl 41) or (15L shl 36)
        si.putLong(packed)
        si.put(ByteArray(16))
        val vendor = "musicapp-test".toByteArray()
        val vc = ByteArrayOutputStream()
        vc.write(le32(vendor.size)); vc.write(vendor); vc.write(le32(comments.size))
        for (c in comments) {
            val b = c.toByteArray(Charsets.UTF_8)
            vc.write(le32(b.size)); vc.write(b)
        }
        fun block(type: Int, data: ByteArray, last: Boolean) =
            byteArrayOf(((if (last) 0x80 else 0) or type).toByte()) + int24(data.size) + data
        file.writeBytes("fLaC".toByteArray() + block(0, si.array(), false) + block(4, vc.toByteArray(), true))
    }

    // ---- M4A（MusicBee形式：同名アトムの繰り返し） ----

    fun m4a(file: File, items: List<Pair<String, List<String>>>, freeform: Map<String, String> = emptyMap()) {
        val ilst = ByteArrayOutputStream()
        for ((name, values) in items) for (v in values) ilst.write(atom(name, dataAtom(v)))
        for ((name, v) in freeform) {
            ilst.write(
                atom(
                    "----",
                    atom("mean", int32(0) + "com.apple.iTunes".toByteArray()) + atom("name", int32(0) + name.toByteArray()) + dataAtom(v),
                ),
            )
        }
        val hdlr = atom("hdlr", int32(0) + int32(0) + "mdirappl".toByteArray() + int32(0) + int32(0) + byteArrayOf(0))
        val meta = atom("meta", int32(0) + hdlr + atom("ilst", ilst.toByteArray()))
        val ftyp = atom("ftyp", "M4A ".toByteArray() + int32(0) + "M4A mp42isom".toByteArray())
        file.writeBytes(ftyp + atom("moov", atom("udta", meta)))
    }

    private fun dataAtom(text: String) = atom("data", int32(1) + int32(0) + text.toByteArray(Charsets.UTF_8))

    private fun atom(type: String, body: ByteArray) = int32(body.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + body

    // ---- WAV（ギャップレス確認用：一定振幅の信号） ----

    /** 16ビット・ステレオ・44.1kHz、全サンプルが [value] の信号を [frames] フレーム。 */
    fun wavConstant(file: File, frames: Int, value: Short) {
        val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames * 2) { data.putShort(value) }
        val fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1).putShort(2).putInt(44100).putInt(44100 * 4).putShort(4).putShort(16).array()
        val chunks = "fmt ".toByteArray() + le32(16) + fmt + "data".toByteArray() + le32(frames * 4) + data.array()
        file.writeBytes("RIFF".toByteArray() + le32(4 + chunks.size) + "WAVE".toByteArray() + chunks)
    }

    // ---- バイト列の補助 ----

    private fun utf16(s: String) = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE)
    private fun int32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()
    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun int24(v: Int) = byteArrayOf((v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun syncsafe(v: Int) = byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())
}
