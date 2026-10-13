package com.repforth.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repforth.core.body.BodyRepository
import com.repforth.core.model.MeasurementKind
import com.repforth.core.model.MeasurementType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A custom type about to be deleted, with how many entries go with it. */
data class PendingDelete(val type: MeasurementType, val entries: Int)

data class TrackingUiState(val types: List<MeasurementType>, val pendingDelete: PendingDelete? = null)

/** Why a custom type's name or unit was refused. */
enum class CustomProblem { NAME_BLANK, NAME_TOO_LONG, UNIT_MISSING, UNIT_TOO_LONG }

internal const val CUSTOM_NAME_MAX = 40
internal const val CUSTOM_UNIT_MAX = 10

/** The name as it will be stored, or why it cannot be. */
internal fun checkCustomName(name: String): CustomProblem? = when {
    name.isBlank() -> CustomProblem.NAME_BLANK
    name.trim().length > CUSTOM_NAME_MAX -> CustomProblem.NAME_TOO_LONG
    else -> null
}

/** A unit label matters only for a plain number; the others take theirs from the unit setting or are %. */
internal fun checkCustomUnit(kind: MeasurementKind, unitLabel: String?): CustomProblem? = when {
    kind != MeasurementKind.PLAIN -> null
    unitLabel.isNullOrBlank() -> CustomProblem.UNIT_MISSING
    unitLabel.trim().length > CUSTOM_UNIT_MAX -> CustomProblem.UNIT_TOO_LONG
    else -> null
}

/**
 * Which types appear on a check-in, and the user's own. Each action checks its
 * input here and returns why it refused, so a dialog can stay open and say so.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(private val body: BodyRepository) : ViewModel() {

    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)

    /** Null until the first read. */
    val uiState: StateFlow<TrackingUiState?> =
        combine(body.observeTypes(), pendingDelete) { types, pending -> TrackingUiState(types, pending) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onToggle(typeId: String, tracked: Boolean) {
        viewModelScope.launch { body.setTracked(typeId, tracked) }
    }

    fun onAdd(name: String, kind: MeasurementKind, unitLabel: String?): CustomProblem? {
        val problem = checkCustomName(name) ?: checkCustomUnit(kind, unitLabel)
        if (problem == null) {
            val unit = unitLabel?.trim()?.takeIf { kind == MeasurementKind.PLAIN }
            viewModelScope.launch { body.addCustom(name.trim(), kind, unit) }
        }
        return problem
    }

    fun onRename(typeId: String, name: String): CustomProblem? {
        val problem = checkCustomName(name)
        if (problem == null) viewModelScope.launch { body.rename(typeId, name.trim()) }
        return problem
    }

    fun onAskDelete(typeId: String) {
        viewModelScope.launch {
            // From the repository, not uiState: nothing need be collecting it yet.
            val type = body.observeTypes().first().firstOrNull { it.id == typeId && it.builtIn == null }
                ?: return@launch
            pendingDelete.value = PendingDelete(type, body.entryCount(typeId))
        }
    }

    fun onConfirmDelete() {
        val pending = pendingDelete.value ?: return
        viewModelScope.launch {
            body.deleteCustom(pending.type.id)
            pendingDelete.value = null
        }
    }

    fun onCancelDelete() {
        pendingDelete.value = null
    }
}
