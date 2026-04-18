package com.opelm.unilife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.opelm.unilife.navigation.UniLifeApp
import com.opelm.unilife.ui.theme.UniLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as UniLifeApplication).container.repository

        setContent {
            UniLifeTheme {
                Surface(modifier = Modifier) {
                    UniLifeApp(repository = repository)
                }
            }
        }
    }
}
