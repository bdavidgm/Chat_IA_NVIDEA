package com.bdavidgm.glm_chat.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.bdavidgm.glm_chat.R
import com.bdavidgm.glm_chat.data.ChatMessage
import com.bdavidgm.glm_chat.data.MessageRole
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MessageBubble(
    message: ChatMessage,
    onEdit: (messageId: String, newContent: String) -> Unit,
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) Color(0xFF424242) else Color(0xFF3C5E00)
    val contentColor = Color.White
    val fallbackLabel = stringResource(R.string.assistant_label)
    val modelLabel = (message.model?.takeIf { it.isNotBlank() } ?: fallbackLabel)
        .substringAfterLast('/')
        .uppercase()
    val clipboardManager = LocalClipboardManager.current
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(message.content) }
    var showFullImage by remember { mutableStateOf(false) }
    
    // Decodificamos el base64 a bytes para que Coil lo maneje mejor
    val imageBytes = remember(message.imageBase64) {
        message.imageBase64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
    }

    if (showFullImage && imageBytes != null) {
        AlertDialog(
            onDismissRequest = { showFullImage = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Box(modifier = Modifier.fillMaxSize().clickable { showFullImage = false }, contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = imageBytes,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                }
            },
            confirmButton = {},
            containerColor = Color.Black.copy(alpha = 0.9f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalAlignment = alignment
    ) {
        if (!isUser) {
            Text(
                text = modelLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF76B900),
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )
        }
        
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isUser) {
                IconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.size(32.dp).padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = bubbleColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (imageBytes != null) {
                        AsyncImage(
                            model = imageBytes,
                            contentDescription = stringResource(R.string.attachment_image),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFullImage = true }
                                .padding(bottom = 8.dp)
                        )
                    }

                    if (isEditing) {
                        Column {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = { editValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { isEditing = false }) { Text(stringResource(R.string.cancel)) }
                                TextButton(onClick = { 
                                    onEdit(message.id, editValue)
                                    isEditing = false 
                                }) { Text(stringResource(R.string.send)) }
                            }
                        }
                    } else {
                        // Usamos un Box con tamaño mínimo para estabilizar la burbuja
                        Box(modifier = Modifier.widthIn(min = 20.dp)) {
                            when {
                                message.content.isEmpty() && message.isStreaming -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                }
                                message.isStreaming -> {
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                        color = Color.White
                                    )
                                }
                                else -> {
                                    Markdown(
                                        content = message.content,
                                        colors = markdownColor(
                                            text = Color.White,
                                            codeBackground = Color.Black.copy(alpha = 0.5f),
                                        ),
                                        typography = markdownTypography(
                                            text = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                            code = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            if (!isUser) {
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                    modifier = Modifier.size(32.dp).padding(start = 4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy), tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
