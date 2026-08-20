package com.example.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.workout.data.db.AppDatabase
import com.example.workout.data.repository.CheckinRepository
import com.example.workout.ui.CalendarScreen
import com.example.workout.ui.HomeScreen
import com.example.workout.ui.LibraryScreen
import com.example.workout.ui.theme.WorkoutTheme
import com.example.workout.viewmodel.CalendarViewModel
import com.example.workout.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        val repo = CheckinRepository(db.recordDao(), this)

        setContent {
            WorkoutTheme {
                MainScaffold(repo)
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(repo: CheckinRepository) {
    var tab by remember { mutableIntStateOf(0) }
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(repo),
    )
    val calendarViewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.factory(repo),
    )

    val tabs = listOf(
        TabItem("打卡", Icons.Filled.Check),
        TabItem("运动库", Icons.Filled.MenuBook),
        TabItem("日历", Icons.Filled.DateRange),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(homeViewModel)
                1 -> LibraryScreen(repo)
                else -> CalendarScreen(calendarViewModel)
            }
        }
    }
}
