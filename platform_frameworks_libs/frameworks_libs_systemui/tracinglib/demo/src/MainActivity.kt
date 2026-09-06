/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.tracing.demo

import android.os.Bundle
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.android.app.tracing.coroutines.launchTraced as launch
import com.example.tracing.demo.experiments.Experiment
import com.example.tracing.demo.experiments.TRACK_NAME
import com.example.tracing.demo.ui.theme.BasicsCodelabTheme
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job

val AllExperiments = compositionLocalOf<List<Experiment>> { error("No Experiments found!") }

val Experiment = compositionLocalOf<Experiment> { error("No found!") }

val ExperimentLaunchDispatcher =
    compositionLocalOf<CoroutineDispatcher> {
        error("No @ExperimentLauncher CoroutineDispatcher found!")
    }

class MainActivity(private val appComponent: ApplicationComponent) : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BasicsCodelabTheme {
                CompositionLocalProvider(
                    AllExperiments provides appComponent.getExperimentList(),
                    ExperimentLaunchDispatcher provides
                        appComponent.getExperimentDefaultCoroutineDispatcher(),
                ) {
                    DemoApp(modifier = Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

@Composable
fun DemoApp(modifier: Modifier = Modifier) {
    Surface(modifier) { ExperimentList() }
}

@Composable
private fun ExperimentList(modifier: Modifier = Modifier) {
    val allExperiments = AllExperiments.current

    LazyColumn(modifier = modifier.padding(vertical = 4.dp)) {
        items(items = allExperiments.stream().toList()) { experiment ->
            CompositionLocalProvider(Experiment provides experiment) { ExperimentCard() }
        }
    }
}

@Composable
private fun ExperimentCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        ExperimentContentRow()
    }
}

@Composable
private fun ExperimentContentRow(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .padding(12.dp)
                .animateContentSize(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        )
                )
    ) {
        ExperimentContent()
    }
}

@Composable
private fun RowScope.ExperimentContent(modifier: Modifier = Modifier) {
    val experiment = Experiment.current
    val launcherDispatcher = ExperimentLaunchDispatcher.current
    val scope = rememberCoroutineScope { launcherDispatcher + experiment.context }

    var isRunning by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val statusMessages = remember { mutableStateListOf<String>() }
    val className = experiment.javaClass.simpleName

    Column(modifier = modifier.weight(1f).padding(12.dp)) {
        Text(text = experiment.javaClass.simpleName, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = experiment.description,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
        )
        if (expanded) {
            Text(
                text = statusMessages.joinToString(separator = "\n") { it },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }

    IconButton(
        onClick = {
            if (isRunning) {
                scope.coroutineContext.job.cancelChildren()
            } else {
                val cookie = Random.nextInt()
                Trace.asyncTraceForTrackBegin(
                    Trace.TRACE_TAG_APP,
                    TRACK_NAME,
                    "Running: $className",
                    cookie,
                )
                statusMessages += "Started"
                expanded = true
                isRunning = true
                scope
                    .launch("$className#runExperiment") { experiment.runExperiment() }
                    .invokeOnCompletion { cause ->
                        Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_APP, TRACK_NAME, cookie)
                        isRunning = false
                        statusMessages +=
                            when (cause) {
                                null -> "completed normally"
                                is CancellationException -> "cancelled normally: ${cause.message}"
                                else -> "failed"
                            }
                    }
            }
        }
    ) {
        Icon(
            imageVector = if (isRunning) Filled.StopCircle else Filled.PlayCircleOutline,
            contentDescription = stringResource(R.string.run_experiment),
        )
    }
    IconButton(
        onClick = {
            expanded = !expanded
            if (!expanded) statusMessages.clear()
        },
        enabled = !expanded || !isRunning,
    ) {
        Icon(
            imageVector = if (expanded) Filled.ExpandLess else Filled.ExpandMore,
            contentDescription =
                if (expanded) stringResource(R.string.show_less)
                else stringResource(R.string.show_more),
        )
    }
}
