package com.vfxsal.filemanager.feature.files.timeline

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vfxsal.filemanager.data.FileCategory
import com.vfxsal.filemanager.data.FileEntry
import com.vfxsal.filemanager.feature.files.util.BackupOps
import com.vfxsal.filemanager.feature.files.util.FileOps
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnThisDayGroup(val yearsAgo: Int, val entries: List<FileEntry>)
data class MonthGroup(val label: String, val entries: List<FileEntry>)

data class TimelineUiState(
    val isLoading: Boolean = true,
    val onThisDay: List<OnThisDayGroup> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
)

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val state = withContext(Dispatchers.IO) { computeState() }
            _uiState.update { state }
        }
    }

    fun backup(treeUri: Uri, onResult: (Boolean) -> Unit) {
        val context = getApplication<Application>()
        val sources = _uiState.value.monthGroups.flatMap { it.entries }.distinctBy { it.path }.map { it.file }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                BackupOps.backupToTree(context, treeUri, sources, "TimelineBackup")
            }
            onResult(success)
        }
    }

    private fun computeState(): TimelineUiState {
        val root = Environment.getExternalStorageDirectory()
        val media = (FileOps.filesByCategory(root, FileCategory.IMAGES) + FileOps.filesByCategory(root, FileCategory.VIDEOS))
            .sortedByDescending { it.lastModified }

        val today = Calendar.getInstance()
        val todayMonthDay = today.get(Calendar.MONTH) to today.get(Calendar.DAY_OF_MONTH)
        val todayYear = today.get(Calendar.YEAR)

        val onThisDay = media
            .groupBy { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.lastModified }
                cal.get(Calendar.MONTH) to cal.get(Calendar.DAY_OF_MONTH) to cal.get(Calendar.YEAR)
            }
            .filterKeys { (monthDay, year) -> monthDay == todayMonthDay && year != todayYear }
            .map { (key, entries) -> OnThisDayGroup(yearsAgo = todayYear - key.second, entries = entries) }
            .sortedBy { it.yearsAgo }

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthGroups = media
            .groupBy { monthFormat.format(Date(it.lastModified)) }
            .entries
            .sortedByDescending { (_, entries) -> entries.maxOf { it.lastModified } }
            .map { (label, entries) -> MonthGroup(label, entries) }

        return TimelineUiState(isLoading = false, onThisDay = onThisDay, monthGroups = monthGroups)
    }
}
