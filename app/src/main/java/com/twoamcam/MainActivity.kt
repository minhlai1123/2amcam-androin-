package com.twoamcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.twoamcam.presentation.camera.CameraScreen
import com.twoamcam.presentation.camera.CameraViewModel

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraScreen(viewModel = cameraViewModel)
                }
            }
        }
    }
}
