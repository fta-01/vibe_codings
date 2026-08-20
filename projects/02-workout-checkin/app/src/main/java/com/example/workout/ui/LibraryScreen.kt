package com.example.workout.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.repository.CheckinRepository

@Composable
fun LibraryScreen(repo: CheckinRepository) {
    var exercises by remember { mutableStateOf<List<Exercise>?>(null) }
    var detail by remember { mutableStateOf<Exercise?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        exercises = repo.loadExercises()
    }

    val list = exercises
    if (list == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
        }
        return
    }

    val selected = detail
    if (selected != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { detail = null }) { Text("← 返回") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(selected.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(selected.summary)
                selected.reps?.let { Text("建议：$it", fontWeight = FontWeight.Bold) }
                selected.defaultSeconds?.let { Text("建议时长：$it 秒", fontWeight = FontWeight.Bold) }
                Text("步骤", fontWeight = FontWeight.Bold)
                selected.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step")
                }
                selected.note?.let { Text(it, color = Color(0xFF888888)) }
                selected.videoUrl?.let { url ->
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }) {
                        Text("观看视频教程")
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        list.groupBy { it.category }.forEach { (category, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                items.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { detail = ex }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.name, fontWeight = FontWeight.Bold)
                            Text(
                                ex.summary +
                                    if (ex.reps != null) " · ${ex.reps}" else "",
                                fontSize = 12.sp,
                                color = Color(0xFF666666),
                            )
                        }
                        Text("›", fontSize = 18.sp, color = Color(0xFF999999))
                    }
                }
            }
        }
    }
}
