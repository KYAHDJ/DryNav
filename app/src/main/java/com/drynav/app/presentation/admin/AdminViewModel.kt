package com.drynav.app.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drynav.app.data.auth.AuthRepository
import com.drynav.app.domain.model.FloodReport
import com.drynav.app.domain.repository.FloodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val isAdmin: Boolean = false,
    val reports: List<FloodReport> = emptyList(),
    val isDeletingAll: Boolean = false,
    val message: String? = null,
    /** Area filter — city first, barangay scoped to whichever city is picked. */
    val selectedCity: String? = null,
    val selectedBarangay: String? = null
) {
    /** Pending first (needs a decision), then newest-first within each group. */
    val sortedReports: List<FloodReport>
        get() = reports.sortedWith(compareBy({ !it.isPending }, { -it.timestamp }))

    val availableCities: List<String>
        get() = reports.mapNotNull { it.areaCity() }.distinct().sorted()

    val availableBarangays: List<String>
        get() = reports
            .filter { selectedCity == null || it.areaCity() == selectedCity }
            .mapNotNull { it.areaBarangay() }
            .distinct()
            .sorted()

    /** [sortedReports] narrowed by the current city/barangay selection. */
    val filteredReports: List<FloodReport>
        get() = sortedReports.filter { report ->
            (selectedCity == null || report.areaCity() == selectedCity) &&
                (selectedBarangay == null || report.areaBarangay() == selectedBarangay)
        }
}

/** [FloodReport.areaLabel] is "Barangay, City" (or just "City") — city is the last segment. */
fun FloodReport.areaCity(): String? =
    areaLabel.takeIf { it.isNotBlank() }?.substringAfterLast(", ")

fun FloodReport.areaBarangay(): String? {
    val label = areaLabel.takeIf { it.isNotBlank() } ?: return null
    val idx = label.lastIndexOf(", ")
    return if (idx >= 0) label.substring(0, idx) else null
}

/**
 * Moderation queue for the single hardcoded admin account. Every non-admin
 * flood submission lands here as [FloodReport.STATUS_PENDING] and has zero
 * effect on the public map or routing until approved — this is the gate that
 * keeps a bad-faith "flood" claim from blocking everyone else's route.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val floodRepository: FloodRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState(isAdmin = authRepository.isAdmin))
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        if (authRepository.isAdmin) observeAllReports()
    }

    private fun observeAllReports() {
        viewModelScope.launch {
            floodRepository.getLiveFloodReports()
                .catch { e ->
                    _uiState.update { it.copy(message = "Couldn't load reports: ${e.message}") }
                }
                .collect { reports ->
                    _uiState.update { it.copy(reports = reports) }
                }
        }
    }

    fun approve(report: FloodReport) {
        viewModelScope.launch {
            floodRepository.setReportStatus(report.id, FloodReport.STATUS_APPROVED)
                .onSuccess {
                    _uiState.update { it.copy(message = "Approved — now live on the map.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "Approve failed: ${e.message}") }
                }
        }
    }

    /** Rejecting a pending report just removes it outright. */
    fun reject(report: FloodReport) {
        viewModelScope.launch {
            floodRepository.deleteReport(report)
                .onSuccess { _uiState.update { it.copy(message = "Report rejected and removed.") } }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "Reject failed: ${e.message}") }
                }
        }
    }

    /** Admin can pull down any flood mark, approved or not. */
    fun deleteReport(report: FloodReport) {
        viewModelScope.launch {
            floodRepository.deleteReport(report)
                .onSuccess { _uiState.update { it.copy(message = "Flood mark deleted.") } }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "Delete failed: ${e.message}") }
                }
        }
    }

    /** Requires the exact confirmation phrase — this wipes every flood report. */
    fun deleteAll(typedConfirmation: String) {
        if (typedConfirmation != CONFIRMATION_PHRASE) {
            _uiState.update { it.copy(message = "Type the phrase exactly to confirm.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAll = true) }
            floodRepository.deleteAllReports()
                .onSuccess {
                    _uiState.update {
                        it.copy(isDeletingAll = false, message = "All flood reports deleted.")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isDeletingAll = false, message = "Delete all failed: ${e.message}")
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Picking a new city always clears any barangay pick from the old one. */
    fun setCityFilter(city: String?) =
        _uiState.update { it.copy(selectedCity = city, selectedBarangay = null) }

    fun setBarangayFilter(barangay: String?) =
        _uiState.update { it.copy(selectedBarangay = barangay) }

    companion object {
        const val CONFIRMATION_PHRASE = "DELETE ALL FLOOD REPORTS"
    }
}
