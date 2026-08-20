package com.example.workout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.repository.CheckinRepository
import com.example.workout.domain.Streak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val streakDays: Int = 0,
    val todayChecked: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val message: String? = null,
    /** 打卡成功后置 true，UI 弹出成功反馈对话框 */
    val showSuccessDialog: Boolean = false,
    /** 本次打卡的描述（如「全身唤醒组合 · 5 分钟」），显示在成功对话框中 */
    val lastCheckInLabel: String = "",
    /** 本次打卡时长（分钟） */
    val lastCheckInMinutes: Int = 0,
)

class HomeViewModel(private val repo: CheckinRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val today: LocalDate = LocalDate.now()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val dates = repo.allDates().first()
            val exercises = repo.loadExercises()
            val todayStr = today.toString()
            val todayChecked = dates.contains(todayStr)
            val checkedSet = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            val days = Streak.currentStreak(checkedSet, today)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                streakDays = days,
                todayChecked = todayChecked,
                exercises = exercises,
            )
        }
    }

    /**
     * 打卡。exerciseName 为展示用名称（单运动名或「xx组合」）。
     * 成功后触发 showSuccessDialog，由 UI 层弹出成功反馈。
     */
    fun checkIn(exerciseName: String, durationMinutes: Int) {
        viewModelScope.launch {
            val date = today.toString()
            val ok = repo.checkIn(date, exerciseName, durationMinutes)
            if (ok) {
                val dates = repo.allDates().first()
                val checkedSet = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
                val days = Streak.currentStreak(checkedSet, today)
                _uiState.value = _uiState.value.copy(
                    todayChecked = true,
                    streakDays = days,
                    message = null,
                    showSuccessDialog = true,
                    lastCheckInLabel = exerciseName,
                    lastCheckInMinutes = durationMinutes,
                )
            } else {
                _uiState.value = _uiState.value.copy(message = "该日期已打卡")
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(showSuccessDialog = false)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        fun factory(repo: CheckinRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(repo) as T
        }
    }
}
