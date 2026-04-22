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

        val container = (application as UniLifeApplication).container

        setContent {
            UniLifeTheme {
                Surface(modifier = Modifier) {
                    UniLifeApp(container = container)
                }
            }
        }
    }
}
