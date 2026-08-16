package com.bdavidgm.glm_chat.ui.chat

import android.app.Activity
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdavidgm.glm_chat.ui.chat.components.ComposerBar
import com.bdavidgm.glm_chat.ui.chat.components.MessageBubble
import com.bdavidgm.glm_chat.ui.chat.components.NvidiaParticlesBackground
import com.bdavidgm.glm_chat.ui.chat.components.SidebarContent
import com.bdavidgm.glm_chat.ui.chat.dialogs.ApiKeySetupDialog
import com.bdavidgm.glm_chat.ui.chat.dialogs.ExitConfirmationDialog
import com.bdavidgm.glm_chat.ui.chat.dialogs.HelpDialog
import com.bdavidgm.glm_chat.ui.chat.views.FullScreenConfigView
import androidx.compose.ui.res.stringResource
import com.bdavidgm.glm_chat.R
import com.bdavidgm.glm_chat.data.MessageRole
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = run {
        val app = LocalContext.current.applicationContext as Application
        viewModel(factory = ChatViewModel.factory(app))
    },
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var stickToBottom by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf { listState.isAtBottom() }
    }

    LaunchedEffect(state.currentThreadId) {
        stickToBottom = true
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.isAtBottom() }
            .distinctUntilChanged()
            .collect { (inProgress, atBottom) ->
                val info = listState.layoutInfo
                if (info.totalItemsCount == 0 || info.visibleItemsInfo.isEmpty()) return@collect
                if (inProgress && !atBottom) {
                    stickToBottom = false
                } else if (!inProgress) {
                    stickToBottom = atBottom
                }
            }
    }


    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    LaunchedEffect(state.info) {
        val info = state.info ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(info)
        viewModel.clearInfo()
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Manejo del botón atrás
    BackHandler(enabled = drawerState.isOpen || showConfigDialog || showHelpDialog || showExitDialog) {
        when {
            showConfigDialog -> showConfigDialog = false
            showHelpDialog -> showHelpDialog = false
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showExitDialog -> showExitDialog = false
            else -> showExitDialog = true
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onFileSelected(uri, context.getString(R.string.attachment_image))
        }
    }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showExitDialog) {
        ExitConfirmationDialog(
            onConfirm = { (context as? Activity)?.finish() },
            onDismiss = { showExitDialog = false }
        )
    }

    if (showConfigDialog && state.config != null) {
        FullScreenConfigView(
            config = state.config!!,
            availableModels = state.availableModels,
            isFetchingModels = state.isFetchingModels,
            onDismiss = { showConfigDialog = false },
            onSave = { updated ->
                viewModel.updateConfig(updated)
                showConfigDialog = false
            },
            onReset = {
                viewModel.clearConfig()
                showConfigDialog = false
            },
            onLoadModels = { viewModel.loadAvailableModels(it) },
            onProbeModel = viewModel::probeModel,
        )
    } else {
        if (state.config == null && !state.isImporting) {
            ApiKeySetupDialog(onConfirm = viewModel::setupDefaultConfig)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SidebarContent(
                    threads = state.threads,
                    currentThreadId = state.currentThreadId,
                    onThreadSelected = { 
                        viewModel.selectThread(it)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteThread = { viewModel.deleteThread(it) },
                    onNewChat = {
                        viewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onOpenSettings = {
                        showConfigDialog = true
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = state.threads.find { it.id == state.currentThreadId }?.title ?: stringResource(R.string.new_chat),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = state.config?.model ?: stringResource(R.string.assistant_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_options))
                            }
                        },
                        actions = {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.menu_options),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.help_title)) },
                                    onClick = {
                                        menuExpanded = false
                                        showHelpDialog = true
                                    },
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                    )
                },
                bottomBar = {
                    if (state.config != null) {
                        IsolatedComposerBar(
                            input = viewModel.input,
                            isGenerating = state.isGenerating,
                            onValueChange = viewModel::onInputChange,
                            onSend = viewModel::sendMessage,
                            onAttachFile = { pickFile.launch("*/*") },
                            selectedFileName = state.selectedFileName,
                            selectedFileUri = state.selectedFileUri,
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.ime.union(WindowInsets.navigationBars),
                            ),
                        )
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = if (state.config?.showParticles == true) Color.Transparent else MaterialTheme.colorScheme.background,
            ) { padding ->
                LaunchedEffect(stickToBottom, state.messages.size, state.streamingMessage?.content?.length) {
                    if (stickToBottom) {
                        listState.scrollToBottom()
                    }
                }

                val mainContent = @Composable {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        when {
                            state.config == null -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (state.isImporting) CircularProgressIndicator()
                                }
                            }

                            state.messages.isEmpty() && state.currentThreadId == null -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }

                            else -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val allMessages = remember(state.messages, state.streamingMessage) {
                                        (state.messages + listOfNotNull(state.streamingMessage)).distinctBy { it.id }
                                    }

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 16.dp),
                                    ) {
                                        items(
                                            allMessages,
                                            key = { it.id },
                                            contentType = { if (it.role == MessageRole.USER) "user" else "assistant" },
                                        ) { message ->
                                            MessageBubble(
                                                message = message,
                                                onEdit = viewModel::editMessage,
                                            )
                                        }
                                    }

                                    // Botón de flecha hacia abajo
                                    AnimatedVisibility(
                                        visible = !isAtBottom,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(bottom = 8.dp, end = 12.dp)
                                    ) {
                                        FloatingActionButton(
                                            onClick = {
                                                stickToBottom = true
                                                scope.launch { listState.scrollToBottom() }
                                            },
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.Black,
                                            shape = CircleShape,
                                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = stringResource(R.string.scroll_to_bottom),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.config?.showParticles == true) {
                    NvidiaParticlesBackground { mainContent() }
                } else {
                    mainContent()
                }
            }
        }
    }
}

@Composable
private fun IsolatedComposerBar(
    input: StateFlow<String>,
    isGenerating: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    selectedFileName: String?,
    selectedFileUri: android.net.Uri?,
    modifier: Modifier = Modifier,
) {
    val value by input.collectAsState()
    ComposerBar(
        value = value,
        isGenerating = isGenerating,
        onValueChange = onValueChange,
        onSend = onSend,
        onAttachFile = onAttachFile,
        selectedFileName = selectedFileName,
        selectedFileUri = selectedFileUri,
        modifier = modifier,
    )
}

private fun LazyListState.isAtBottom(thresholdPx: Int = 120): Boolean {
    val info = layoutInfo
    val lastItem = info.visibleItemsInfo.lastOrNull() ?: return false
    if (lastItem.index < info.totalItemsCount - 1) return false
    return lastItem.offset + lastItem.size <= info.viewportEndOffset + thresholdPx
}

private suspend fun LazyListState.scrollToBottom() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val overflow = lastItem.offset + lastItem.size - layoutInfo.viewportEndOffset
    if (overflow > 0) {
        scrollBy(overflow.toFloat())
    }
}
