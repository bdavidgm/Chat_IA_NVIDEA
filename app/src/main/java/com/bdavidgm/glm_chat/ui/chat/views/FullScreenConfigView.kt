package com.bdavidgm.glm_chat.ui.chat.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.bdavidgm.glm_chat.R
import com.bdavidgm.glm_chat.data.ApiConfig
import com.bdavidgm.glm_chat.data.ModelProbeResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenConfigView(
    config: ApiConfig,
    availableModels: List<String>,
    isFetchingModels: Boolean,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit,
    onReset: () -> Unit,
    onLoadModels: (ApiConfig) -> Unit,
    onProbeModel: (config: ApiConfig, modelId: String, onResult: (ModelProbeResult) -> Unit) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var chatPath by remember { mutableStateOf(config.chatPath) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var model by remember { mutableStateOf(config.model) }
    var temperature by remember { mutableStateOf(config.temperature.toString()) }
    var topP by remember { mutableStateOf(config.topP.toString()) }
    var maxTokens by remember { mutableStateOf(config.maxTokens.toString()) }
    var seed by remember { mutableStateOf(config.seed.toString()) }
    var stream by remember { mutableStateOf(config.stream) }
    var showParticles by remember { mutableStateOf(config.showParticles) }

    var modelMenuExpanded by remember { mutableStateOf(false) }
    var modelFilter by remember { mutableStateOf("") }
    var isProbing by remember { mutableStateOf(false) }
    var probingModelId by remember { mutableStateOf<String?>(null) }
    var probeMessage by remember { mutableStateOf<String?>(null) }
    val filteredModels = remember(availableModels, modelFilter) {
        availableModels.filter { it.contains(modelFilter, ignoreCase = true) }
    }

    fun draftConfig(selectedModel: String = model) = config.copy(
        baseUrl = baseUrl,
        chatPath = chatPath,
        apiKey = apiKey,
        model = selectedModel,
        temperature = temperature.toDoubleOrNull() ?: config.temperature,
        topP = topP.toDoubleOrNull() ?: config.topP,
        maxTokens = maxTokens.toIntOrNull() ?: config.maxTokens,
        seed = seed.toIntOrNull() ?: config.seed,
        stream = stream,
        showParticles = showParticles,
    )

    val probeUnavailable = stringResource(R.string.error_model_unavailable)
    val probeUnreachable = stringResource(R.string.error_model_unreachable)

    fun probeAndThen(modelId: String, onAvailable: () -> Unit) {
        if (isProbing) return
        isProbing = true
        probingModelId = modelId
        probeMessage = null
        onProbeModel(draftConfig(modelId), modelId) { result ->
            isProbing = false
            probingModelId = null
            when (result) {
                ModelProbeResult.Available -> {
                    probeMessage = null
                    onAvailable()
                }
                ModelProbeResult.Unavailable -> probeMessage = probeUnavailable
                ModelProbeResult.Unreachable -> probeMessage = probeUnreachable
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.api_key_label)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { },
                    label = { Text(stringResource(R.string.model_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray),
                    trailingIcon = {
                        IconButton(onClick = {
                            modelMenuExpanded = true
                            onLoadModels(draftConfig())
                        }) {
                            if (isFetchingModels || isProbing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    },
                )
                Box(modifier = Modifier.matchParentSize().clickable {
                    modelMenuExpanded = true
                    onLoadModels(draftConfig())
                })
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { if (!isProbing) modelMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.8f).height(400.dp),
                ) {
                    OutlinedTextField(
                        value = modelFilter,
                        onValueChange = { modelFilter = it },
                        placeholder = { Text(stringResource(R.string.search_model_placeholder)) },
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        singleLine = true,
                        enabled = !isProbing,
                    )
                    if (isProbing) {
                        Text(
                            text = stringResource(R.string.probing_model),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    filteredModels.take(50).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            enabled = !isProbing,
                            trailingIcon = if (probingModelId == m) {
                                {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                null
                            },
                            onClick = {
                                probeAndThen(m) {
                                    model = m
                                    modelMenuExpanded = false
                                }
                            },
                        )
                    }
                }
            }

            if (probeMessage != null) {
                Text(
                    text = probeMessage!!,
                    color = Color(0xFFE34234),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.base_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) temperature = it },
                    label = { Text(stringResource(R.string.temp_label)) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray)
                )
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) maxTokens = it },
                    label = { Text(stringResource(R.string.tokens_label)) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.enable_streaming), color = Color.White)
                Switch(checked = stream, onCheckedChange = { stream = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.particle_background), color = Color.White)
                    Text(stringResource(R.string.neural_network_effect), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Switch(checked = showParticles, onCheckedChange = { showParticles = it })
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        probeAndThen(model) {
                            onSave(draftConfig(model))
                        }
                    },
                    enabled = !isProbing,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF76B900).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF76B900),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE34234).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE34234).copy(alpha = 0.1f),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
