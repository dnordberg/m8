package com.m8droid.academy.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EmulatorEventRepository {

    private val _snapshots = MutableSharedFlow<EmulatorSnapshot>(replay = 1)
    val snapshots: SharedFlow<EmulatorSnapshot> = _snapshots.asSharedFlow()

    fun emit(snapshot: EmulatorSnapshot) {
        _snapshots.tryEmit(snapshot)
    }
}
