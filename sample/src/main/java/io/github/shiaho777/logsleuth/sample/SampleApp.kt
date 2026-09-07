package io.github.shiaho777.logsleuth.sample

import android.app.Application
import android.util.Log
import android.widget.Toast
import io.github.shiaho777.logsleuth.sdk.Sleuth
import io.github.shiaho777.logsleuth.sdk.SleuthConfig

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Sleuth.init(
            this,
            SleuthConfig.Builder()
                .maxFiles(3)
                .maxFileBytes(512 * 1024)
                .addRedaction(Regex("token=\\S+"))
                .build(),
        )

        // The crash happened last run: notify the user / upload it here.
        Sleuth.onCrash { report ->
            Log.i("SampleApp", "Previous run crashed: ${report.exceptionClass}")
            Toast.makeText(
                this,
                "Recovered from a crash: ${report.exceptionClass}",
                Toast.LENGTH_LONG,
            ).show()
        }

        Log.i("SampleApp", "App started — every log line is recorded by the SDK")
    }
}
