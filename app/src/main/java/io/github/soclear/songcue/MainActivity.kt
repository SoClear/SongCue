package io.github.soclear.songcue

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.soclear.songcue.ui.theme.SongCueTheme
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    companion object {
        private var preferences by mutableStateOf<SharedPreferences?>(null)

        init {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    preferences = service.getRemotePreferences("settings")
                }

                override fun onServiceDied(service: XposedService) {
                    preferences = null
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SongCueTheme {
                val prefs = preferences ?: return@SongCueTheme
                var singerPosition by remember(prefs) {
                    mutableIntStateOf(prefs.getInt("singer_position", 0) ?: 0)
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(24.dp)
                            .selectableGroup()
                    ) {
                        Text(
                            stringResource(R.string.artist_announcement),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        listOf(
                            R.string.artist_none,
                            R.string.artist_before_title,
                            R.string.artist_after_title
                        )
                            .forEachIndexed { index, label ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = singerPosition == index,
                                            role = Role.RadioButton,
                                            onClick = {
                                                prefs.edit { putInt("singer_position", index) }
                                                singerPosition = index
                                            }
                                        )
                                        .padding(vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = singerPosition == index,
                                        onClick = null,
                                    )
                                    Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                    }
                }
            }
        }
    }
}
