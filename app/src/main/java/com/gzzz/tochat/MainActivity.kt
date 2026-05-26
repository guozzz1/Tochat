package com.gzzz.tochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.gzzz.tochat.ui.chat.ChatViewModel
import com.gzzz.tochat.ui.navigation.AppNavigation
import com.gzzz.tochat.ui.theme.ToChatTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToChatTheme {
                val viewModel: ChatViewModel = hiltViewModel()
                val backgroundPath by viewModel.backgroundPath.collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    // 全局背景层
                    if (backgroundPath != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = File(backgroundPath!!)),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                    }

                    AppNavigation(hasBackground = backgroundPath != null)
                }
            }
        }
    }
}
