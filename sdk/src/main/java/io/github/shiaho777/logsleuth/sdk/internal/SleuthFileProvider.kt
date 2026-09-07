package io.github.shiaho777.logsleuth.sdk.internal

import android.content.Context
import androidx.core.content.FileProvider

/** FileProvider subclass so the SDK's provider authority never clashes with
 * the host app's own FileProvider. */
class SleuthFileProvider : FileProvider() {

    companion object {
        fun authority(context: Context): String =
            context.packageName + ".sleuth.fileprovider"
    }
}
