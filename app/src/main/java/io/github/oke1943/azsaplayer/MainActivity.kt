package io.github.oke1943.azsaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.oke1943.azsaplayer.ui.AppRoot

class MainActivity : ComponentActivity() {
    /** アプリ内で選んだ表示言語を反映する（Android 12にはアプリごとの言語設定がないため）。 */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }

    override fun onStop() {
        super.onStop()
        // 設定で選んだアプリアイコンは、画面から離れたときに切り替える（AppIcons）
        if (!isChangingConfigurations) AppIcons.applyPending(this)
    }
}
