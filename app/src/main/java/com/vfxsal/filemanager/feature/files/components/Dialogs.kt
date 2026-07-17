package com.vfxsal.filemanager.feature.files.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vfxsal.filemanager.feature.files.tags.FileTag
import com.vfxsal.filemanager.feature.files.util.RenamePattern
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

/** [onConfirm] receives whether the user chose permanent deletion - the dialog previously always
 *  said "This action cannot be undone" while actually moving the file to the recycle bin, which
 *  was simply untrue and left no way to actually delete something outright. */
@Composable
fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: (permanent: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Delete this item?" else "Delete $count items?") },
        text = {
            Column {
                Text("Moved items stay in the Recycle Bin and can be restored later.")
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { onConfirm(true) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Delete permanently instead", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(false) }) { Text("Move to Recycle Bin") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Lets the user rename a multi-selection at once: a shared base name plus either a sequential
 *  number or each file's modified-date as a suffix, so names stay unique. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchRenameDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (baseName: String, pattern: RenamePattern) -> Unit,
) {
    var baseName by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(RenamePattern.SEQUENTIAL) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename $count items") },
        text = {
            Column {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = {
                        baseName = it
                        error = null
                    },
                    label = { Text("Base name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                    } else {
                        null
                    },
                )
                RenamePatternOption(
                    label = "Sequential number: Name (1), Name (2)…",
                    selected = pattern == RenamePattern.SEQUENTIAL,
                    onClick = { pattern = RenamePattern.SEQUENTIAL },
                )
                RenamePatternOption(
                    label = "Date modified: Name_20260704_153000",
                    selected = pattern == RenamePattern.DATE_MODIFIED,
                    onClick = { pattern = RenamePattern.DATE_MODIFIED },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = baseName.trim()
                if (trimmed.isEmpty()) {
                    error = "Name cannot be empty"
                } else if (trimmed.contains('/')) {
                    error = "Name cannot contain '/'"
                } else {
                    onConfirm(trimmed, pattern)
                }
            }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenamePatternOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Lets the user pick a color tag (or clear it) for one or more files at once. */
@Composable
fun TagPickerDialog(
    count: Int,
    currentTag: FileTag?,
    onDismiss: () -> Unit,
    onSelect: (FileTag?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Tag this item" else "Tag $count items") },
        text = {
            Column {
                TagOptionRow(label = "No tag", color = null, selected = currentTag == null, onClick = { onSelect(null) })
                FileTag.entries.forEach { tag ->
                    TagOptionRow(label = tag.label, color = tag.color, selected = currentTag == tag, onClick = { onSelect(tag) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun TagOptionRow(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color ?: Color.Transparent)
                .then(
                    if (color == null) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
    }
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
