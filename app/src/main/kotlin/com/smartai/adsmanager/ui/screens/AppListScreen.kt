package com.smartai.adsmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartai.adsmanager.appdetection.DiscoveredApp
import com.smartai.adsmanager.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: MainViewModel) {
    val discovered by viewModel.discovered.collectAsState()
    val ui by viewModel.ui.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.searchApps("")
    }
    LaunchedEffect(query) {
        viewModel.searchApps(query)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Connect Applications") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search installed apps…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(discovered, key = { it.packageName }) { app ->
                    val connection = ui.connections.find { it.packageName == app.packageName }
                    val isConnected = connection != null
                    val isSelected = ui.selectedPackage == app.packageName
                    AppCard(
                        app = app,
                        isConnected = isConnected,
                        isSelected = isSelected,
                        onConnect = { viewModel.connectApp(app) },
                        onDisconnect = { viewModel.disconnectApp(app.packageName) },
                        onSelect = { viewModel.selectApp(app.packageName) },
                    )
                }
                if (discovered.isEmpty()) {
                    item {
                        Text(
                            "No applications found. Install more apps or adjust your search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    app: DiscoveredApp,
    isConnected: Boolean,
    isSelected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(app.appName, fontWeight = FontWeight.SemiBold)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (isConnected) {
                        OutlinedButton(onClick = onDisconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Text("Disconnect", modifier = Modifier.padding(start = 6.dp))
                        }
                    } else {
                        Button(
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Text("Connect", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            if (isConnected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                    Text("Select as active application")
                }
            }
        }
    }
}
