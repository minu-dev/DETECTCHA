package com.example.detectcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhishingTestScreen(
    onBack: () -> Unit,
    viewModel: PhishingTestViewModel = viewModel()
) {
    var inputText by remember { mutableStateOf("") }
    val testResult by viewModel.testResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("후처리 로직 테스트") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("테스트 문장 입력", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Cyan,
                    unfocusedBorderColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.runTest(inputText) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan)
            ) {
                Text("분석 실행", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            testResult?.let { result ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    item {
                        Text("--- 분석 결과 ---", color = Color.Cyan, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        ResultSection("최종 판정", if (result.finalDecision) "🚨 피싱 의심" else "✅ 정상")
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("위험 지표", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        Text("Primary Risk Prob: ${String.format("%.2f", result.primaryRiskProb)}", color = Color.White)
                        Text("Directive Risk Prob: ${String.format("%.2f", result.directiveRiskProb)}", color = Color.White)
                        Text("Signal Count: ${result.signalCount}", color = Color.White)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Top-3 결과", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        result.top3.forEachIndexed { index, pair ->
                            Text("${index + 1}: ${pair.first} (${String.format("%.1f", pair.second * 100)}%)", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("패턴 감지", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        result.patterns.forEach { (name, count) ->
                            Text("$name: $count", color = if (count > 0) Color.Red else Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("전체 분포 (Softmax)", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        result.fullDistribution.sortedByDescending { it.second }.forEach { pair ->
                            if (pair.second > 0.01f) {
                                Text("${pair.first}: ${String.format("%.1f", pair.second * 100)}%", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultSection(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
