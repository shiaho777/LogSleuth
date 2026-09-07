package io.github.logsleuth.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.logsleuth.sdk.Sleuth
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("SampleApp", "MainActivity created")
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    SampleScreen(
                        onWriteLogs = {
                            repeat(50) { i ->
                                Log.i("SampleApp", "test log line #$i (token=secret-$i should be redacted)")
                            }
                            Sleuth.log("SampleApp", "breadcrumb: wrote 50 test lines")
                        },
                        onCrash = {
                            thread {
                                throw RuntimeException("Sample crash from background thread")
                            }
                        },
                        onAnr = {
                            // Freeze the main thread so the watchdog fires.
                            Thread.sleep(8_000)
                        },
                        onShare = { Sleuth.shareLogs(this) },
                    )
                }
            }
        }
    }
}

@Composable
fun SampleScreen(
    onWriteLogs: () -> Unit,
    onCrash: () -> Unit,
    onAnr: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LogSleuth SDK sample", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Everything you trigger here is captured by the embedded SDK — " +
                "no permissions involved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        DemoButton("Write 50 log lines", onWriteLogs)
        DemoButton("Crash the app", onCrash)
        DemoButton("Freeze main thread (ANR)", onAnr)
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text("Share captured logs (zip)")
        }
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label)
    }
}
