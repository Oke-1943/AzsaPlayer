package io.github.oke1943.azsaplayer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * アプリアイコンの選択（ver.7）。マニフェストのactivity-aliasを1つだけ有効にして切り替える。
 * どのエイリアスも同じMainActivityを開くので、設定やデータには影響しない。
 *
 * エイリアスを無効にするとシステムがアプリの画面（タスク）を閉じてしまうため、選んだ時点では保存だけしておき、
 * アプリが画面から離れたとき（MainActivity.onStop）に切り替える。プロセスは終了しないので再生は続く。
 */
object AppIcons {

    data class Option(val alias: String, @StringRes val label: Int, @DrawableRes val icon: Int)

    val OPTIONS = listOf(
        Option(".LauncherDefault", R.string.icon_default, R.mipmap.ic_launcher),
        Option(".LauncherMonogram", R.string.icon_monogram, R.mipmap.ic_launcher_monogram),
        Option(".LauncherRecord", R.string.icon_record, R.mipmap.ic_launcher_record),
        Option(".LauncherWave", R.string.icon_wave, R.mipmap.ic_launcher_wave),
    )

    private const val PREFS = "app_icon"
    private const val KEY_PENDING = "pending"

    private fun component(context: Context, alias: String) = ComponentName(context.packageName, context.packageName + alias)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pending(context: Context): Option? =
        prefs(context).getString(KEY_PENDING, null)?.let { a -> OPTIONS.firstOrNull { it.alias == a } }

    /** 今のアイコン（切り替え待ちがあればそれ）。 */
    fun current(context: Context): Option = pending(context) ?: enabled(context)

    private fun enabled(context: Context): Option {
        val pm = context.packageManager
        return OPTIONS.firstOrNull { o ->
            when (pm.getComponentEnabledSetting(component(context, o.alias))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // 既定の状態ではマニフェストの指定（標準のみ有効）に従う
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> o.alias == ".LauncherDefault"
                else -> false
            }
        } ?: OPTIONS.first()
    }

    /** 選んだアイコンを覚えておく（反映はアプリを離れたとき）。 */
    fun choose(context: Context, option: Option) {
        prefs(context).edit().putString(KEY_PENDING, option.alias).apply()
    }

    /** 切り替え待ちのアイコンがあれば反映する。アプリが画面から離れたときに呼ぶ。 */
    fun applyPending(context: Context) {
        val option = pending(context) ?: return
        prefs(context).edit().remove(KEY_PENDING).apply()
        if (option == enabled(context)) return
        val pm = context.packageManager
        // 先に新しい方を有効にしてから古い方を無効にする（ランチャーから一瞬でも消えないように）
        pm.setComponentEnabledSetting(
            component(context, option.alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        for (o in OPTIONS) {
            if (o.alias == option.alias) continue
            pm.setComponentEnabledSetting(
                component(context, o.alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
            )
        }
    }
}
