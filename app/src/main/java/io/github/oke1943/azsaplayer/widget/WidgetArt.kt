package io.github.oke1943.azsaplayer.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** ウィジェット用の画像を描く（RemoteViewsでは複雑な描画ができないため、Bitmapに描いて渡す）。 */
object WidgetArt {

    /**
     * レコードプレーヤー。木目調の台座にレコードを載せ、ジャケットをレーベルとして中央に置く。
     * 再生中はトーンアームの針がレコードに載り、停止中はアームが外側のレストに戻る。
     */
    fun turntable(art: Bitmap?, playing: Boolean, accent: Int, size: Int = 480): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size / 480f
        c.scale(s, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 台座（木目調のグラデーションと細い筋）
        val plinth = RectF(0f, 0f, 480f, 480f)
        p.shader = LinearGradient(0f, 0f, 480f, 480f, Color.rgb(78, 52, 46), Color.rgb(33, 20, 16), Shader.TileMode.CLAMP)
        c.drawRoundRect(plinth, 40f, 40f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(28, 255, 220, 180)
        for (i in 0 until 14) {
            val y = 30f + i * 33f
            val path = Path().apply {
                moveTo(10f, y)
                cubicTo(160f, y - 10f, 320f, y + 12f, 470f, y - 4f)
            }
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL

        // プラッター（金属の縁）
        val cx = 205f
        val cy = 250f
        p.shader = RadialGradient(cx, cy, 182f, intArrayOf(Color.rgb(90, 90, 90), Color.rgb(40, 40, 40)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 182f, p)
        p.shader = null

        // レコード本体と溝
        p.color = Color.rgb(14, 14, 14)
        c.drawCircle(cx, cy, 172f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        var r = 66f
        var i = 0
        while (r < 168f) {
            p.color = if (i % 3 == 0) Color.rgb(40, 40, 40) else Color.rgb(26, 26, 26)
            c.drawCircle(cx, cy, r, p)
            r += 4.2f
            i++
        }
        p.style = Paint.Style.FILL
        // 光沢（向かい合う2本の反射）
        p.shader = SweepGradient(
            cx, cy,
            intArrayOf(0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x1AFFFFFF, 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.08f, 0.16f, 0.5f, 0.58f, 0.66f, 1f),
        )
        c.drawCircle(cx, cy, 170f, p)
        p.shader = null

        // レーベル（ジャケット、なければ強調色）
        val labelR = 60f
        if (art != null) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = (labelR * 2) / minOf(art.width, art.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - art.width * scale / 2, cy - art.height * scale / 2)
            }
            shader.setLocalMatrix(m)
            p.shader = shader
            c.drawCircle(cx, cy, labelR, p)
            p.shader = null
        } else {
            p.color = accent
            c.drawCircle(cx, cy, labelR, p)
            p.color = Color.argb(70, 255, 255, 255)
            c.drawCircle(cx, cy, labelR * 0.62f, p)
        }
        // スピンドル
        p.color = Color.rgb(210, 210, 210)
        c.drawCircle(cx, cy, 6f, p)

        // トーンアーム（支点の右上から。再生中は針がレコードに載る）
        val px = 425f
        val py = 72f
        val angle = if (playing) 26f else 8f
        val rad = Math.toRadians(angle.toDouble())
        val len = 290f
        val ex = px - (len * sin(rad)).toFloat()
        val ey = py + (len * cos(rad)).toFloat()
        // 支点の台
        p.color = Color.rgb(60, 60, 60)
        c.drawCircle(px, py, 34f, p)
        p.color = Color.rgb(170, 170, 170)
        c.drawCircle(px, py, 22f, p)
        p.color = Color.rgb(110, 110, 110)
        c.drawCircle(px, py, 10f, p)
        // アームのレスト
        p.color = Color.rgb(70, 70, 70)
        c.drawRoundRect(RectF(398f, 330f, 422f, 372f), 6f, 6f, p)
        // アームの影と本体
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 12f
        p.color = Color.argb(90, 0, 0, 0)
        c.drawLine(px + 6f, py + 8f, ex + 6f, ey + 8f, p)
        p.strokeWidth = 9f
        p.color = Color.rgb(205, 205, 205)
        c.drawLine(px, py, ex, ey, p)
        p.style = Paint.Style.FILL
        // ヘッドシェル
        c.save()
        c.rotate(-angle, ex, ey)
        p.color = Color.rgb(40, 40, 40)
        c.drawRoundRect(RectF(ex - 14f, ey - 6f, ex + 14f, ey + 30f), 5f, 5f, p)
        p.color = accent
        c.drawRect(RectF(ex - 14f, ey + 22f, ex + 14f, ey + 26f), p)
        c.restore()
        // カウンターウェイト
        val bx = px + (40 * sin(rad)).toFloat()
        val by = py - (40 * cos(rad)).toFloat()
        p.color = Color.rgb(140, 140, 140)
        c.drawCircle(bx, by, 16f, p)

        // 回転数の表示（33 / 45）と電源ランプ
        p.color = if (playing) accent else Color.rgb(90, 70, 60)
        c.drawCircle(445f, 445f, 7f, p)
        return bmp
    }

    /** 角を丸めたジャケット（小さいウィジェット用）。 */
    fun rounded(art: Bitmap, size: Int, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = size.toFloat() / minOf(art.width, art.height)
        shader.setLocalMatrix(Matrix().apply {
            setScale(scale, scale)
            postTranslate((size - art.width * scale) / 2, (size - art.height * scale) / 2)
        })
        p.shader = shader
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, p)
        return out
    }

    /** ジャケットの下側を暗くして文字を読みやすくした正方形（ジャケットウィジェット用）。 */
    fun cover(art: Bitmap?, accent: Int, size: Int = 480): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (art != null) {
            val scale = max(size.toFloat() / art.width, size.toFloat() / art.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate((size - art.width * scale) / 2, (size - art.height * scale) / 2)
            }
            c.drawBitmap(art, m, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), accent, Color.rgb(20, 20, 30), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
            p.shader = null
        }
        p.shader = LinearGradient(0f, size * 0.45f, 0f, size.toFloat(), Color.TRANSPARENT, Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        return out
    }
}
