package com.kiskujab.arise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kiskujab.arise.ui.AriseRoot
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.theme.AriseTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AriseTheme {
                AriseRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
