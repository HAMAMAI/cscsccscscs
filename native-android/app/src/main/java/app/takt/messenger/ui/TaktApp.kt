package app.takt.messenger.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.takt.messenger.CallState
import app.takt.messenger.MessengerUiState
import app.takt.messenger.MessengerViewModel
import app.takt.messenger.data.Message
import app.takt.messenger.data.Participant
import app.takt.messenger.ui.theme.Ink
import app.takt.messenger.ui.theme.Mint
import app.takt.messenger.ui.theme.Panel
import app.takt.messenger.ui.theme.PanelRaised
import app.takt.messenger.ui.theme.Purple
import app.takt.messenger.ui.theme.PurpleSoft
import app.takt.messenger.ui.theme.Rose
import app.takt.messenger.ui.theme.Stroke
import app.takt.messenger.ui.theme.TextMain
import app.takt.messenger.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaktApp(viewModel: MessengerViewModel, inviteCode: String?) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.openFile.collect { intent ->
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                snackbar.showSnackbar("На телефоне нет приложения для этого файла")
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF2B205A), Ink), radius = 1100f)
        )
    ) {
        when {
            state.session == null -> Onboarding(
                inviteCode = inviteCode,
                busy = state.busy,
                onCreate = viewModel::createRoom,
                onJoin = viewModel::joinRoom,
            )
            state.loading && state.room == null -> LoadingScreen()
            state.room != null -> MessengerScreen(state, viewModel)
            else -> LoadingScreen()
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(large = true)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = Purple, strokeWidth = 3.dp)
    }
}

@Composable
private fun Onboarding(
    inviteCode: String?,
    busy: Boolean,
    onCreate: (String, String) -> Unit,
    onJoin: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var roomName by rememberSaveable { mutableStateOf("Мой чат") }
    var manualCode by rememberSaveable { mutableStateOf("") }
    var joinMode by rememberSaveable(inviteCode) { mutableStateOf(inviteCode != null) }
    val code = inviteCode ?: manualCode.trim().uppercase()
    val canSubmit = name.trim().length >= 2 && if (joinMode) code.length == 10 else roomName.trim().length >= 2

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp)) {
                Logo()
                Spacer(Modifier.height(42.dp))
                Surface(
                    color = Purple.copy(alpha = .15f),
                    shape = RoundedCornerShape(40.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Purple.copy(alpha = .35f)),
                ) {
                    Text(
                        "●  БЕЗ GMAIL И EMAIL",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = PurpleSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    if (joinMode) "Вас уже\nждут внутри" else "Ближе к людям.\nБез лишнего.",
                    color = TextMain,
                    fontSize = 43.sp,
                    lineHeight = 47.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Сообщения, фото, файлы, голосовые и чистый звук. Камеры здесь нет.",
                    color = TextMuted,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(28.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .96f)),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text(if (joinMode) "Войти по приглашению" else "Создать пространство", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Только имя — никаких почтовых аккаунтов", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(20.dp))
                        TaktField(name, { name = it.take(32) }, "Ваше имя", "Например, Алекс")
                        Spacer(Modifier.height(12.dp))
                        if (joinMode) {
                            if (inviteCode != null) {
                                InviteCode(code)
                            } else {
                                TaktField(manualCode, { manualCode = it.uppercase().filter(Char::isLetterOrDigit).take(10) }, "Код приглашения", "A1B2C3D4E5")
                            }
                        } else {
                            TaktField(roomName, { roomName = it.take(48) }, "Название чата", "Команда")
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { if (joinMode) onJoin(code, name) else onCreate(roomName, name) },
                            enabled = canSubmit && !busy,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            else {
                                Text(if (joinMode) "Войти в чат" else "Создать чат", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                            }
                        }
                        if (inviteCode == null) {
                            TextButton(onClick = { joinMode = !joinMode }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(if (joinMode) "Создать новый чат" else "У меня есть код")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                FeatureRow()
                Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp))
            }
        }
    }
}

@Composable
private fun FeatureRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Feature(Icons.Default.Call, "Аудио")
        Feature(Icons.Default.Mic, "Голосовые")
        Feature(Icons.Default.AttachFile, "Файлы")
        Feature(Icons.Default.Link, "По ссылке")
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(43.dp), shape = CircleShape, color = PanelRaised) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = PurpleSoft, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun InviteCode(code: String) {
    Surface(color = PanelRaised, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, null, tint = PurpleSoft)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Код приглашения", color = TextMuted, fontSize = 11.sp)
                Text(code, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun TaktField(value: String, onValue: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessengerScreen(state: MessengerUiState, viewModel: MessengerViewModel) {
    val session = state.session ?: return
    val room = state.room ?: return
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var microphoneAction by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::sendUri) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::sendUri) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (microphoneAction == "record") viewModel.startRecording() else if (microphoneAction == "call") viewModel.joinCall()
        }
        microphoneAction = null
    }
    fun withMicrophone(action: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (action == "record") viewModel.startRecording() else viewModel.joinCall()
        } else {
            microphoneAction = action
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(room.messages.size) {
        if (room.messages.isNotEmpty()) listState.animateScrollToItem(room.messages.lastIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = Ink.copy(alpha = .96f), tonalElevation = 6.dp) {
                Column(Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(room.me, 40)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(room.roomName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${room.participants.count { it.online }.coerceAtLeast(1)} в сети · ${room.participants.size} участников", color = TextMuted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Поиск") }
                        IconButton(onClick = { showPeople = true }) { Icon(Icons.Default.Group, "Участники") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Поделиться приглашением") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { menuOpen = false; shareInvite(context as Activity, session.inviteCode, session.roomName) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Выйти с устройства", color = Rose) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Rose) },
                                    onClick = { menuOpen = false; viewModel.leaveRoom() },
                                )
                            }
                        }
                    }
                    AnimatedVisibility(searchOpen) {
                        TextField(
                            value = search,
                            onValueChange = { search = it.take(80) },
                            placeholder = { Text("Поиск по сообщениям") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PanelRaised,
                                unfocusedContainerColor = PanelRaised,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                    CallBanner(state, onJoin = { withMicrophone("call") }, onOpen = {})
                    HorizontalDivider(color = Stroke)
                }
            }
        },
        bottomBar = {
            Composer(
                draft = draft,
                busy = state.busy,
                recording = state.recording,
                recordingSeconds = state.recordingSeconds,
                onDraft = { draft = it.take(2_000) },
                onSend = {
                    val body = draft.trim()
                    if (body.isNotEmpty()) { viewModel.sendText(body); draft = "" }
                },
                onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onFile = { filePicker.launch(arrayOf("image/*", "audio/*", "application/pdf", "text/plain", "application/zip", "application/octet-stream")) },
                onRecord = { withMicrophone("record") },
                onStopRecord = { viewModel.stopRecording(false) },
                onCancelRecord = { viewModel.stopRecording(true) },
            )
        },
    ) { padding ->
        val filtered = remember(room.messages, search) {
            if (search.isBlank()) room.messages else room.messages.filter {
                it.body.contains(search, true) || it.displayName.contains(search, true) || it.attachment?.fileName?.contains(search, true) == true
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item { ChatIntro(room.roomName, session.inviteCode) { shareInvite(context as Activity, session.inviteCode, session.roomName) } }
            items(filtered, key = { it.id }) { message ->
                MessageRow(message, mine = message.participantId == session.participantId, viewModel = viewModel)
            }
            if (filtered.isEmpty() && search.isNotBlank()) item {
                Text("Ничего не найдено", Modifier.fillMaxWidth().padding(32.dp), color = TextMuted)
            }
        }
    }

    if (state.callState != CallState.Idle) {
        CallOverlay(state, onMute = viewModel::toggleMute, onEnd = viewModel::leaveCall)
    }
    if (showPeople) {
        PeopleDialog(room.participants, onDismiss = { showPeople = false })
    }
}

@Composable
private fun CallBanner(state: MessengerUiState, onJoin: () -> Unit, onOpen: () -> Unit) {
    val call = state.room?.activeCall
    if (call == null || state.callState != CallState.Idle) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onOpen),
        color = Mint.copy(alpha = .12f),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Mint.copy(alpha = .4f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), shape = CircleShape, color = Mint.copy(alpha = .2f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, tint = Mint) }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Идёт аудиозвонок", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Начал ${call.startedByName}", color = TextMuted, fontSize = 12.sp)
            }
            Button(onClick = onJoin, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("Войти", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ChatIntro(name: String, code: String, share: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .9f)),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(54.dp), shape = CircleShape, color = Purple.copy(alpha = .18f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Waves, null, tint = PurpleSoft, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(name, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text("Начало разговора. Вход без почты и Gmail.", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = share, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Share, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Пригласить · $code")
            }
        }
    }
}

@Composable
private fun MessageRow(message: Message, mine: Boolean, viewModel: MessengerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            Avatar(Participant(message.participantId, message.displayName, message.color, false), 34)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(.82f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (mine) "Вы" else message.displayName, color = if (mine) PurpleSoft else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(7.dp))
                Text(formatTime(message.createdAt), color = TextMuted.copy(alpha = .7f), fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Surface(
                color = if (mine) Purple else PanelRaised,
                shape = if (mine) RoundedCornerShape(19.dp, 19.dp, 5.dp, 19.dp) else RoundedCornerShape(19.dp, 19.dp, 19.dp, 5.dp),
                border = if (mine) null else androidx.compose.foundation.BorderStroke(1.dp, Stroke),
            ) {
                when {
                    message.kind == "image" && message.attachment != null -> ImageMessage(message, viewModel)
                    message.kind == "audio" && message.attachment != null -> AttachmentMessage(message, viewModel, voice = true)
                    message.attachment != null -> AttachmentMessage(message, viewModel, voice = false)
                    else -> Text(message.body, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ImageMessage(message: Message, viewModel: MessengerViewModel) {
    val attachment = message.attachment ?: return
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.id) {
        val payload = viewModel.loadAttachment(attachment.id)
        value = payload?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Column(Modifier.clickable { viewModel.openAttachment(message) }) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = attachment.fileName,
                modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ImageIcon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AttachmentMessage(message: Message, viewModel: MessengerViewModel, voice: Boolean) {
    val attachment = message.attachment ?: return
    Row(
        Modifier.clickable { viewModel.openAttachment(message) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(42.dp), shape = CircleShape, color = Color.White.copy(alpha = .13f)) {
            Box(contentAlignment = Alignment.Center) { Icon(if (voice) Icons.Default.AudioFile else Icons.Default.FilePresent, null) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (voice) "Голосовое сообщение" else attachment.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (voice) formatDuration(attachment.durationSeconds ?: 0) else formatBytes(attachment.sizeBytes), color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
        }
        Icon(Icons.Default.Download, "Скачать", Modifier.size(20.dp))
    }
}

@Composable
private fun Composer(
    draft: String,
    busy: Boolean,
    recording: Boolean,
    recordingSeconds: Int,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onPhoto: () -> Unit,
    onFile: () -> Unit,
    onRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onCancelRecord: () -> Unit,
) {
    Surface(color = Ink.copy(alpha = .98f), shadowElevation = 12.dp) {
        if (recording) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPaddingCompat().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancelRecord) { Icon(Icons.Default.Cancel, "Отменить", tint = Rose) }
                RecordingPulse()
                Spacer(Modifier.width(9.dp))
                Text("Запись  ${formatDuration(recordingSeconds)}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                FilledIconButton(onClick = onStopRecord, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Purple)) { Icon(Icons.Default.Stop, "Отправить голосовое") }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().navigationBarsPaddingCompat().imePadding().padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = onPhoto, enabled = !busy) { Icon(Icons.Default.AddPhotoAlternate, "Фото", tint = TextMuted) }
                IconButton(onClick = onFile, enabled = !busy) { Icon(Icons.Default.AttachFile, "Файл", tint = TextMuted) }
                TextField(
                    value = draft,
                    onValueChange = onDraft,
                    placeholder = { Text("Сообщение…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PanelRaised,
                        unfocusedContainerColor = PanelRaised,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = if (draft.isBlank()) onRecord else onSend,
                    enabled = !busy,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Purple),
                ) { Icon(if (draft.isBlank()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send, if (draft.isBlank()) "Голосовое" else "Отправить") }
            }
        }
    }
}

@Composable
private fun RecordingPulse() {
    val transition = rememberInfiniteTransition(label = "record")
    val alpha by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "record-alpha")
    Box(Modifier.size(11.dp).alpha(alpha).background(Rose, CircleShape))
}

@Composable
private fun CallOverlay(state: MessengerUiState, onMute: () -> Unit, onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .82f)), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 30.dp).navigationBarsPaddingCompat(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(Modifier.size(78.dp), shape = CircleShape, color = Purple.copy(alpha = .2f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Waves, null, tint = PurpleSoft, modifier = Modifier.size(38.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                Text(if (state.callState == CallState.Connecting) "Подключаем звук…" else "Аудиозвонок", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (state.callState == CallState.Connected) "${state.callParticipants} в разговоре" else "Ищем лучший маршрут", color = TextMuted)
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    FilledIconButton(onClick = onMute, modifier = Modifier.size(58.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = PanelRaised)) {
                        Icon(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic, if (state.muted) "Включить микрофон" else "Выключить микрофон")
                    }
                    FilledIconButton(onClick = onEnd, modifier = Modifier.size(58.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rose)) {
                        Icon(Icons.Default.CallEnd, "Завершить")
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleDialog(people: List<Participant>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Участники · ${people.size}") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.55f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(people, key = { it.id }) { person ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person, 40)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.displayName, fontWeight = FontWeight.Bold)
                            Text(if (person.online) "в сети" else "не в сети", color = if (person.online) Mint else TextMuted, fontSize = 12.sp)
                        }
                        if (person.isOwner) Icon(Icons.Default.CheckCircle, "Владелец", tint = PurpleSoft, modifier = Modifier.size(19.dp))
                    }
                }
            }
        },
        containerColor = Panel,
    )
}

@Composable
private fun Logo(large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(if (large) 58.dp else 39.dp), shape = RoundedCornerShape(if (large) 19.dp else 13.dp), color = Purple) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Waves, null, tint = Color.White, modifier = Modifier.size(if (large) 31.dp else 22.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Text("такт", fontSize = if (large) 39.sp else 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    }
}

@Composable
private fun Avatar(person: Participant, size: Int) {
    val color = runCatching { Color(android.graphics.Color.parseColor(person.color)) }.getOrDefault(Purple)
    Surface(Modifier.size(size.dp), shape = CircleShape, color = color) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                person.displayName.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" },
                color = Color.White,
                fontSize = (size * .34f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

private fun shareInvite(activity: Activity, code: String, roomName: String) {
    val url = "https://takt-messenger.vercel.app/join/$code"
    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Чат «$roomName»")
        putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь к чату в Такте — почта не нужна\n$url")
    }, "Отправить приглашение"))
}

private fun formatBytes(value: Int): String = when {
    value < 1024 -> "$value Б"
    value < 1024 * 1024 -> "${value / 1024} КБ"
    else -> String.format(Locale.US, "%.1f МБ", value / 1024f / 1024f)
}

private fun formatDuration(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formatTime(value: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    val date = parser.parse(value.take(19)) ?: Date()
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}.getOrDefault("")

private fun Modifier.navigationBarsPaddingCompat(): Modifier = padding(
    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
)
