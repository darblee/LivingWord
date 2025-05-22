package com.darblee.livingword

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class SnackBarEvent(
    val message: String,
    val action: SnackBarAction? = null
)

data class SnackBarAction(
    val name: String,
    val action: suspend () -> Unit
)

object SnackBarController {

    private val _events = Channel<SnackBarEvent>()
    val events = _events.receiveAsFlow()

    suspend fun sendEvent(event: SnackBarEvent) {
        _events.send(event)
    }

    /**
     * Helper function to show a simple message without action
     */
    suspend fun showMessage(message: String) {
        sendEvent(SnackBarEvent(message = message))
    }

    /**
     * Helper function to show a message with action
     */
    suspend fun showActionMessage(message: String, actionName: String, action: suspend () -> Unit) {
        sendEvent(
            SnackBarEvent(
                message = message,
                action = SnackBarAction(name = actionName, action = action)
            )
        )
    }
}