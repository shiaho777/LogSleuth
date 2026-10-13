package io.github.shiaho777.logsleuth.app.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.shiaho777.logsleuth.app.R

/** Copies a crash stack from the notification action. */
class CopyCrashReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("crash", text))
        Toast.makeText(context, context.getString(R.string.crash_copied), Toast.LENGTH_SHORT).show()
    }
}
