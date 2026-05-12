package com.mixer.xr18.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppExecutors {
    private val supervisorJob = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + supervisorJob)
}