package io.github.oke1943.azsaplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * 音楽ファイルを読むための権限（ver.6）。次のどちらかがあればよい。
 * - 音楽ファイルの読み取りのみ（おすすめ）：Android 12 は READ_EXTERNAL_STORAGE、13 以降は READ_MEDIA_AUDIO
 *   （フォルダのジャケット画像用に READ_MEDIA_IMAGES も求めるが、なくても動く）。
 *   Androidが音声として扱わない形式（APE・DSD など）や M3U ファイルは見えないことがある。
 * - すべてのファイルへのアクセス：MANAGE_EXTERNAL_STORAGE。あらゆる形式を読める。
 * どちらの場合もアプリは読み取りだけを行い、音楽ファイルには書き込まない。
 */
object StorageAccess {

    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun hasAllFiles(): Boolean = Environment.isExternalStorageManager()

    fun hasMediaRead(context: Context): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(needed) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAny(context: Context): Boolean = hasAllFiles() || hasMediaRead(context)

    /** 「すべてのファイルへのアクセス」の設定画面（許可も取り消しもここから）。 */
    fun allFilesSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** アプリ情報の画面（権限の取り消し用）。 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
