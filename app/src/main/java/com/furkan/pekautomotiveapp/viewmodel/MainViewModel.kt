package com.furkan.pekautomotiveapp.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val isConnected = MutableLiveData(false)
    var ipList = MutableLiveData<HashSet<String>>(hashSetOf())
    var refusedList = MutableLiveData<MutableList<String>>(mutableListOf())
}