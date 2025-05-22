package com.darblee.livingword

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CustomSnackBar(
    visible: Boolean,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val animationDuration = 150

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(animationDuration)),
            exit = fadeOut(tween(animationDuration))
        ) {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.wrapContentSize(),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!actionLabel.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onAction()
                                onDismiss()
                            },
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(text = actionLabel)
                        }
                    }
                }
            }
        }
    }

    // Auto dismiss after 1 second (1000ms)
    LaunchedEffect(visible) {
        if (visible) {
            delay(1000) // Half second duration
            onDismiss()
        }
    }
}

@Composable
fun CustomSnackBarHost(
    snackBarEvent: SnackBarEvent?,
    onDismiss: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var actionLabel by remember { mutableStateOf<String?>(null) }
    var currentEvent by remember { mutableStateOf<SnackBarEvent?>(null) }

    LaunchedEffect(snackBarEvent) {
        if (snackBarEvent != null) {
            message = snackBarEvent.message
            actionLabel = snackBarEvent.action?.name
            currentEvent = snackBarEvent
            visible = true
        }
    }

    CustomSnackBar(
        visible = visible,
        message = message,
        actionLabel = actionLabel,
        onAction = {
            currentEvent?.action?.let { action ->
                // Launch a coroutine to handle the suspend function
                kotlinx.coroutines.MainScope().launch {
                    action.action.invoke()
                }
            }
        },
        onDismiss = {
            visible = false
            onDismiss()
        }
    )
}