package com.vfxsal.filemanager.feature.files.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Generic single-field text dialog used for both "New folder" and "Rename". Validation
 * runs on confirm rather than dismissing immediately so the error can be shown inline.
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String = "OK",
    validator: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                val validationError = validator(trimmed)
                if (validationError != null) {
                    error = validationError
                } else {
                    onConfirm(trimmed)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Delete this item?" else "Delete $count items?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Shared validation used by both New Folder and Rename dialogs. */
fun validateFileName(input: String, parent: File?, currentName: String): String? {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> "Name cannot be empty"
        trimmed.contains('/') -> "Name cannot contain '/'"
        trimmed != currentName && parent != null && File(parent, trimmed).exists() ->
            "An item with this name already exists"
        else -> null
    }
}
