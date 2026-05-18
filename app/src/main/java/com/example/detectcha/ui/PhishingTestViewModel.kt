package com.example.detectcha.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.detectcha.PhishingModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhishingTestViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = PhishingModelManager(application)
    
    private val _testResult = MutableStateFlow<PhishingModelManager.DetailedAnalysisResult?>(null)
    val testResult: StateFlow<PhishingModelManager.DetailedAnalysisResult?> = _testResult.asStateFlow()

    fun runTest(text: String) {
        viewModelScope.launch {
            _testResult.value = modelManager.classifyDetailed(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelManager.close()
    }
}
