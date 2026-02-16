package com.example.vasganchalenge1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.vasganchalenge1.ui.MainScreen
import com.example.vasganchalenge1.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val vm = hiltViewModel<MainViewModel>()
                val state = vm.state.collectAsState().value
                MainScreen(
                    state = state,
                    onInputChange = vm::onInputChange,
                    onSendClick = vm::onSendClick
                )
            }
        }
    }
}