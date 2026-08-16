package com.bdavidgm.glm_chat.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.glm_chat.R
import com.bdavidgm.glm_chat.data.ApiConfig
import com.bdavidgm.glm_chat.data.ApiConfigStore
import com.bdavidgm.glm_chat.data.ChatMessage
import com.bdavidgm.glm_chat.data.MessageRole
import com.bdavidgm.glm_chat.data.ModelProbeResult
import com.bdavidgm.glm_chat.data.NvidiaChatClient
import com.bdavidgm.glm_chat.data.local.ChatDatabase
import com.bdavidgm.glm_chat.data.local.ChatThread
import com.bdavidgm.glm_chat.data.local.LocalMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

data class ChatUiState(
    val config: ApiConfig? = null,
    val currentThreadId: String? = null,
    val threads: List<ChatThread> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val streamingMessage: ChatMessage? = null,
    val isGenerating: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val availableModels: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedFileBase64: String? = null,
    val selectedFileType: String? = null,
)

class ChatViewModel(
    application: Application,
    private val configStore: ApiConfigStore = ApiConfigStore(application),
    private val chatClient: NvidiaChatClient = NvidiaChatClient(),
) : AndroidViewModel(application) {

    private val db = ChatDatabase.getDatabase(application)
    private val chatDao = db.chatDao()

    private val _uiState = MutableStateFlow(ChatUiState(config = configStore.load()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Isolated from ChatUiState so keystrokes do not recompose the chat list, sidebar, or markdown.
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private var streamJob: Job? = null
    private var probeJob: Job? = null
    private var messagesJob: Job? = null

    init {
        loadThreads()
    }

    private fun loadThreads() {
        viewModelScope.launch {
            chatDao.getAllThreads().collect { threads ->
                _uiState.update { it.copy(threads = threads) }
            }
        }
    }

    fun selectThread(threadId: String?) {
        messagesJob?.cancel()
        _uiState.update { it.copy(currentThreadId = threadId, messages = emptyList(), streamingMessage = null) }
        threadId?.let { observeMessages(it) }
    }

    fun createNewChat() {
        selectThread(null)
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            chatDao.deleteMessagesForThread(threadId)
            chatDao.deleteThread(threadId)
            if (_uiState.value.currentThreadId == threadId) {
                createNewChat()
            }
        }
    }

    fun onFileSelected(uri: Uri?, name: String?) {
        if (uri == null) {
            _uiState.update { it.copy(selectedFileUri = null, selectedFileName = null, selectedFileBase64 = null, selectedFileType = null) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = getApplication<Application>().contentResolver
                val type = contentResolver.getType(uri)
                
                if (type?.startsWith("image/") == true) {
                    val inputStream = contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    originalBitmap?.let { processBitmap(it, uri, name) }
                } else if (type == "application/pdf") {
                    processPdf(uri, name)
                } else if (type?.startsWith("text/") == true) {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    text?.let { fileText ->
                        _input.update { current -> current + "\n" + fileText }
                    }
                } else {
                    _uiState.update { it.copy(selectedFileUri = uri, selectedFileName = name, selectedFileType = type) }
                }
            } catch (e: Exception) {
                val application = getApplication<Application>()
                _uiState.update { it.copy(error = application.getString(R.string.error_file_process, e.message)) }
            }
        }
    }

    private fun processBitmap(originalBitmap: Bitmap, uri: Uri, name: String?) {
        // Reescalar si es muy grande (máximo 1024px en el lado más largo)
        val maxDimension = 1024
        val width = originalBitmap.width
        val height = originalBitmap.height
        val (newWidth, newHeight) = if (width > height) {
            if (width > maxDimension) {
                maxDimension to (height * maxDimension / width)
            } else width to height
        } else {
            if (height > maxDimension) {
                (width * maxDimension / height) to maxDimension
            } else width to height
        }

        val bitmap = if (newWidth != width || newHeight != height) {
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = name,
                selectedFileBase64 = base64,
                selectedFileType = "image/jpeg"
            )
        }
    }

    private fun processPdf(uri: Uri, name: String?) {
        try {
            val contentResolver = getApplication<Application>().contentResolver
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.use { pfd ->
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    processBitmap(bitmap, uri, name)
                }
                renderer.close()
            }
        } catch (e: Exception) {
            val application = getApplication<Application>()
            _uiState.update { it.copy(error = application.getString(R.string.error_pdf_preview, e.message)) }
        }
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            // Actualizar en DB
            chatDao.updateMessageContent(messageId, newContent)
            // Si el mensaje editado era del usuario, regenerar respuesta si es el último
            val state = _uiState.value
            val messages = state.messages
            val index = messages.indexOfFirst { it.id == messageId }
            if (index != -1 && messages[index].role == MessageRole.USER) {
                // Si es el último mensaje del usuario, o queremos re-procesar
                _input.value = newContent
                sendMessage()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(info = null) }
    }


    fun clearConfig() {
        streamJob?.cancel()
        configStore.clear()
        val application = getApplication<Application>()
        _input.value = ""
        _uiState.value = ChatUiState(info = application.getString(R.string.info_config_deleted))
    }

    fun setupDefaultConfig(apiKey: String) {
        val config = DEFAULT_API_CONFIG.copy(apiKey = apiKey.trim())
        configStore.save(config)
        val application = getApplication<Application>()
        _uiState.update { it.copy(config = config, info = application.getString(R.string.info_api_configured)) }
    }

    fun updateConfig(config: ApiConfig) {
        configStore.save(config)
        val application = getApplication<Application>()
        _uiState.update { it.copy(config = config, info = application.getString(R.string.info_config_updated)) }
    }

    fun loadAvailableModels(configOverride: ApiConfig? = null) {
        val config = configOverride ?: _uiState.value.config ?: return
        if (_uiState.value.isFetchingModels) return

        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingModels = true) }
            try {
                val models = chatClient.fetchModels(config)
                _uiState.update { it.copy(availableModels = models, isFetchingModels = false) }
            } catch (e: Exception) {
                val application = getApplication<Application>()
                _uiState.update {
                    it.copy(
                        isFetchingModels = false,
                        error = application.getString(R.string.error_load_models, e.message),
                    )
                }
            }
        }
    }

    fun probeModel(
        config: ApiConfig,
        modelId: String,
        onResult: (ModelProbeResult) -> Unit,
    ) {
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            try {
                val result = chatClient.probeChatModel(config, modelId)
                onResult(result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }
    }

    fun sendMessage() {
        val text = _input.value.trim()
        val config = _uiState.value.config
        if (text.isEmpty() || _uiState.value.isGenerating) return
        if (config == null) {
            val application = getApplication<Application>()
            _uiState.update {
                it.copy(error = application.getString(R.string.error_no_config))
            }
            return
        }

        viewModelScope.launch {
            var threadId = _uiState.value.currentThreadId
            if (threadId == null) {
                threadId = UUID.randomUUID().toString()
                val newThread = ChatThread(
                    id = threadId,
                    title = text.take(30) + if (text.length > 30) "..." else "",
                )
                chatDao.insertThread(newThread)
                _uiState.update { it.copy(currentThreadId = threadId) }
                // Reiniciar observación de mensajes para el nuevo thread
                observeMessages(threadId)
            }

            val userMsgId = UUID.randomUUID().toString()
            val fileBase64 = _uiState.value.selectedFileBase64
            val fileType = _uiState.value.selectedFileType

            chatDao.insertMessage(
                LocalMessage(
                    id = userMsgId,
                    threadId = threadId,
                    role = MessageRole.USER,
                    content = text,
                    filePath = _uiState.value.selectedFileUri?.toString(),
                    imageBase64 = fileBase64,
                    imageType = fileType
                )
            )

            val assistantId = UUID.randomUUID().toString()
            val assistantModel = config.model
            val assistantPlaceholder = ChatMessage(
                id = assistantId,
                role = MessageRole.ASSISTANT,
                content = "",
                isStreaming = true,
                model = assistantModel,
            )

            _input.value = ""
            _uiState.update {
                it.copy(
                    selectedFileUri = null,
                    selectedFileName = null,
                    selectedFileBase64 = null,
                    selectedFileType = null,
                    isGenerating = true,
                    streamingMessage = assistantPlaceholder,
                    error = null,
                )
            }

            // Usamos la lista actual de mensajes + el nuevo mensaje de usuario para la API
            val historyForApi = _uiState.value.messages + ChatMessage(
                id = userMsgId, 
                role = MessageRole.USER, 
                content = text,
                imageBase64 = fileBase64,
                imageType = fileType
            )

            streamJob = viewModelScope.launch {
                try {
                    var fullContent = ""
                    chatClient.streamChat(config, historyForApi).collect { token ->
                        fullContent += token
                        _uiState.update { state ->
                            state.copy(
                                streamingMessage = state.streamingMessage?.copy(content = fullContent)
                            )
                        }
                    }
                    
                    // Guardar respuesta final en DB
                    chatDao.insertMessage(
                        LocalMessage(
                            id = assistantId,
                            threadId = threadId,
                            role = MessageRole.ASSISTANT,
                            content = fullContent,
                            model = assistantModel,
                        )
                    )
                    // Actualizar timestamp del thread
                    _uiState.value.threads.find { it.id == threadId }?.let {
                        chatDao.updateThread(it.copy(lastMessageAt = System.currentTimeMillis()))
                    }

                    // Actualizar estado de forma atómica para evitar saltos/parpadeos en la UI
                    val finalAssistantMsg = ChatMessage(
                        id = assistantId,
                        role = MessageRole.ASSISTANT,
                        content = fullContent,
                        isStreaming = false,
                        model = assistantModel,
                    )
                    _uiState.update { state ->
                        state.copy(
                            isGenerating = false,
                            streamingMessage = null,
                            messages = (state.messages + finalAssistantMsg).distinctBy { it.id }
                        )
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val application = getApplication<Application>()
                    _uiState.update { 
                        it.copy(
                            isGenerating = false, 
                            streamingMessage = null,
                            error = e.message ?: application.getString(R.string.error_response_failed)
                        ) 
                    }
                }
            }
        }
    }

    private fun observeMessages(threadId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatDao.getMessagesForThread(threadId).collect { localMessages ->
                val chatMessages = localMessages.map { 
                    ChatMessage(
                        id = it.id,
                        role = it.role,
                        content = it.content,
                        imageBase64 = it.imageBase64,
                        imageType = it.imageType,
                        model = it.model,
                    ) 
                }
                _uiState.update { it.copy(messages = chatMessages) }
            }
        }
    }

    companion object {
        val DEFAULT_API_CONFIG = ApiConfig(
            baseUrl = "https://integrate.api.nvidia.com/v1",
            chatPath = "/chat/completions",
            apiKey = "",
            model = "z-ai/glm-5.2",
            temperature = 0.6,
            topP = 0.7,
            maxTokens = 1024,
            seed = 42,
            stream = true,
            showParticles = false,
        )

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        return ChatViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
    }
}
