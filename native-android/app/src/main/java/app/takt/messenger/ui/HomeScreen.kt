package app.takt.messenger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.takt.messenger.HomeSection
import app.takt.messenger.MessengerUiState
import app.takt.messenger.data.AppearanceSettings
import app.takt.messenger.data.AvatarShape
import app.takt.messenger.data.ConversationSummary
import app.takt.messenger.data.ThemeMode
import app.takt.messenger.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MessengerUiState,
    onRefresh: () -> Unit,
    onSection: (HomeSection) -> Unit,
    onOpenChat: (ConversationSummary) -> Unit,
    onPeopleQuery: (String) -> Unit,
    onOpenPerson: (UserProfile) -> Unit,
    onCreateGroup: (String, List<UserProfile>) -> Unit,
    onSaveProfile: (String, String, String, String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAvatarShape: (AvatarShape) -> Unit,
    onCreateFolder: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var createFolder by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(30.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Waves, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("такт", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { editProfile = true }) {
                        Avatar(state.profile, state.appearance, 34)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    HomeSection.Chats to Icons.Default.Chat,
                    HomeSection.People to Icons.Default.People,
                    HomeSection.Calls to Icons.Default.Call,
                    HomeSection.Settings to Icons.Default.Settings,
                ).forEach { (section, icon) ->
                    NavigationBarItem(
                        selected = state.section == section,
                        onClick = { onSection(section) },
                        icon = { Icon(icon, section.name) },
                        label = { Text(sectionLabel(section)) },
                    )
                }
            }
        },
    ) { padding ->
        when (state.section) {
            HomeSection.Chats -> ChatsPanel(
                conversations = state.conversations,
                appearance = state.appearance,
                onOpenChat = onOpenChat,
                onShowArchive = { onSection(HomeSection.Chats) },
                modifier = Modifier.padding(padding),
            )
            HomeSection.People -> PeoplePanel(
                query = state.peopleQuery,
                people = state.people,
                appearance = state.appearance,
                onQuery = onPeopleQuery,
                onOpen = onOpenPerson,
                onCreateGroup = onCreateGroup,
                modifier = Modifier.padding(padding),
            )
            HomeSection.Calls -> CallsPanel(
                calls = state.calls,
                conversations = state.conversations,
                modifier = Modifier.padding(padding),
            )
            HomeSection.Settings -> SettingsPanel(
                profile = state.profile,
                folders = state.folders.map { it.name },
                appearance = state.appearance,
                onEditProfile = { editProfile = true },
                onTheme = onTheme,
                onAvatarShape = onAvatarShape,
                onCreateFolder = { createFolder = true },
                onSignOut = onSignOut,
                modifier = Modifier.padding(padding),
            )
        }
    }
    if (editProfile && state.profile != null) {
        EditProfileDialog(
            profile = state.profile,
            onSave = { name, username, about, color ->
                onSaveProfile(name, username, about, color)
                editProfile = false
            },
            onDismiss = { editProfile = false },
        )
    }
    if (createFolder) {
        SimpleTextDialog(
            title = "Новая папка",
            label = "Название папки",
            confirm = "Создать",
            onConfirm = { name -> onCreateFolder(name); createFolder = false },
            onDismiss = { createFolder = false },
        )
    }
}

@Composable
private fun ChatsPanel(
    conversations: List<ConversationSummary>,
    appearance: AppearanceSettings,
    onOpenChat: (ConversationSummary) -> Unit,
    onShowArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = conversations.filterNot { it.settings.archived }
    val archived = conversations.count { it.settings.archived }
    if (visible.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Chat, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Пока нет чатов", style = MaterialTheme.typography.titleLarge)
            Text("Откройте «Люди» и найдите пользователя по username.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        if (archived > 0) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onShowArchive).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Архив · $archived", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(visible.sortedWith(compareByDescending<ConversationSummary> { it.settings.pinned }.thenByDescending { it.updatedAt }), key = { it.id }) { chat ->
            ConversationRow(chat, appearance, onClick = { onOpenChat(chat) })
        }
    }
}

@Composable
private fun ConversationRow(chat: ConversationSummary, appearance: AppearanceSettings, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = if (appearance.compactChats) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.title, chat.avatarColor, appearance, if (appearance.compactChats) 45 else 54)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.settings.pinned) Icon(Icons.Default.Check, "Закреплён", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Text(formatChatTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val message = chat.lastMessage
                Text(
                    when {
                        chat.settings.draft.isNotBlank() -> "Черновик: ${chat.settings.draft}"
                        message == null -> "Начните разговор"
                        message.kind == "image" -> "Фотография"
                        message.kind == "voice" -> "Голосовое сообщение"
                        message.kind == "file" -> message.attachment?.fileName ?: "Файл"
                        else -> message.body
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (chat.settings.draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (chat.unreadCount > 0) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text(chat.unreadCount.coerceAtMost(99).toString(), Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeoplePanel(
    query: String,
    people: List<UserProfile>,
    appearance: AppearanceSettings,
    onQuery: (String) -> Unit,
    onOpen: (UserProfile) -> Unit,
    onCreateGroup: (String, List<UserProfile>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectingGroup by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var createDialog by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (selectingGroup) "Выберите участников" else "Люди", style = MaterialTheme.typography.titleLarge)
                Text("Ищите по @username или имени", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                selectingGroup = !selectingGroup
                selected = emptySet()
            }) { Icon(if (selectingGroup) Icons.Default.Close else Icons.Default.GroupAdd, if (selectingGroup) "Отмена" else "Группа") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Найти пользователя") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        if (selectingGroup && selected.isNotEmpty()) {
            Button(onClick = { createDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Создать группу · ${selected.size}")
            }
        }
        if (query.trim().length < 2) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Введите минимум 2 символа", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(people, key = { it.id }) { person ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (selectingGroup) selected = if (person.id in selected) selected - person.id else selected + person.id
                            else onOpen(person)
                        }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(person, appearance, 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person.displayName, fontWeight = FontWeight.Medium)
                            Text(person.username?.let { "@$it" } ?: person.about, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (person.isOnline) Text("в сети", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        if (selectingGroup && person.id in selected) Icon(Icons.Default.Check, "Выбран", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (createDialog) {
        SimpleTextDialog(
            title = "Новая группа",
            label = "Название группы",
            confirm = "Создать",
            onConfirm = { title ->
                onCreateGroup(title, people.filter { it.id in selected })
                createDialog = false
                selectingGroup = false
                selected = emptySet()
            },
            onDismiss = { createDialog = false },
        )
    }
}

@Composable
private fun CallsPanel(calls: List<app.takt.messenger.data.CallHistoryItem>, conversations: List<ConversationSummary>, modifier: Modifier = Modifier) {
    val titles = conversations.associate { it.id to it.title }
    if (calls.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("История звонков пуста")
                Text("Звонок можно начать из любого чата.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Звонки", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)) }
        items(calls, key = { it.id }) { call ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titles[call.conversationId] ?: "Чат", fontWeight = FontWeight.Medium)
                    Text(if (call.video) "Видеозвонок" else "Аудиозвонок", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatChatTime(call.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    profile: UserProfile?,
    folders: List<String>,
    appearance: AppearanceSettings,
    onEditProfile: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAvatarShape: (AvatarShape) -> Unit,
    onCreateFolder: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onEditProfile).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile, appearance, 54)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.displayName ?: "Профиль", style = MaterialTheme.typography.titleMedium)
                    Text(profile?.username?.let { "@$it" } ?: "Задайте уникальный username", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.Person, null)
            }
        }
        SettingsRow(Icons.Default.Palette, "Тема", when (appearance.themeMode) { ThemeMode.Dark -> "Тёмная"; ThemeMode.Light -> "Светлая"; ThemeMode.Midnight -> "Полночь" }) {
            onTheme(when (appearance.themeMode) { ThemeMode.Dark -> ThemeMode.Midnight; ThemeMode.Midnight -> ThemeMode.Light; ThemeMode.Light -> ThemeMode.Dark })
        }
        SettingsRow(Icons.Default.Person, "Форма аватаров", when (appearance.avatarShape) { AvatarShape.Circle -> "Круг"; AvatarShape.Rounded -> "Скруглённый"; AvatarShape.Square -> "Квадрат" }) {
            onAvatarShape(when (appearance.avatarShape) { AvatarShape.Circle -> AvatarShape.Rounded; AvatarShape.Rounded -> AvatarShape.Square; AvatarShape.Square -> AvatarShape.Circle })
        }
        SettingsRow(Icons.Default.Folder, "Папки", if (folders.isEmpty()) "Создать папку" else folders.joinToString()) { onCreateFolder() }
        HorizontalDivider()
        SettingsRow(Icons.Default.Logout, "Выйти", "Сессия будет завершена") { onSignOut() }
        Text("PIN и биометрия, уведомления и приватность подготовлены в следующем защищённом этапе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditProfileDialog(profile: UserProfile, onSave: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var username by rememberSaveable { mutableStateOf(profile.username.orEmpty()) }
    var about by rememberSaveable { mutableStateOf(profile.about) }
    var color by rememberSaveable { mutableStateOf(profile.avatarColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(name, username, about, color) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(username, { username = it.lowercase().replace("@", "") }, label = { Text("Username") }, singleLine = true, prefix = { Text("@") })
                OutlinedTextField(about, { about = it }, label = { Text("О себе") }, maxLines = 3)
                OutlinedTextField(color, { color = it.uppercase() }, label = { Text("Цвет аватара (#RRGGBB)") }, singleLine = true)
            }
        },
    )
}

@Composable
private fun SimpleTextDialog(title: String, label: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (value.trim().isNotBlank()) onConfirm(value.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun Avatar(profile: UserProfile?, appearance: AppearanceSettings, size: Int) {
    Avatar(profile?.displayName ?: "?", profile?.avatarColor ?: "#8C78FF", appearance, size)
}

@Composable
internal fun Avatar(name: String, colorHex: String, appearance: AppearanceSettings, size: Int) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)
    val shape = when (appearance.avatarShape) {
        AvatarShape.Circle -> CircleShape
        AvatarShape.Rounded -> RoundedCornerShape((size * .28f).dp)
        AvatarShape.Square -> RoundedCornerShape(4.dp)
    }
    Surface(Modifier.size(size.dp).clip(shape), shape = shape, color = color) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" }, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ActiveCallBar(muted: Boolean, onOpen: () -> Unit, onToggleMute: () -> Unit, onEnd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text("Активный звонок", fontWeight = FontWeight.Bold)
                Text("Можно продолжать переписку", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onToggleMute) { Icon(if (muted) Icons.Default.Call else Icons.Default.Call, "Микрофон") }
            IconButton(onClick = onEnd) { Icon(Icons.Default.Close, "Завершить") }
        }
    }
}

private fun sectionLabel(section: HomeSection) = when (section) {
    HomeSection.Chats -> "Чаты"
    HomeSection.People -> "Люди"
    HomeSection.Calls -> "Звонки"
    HomeSection.Settings -> "Настройки"
}

internal fun formatChatTime(value: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(value.take(19)) ?: Date()
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}.getOrDefault("")
