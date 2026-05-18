package com.example.detectcha.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.detectcha.data.AppDatabase
import com.example.detectcha.data.PhishingHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhishingHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).phishingHistoryDao()

    val historyList: StateFlow<List<PhishingHistory>> = dao.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}
