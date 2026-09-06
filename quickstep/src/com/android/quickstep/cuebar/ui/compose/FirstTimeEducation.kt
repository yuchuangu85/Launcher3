/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.cuebar.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.R
import com.android.quickstep.cuebar.ui.compose.modifier.eduBalloon

@Composable
fun FirstTimeEducation(
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceBright
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.eduBalloon(backgroundColor, horizontalAlignment),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ambientcue_edu),
            contentDescription =
                stringResource(R.string.ambientcue_first_time_edu_icon_description),
            modifier = Modifier.size(56.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 204.dp),
        ) {
            Text(
                text = stringResource(R.string.ambientcue_first_time_edu_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.ambientcue_first_time_edu_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCloseClick, modifier = modifier.size(24.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_close_white),
                contentDescription =
                    stringResource(id = R.string.underlay_close_button_content_description),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
