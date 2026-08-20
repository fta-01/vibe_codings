package com.example.workout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.workout.data.repository.CheckinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalendarUiState(
    val checkedDates: Set<String> = emptySet(),
    val month: String = LocalDate.now().toString().substring(0, 7),
)

class CalendarViewModel(private val repo: CheckinRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    fun loadMonth(month: String) {
        viewModelScope.launch {
            val recs = repo.recordsForMonth(month).first()
            _uiState.value = CalendarUiState(
                checkedDates = recs.map { it.date }.toSet(),
                month = month,
            )
        }
    }

    companion object {
        fun factory(repo: CheckinRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CalendarViewModel(repo) as T
        }
    }
}
