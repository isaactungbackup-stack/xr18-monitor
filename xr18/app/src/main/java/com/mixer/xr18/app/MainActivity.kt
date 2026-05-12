package com.mixer.xr18.app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

/**
 * Minimal Android Activity that hosts [Xr18TestApp].
 * The actual logic is in [Xr18TestApp] — this just provides the Android lifecycle.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created — starting XR18 Test App")

        // Execute the full discovery + query flow
        Xr18TestApp(scope).執行()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
