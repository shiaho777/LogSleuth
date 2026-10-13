package io.github.shiaho777.logsleuth.app.core.crashes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Surrounding-log files, one per stored crash, under the app's files dir. */
@Singleton
class CrashContextStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "crash-context").apply { mkdirs() }

    fun write(eventId: Long, text: String): String? {
        if (text.isBlank()) return null
        val file = File(dir, "$eventId.log")
        file.writeText(text)
        return file.absolutePath
    }

    fun read(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
