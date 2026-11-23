package com.teleprompter.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.MaterialTheme
import com.teleprompter.wear.ui.RemoteScreen
import com.teleprompter.wear.wear.WearDataLayerClient

class WearMainActivity : ComponentActivity() {
    private lateinit var client: WearDataLayerClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = WearDataLayerClient(this)
        setContent { MaterialTheme { RemoteScreen(client) } }
    }

    override fun onStart() {
        super.onStart()
        client.connect()
    }

    override fun onStop() {
        client.disconnect()
        super.onStop()
    }
}
