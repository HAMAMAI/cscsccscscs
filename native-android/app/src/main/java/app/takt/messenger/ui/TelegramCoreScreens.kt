package app.takt.messenger.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * UI-only models for the Telegram-like core screens.  Keeping them independent
 * from the repository models lets the app map API data without coupling these
 * composables to a particular database response.
 */
data class TaktSelfProfileUi(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val avatarColor: String = "#8C78FF",
    /** A real, already generated public link. The UI never invents a fake one. */
    val publicLink: String,
)

data class TaktPersonProfileUi(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val about: String = "",
    val avatarColor: String = "#8C78FF",
    val statusText: String = "был(а) недавно",
    val publicLink: String? = null,
    val isBlocked: Boolean = false,
)

data class TaktBlockedUserUi(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val avatarColor: String = "#8C78FF",
    val blockedAtText: String = "",
)

data class TaktFolderUi(
    val id: String,
    val name: String,
    val color: String = "#8C78FF",
    val position: Int = 0,
    val chatCount: Int = 0,
)

enum class TaktSearchResultKind { Chat, Person, Message, File, Call }

data class TaktSearchResultUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: TaktSearchResultKind,
    val avatarColor: String = "#8C78FF",
    val timeLabel: String? = null,
)

enum class TaktPrivacyAudience(val title: String) {
    Everyone("Все"),
    Contacts("Контакты"),
    Nobody("Никто"),
}

data class TaktPrivacyUi(
    val showAvatarTo: TaktPrivacyAudience = TaktPrivacyAudience.Everyone,
    val showLastSeenTo: TaktPrivacyAudience = TaktPrivacyAudience.Everyone,
    val allowCallsFrom: TaktPrivacyAudience = TaktPrivacyAudience.Everyone,
    val allowMessagesFrom: TaktPrivacyAudience = TaktPrivacyAudience.Everyone,
    val allowGroupInvitesFrom: TaktPrivacyAudience = TaktPrivacyAudience.Everyone,
)

private val TaktTeal = Color(0xFF5ED8C7)
private val TaktPurple = Color(0xFF8C78FF)
private val TaktCoral = Color(0xFFFF817A)

/**
 * Profile action sheet with the same large dark-card rhythm as the supplied
 * reference, but using Takt's mint/purple identity rather than Telegram UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaktSelfProfileActionSheet(
    profile: TaktSelfProfileUi,
    onDismissRequest: () -> Unit,
    onChangeColor: () -> Unit,
    onChangeName: () -> Unit,
    onCopyProfileLink: (String) -> Unit,
    onShareProfileLink: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(304.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(TaktTeal, TaktPurple, TaktCoral),
                            ),
                        ),
                )
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    TaktSheetAction(
                        icon = Icons.Default.ColorLens,
                        title = "Изменить цвет профиля",
                        subtitle = "Выберите свой цвет",
                        onClick = onChangeColor,
                    )
                    TaktSheetAction(
                        icon = Icons.Default.Edit,
                        title = "Изменить имя",
                        subtitle = profile.displayName,
                        onClick = onChangeName,
                    )
                    TaktSheetAction(
                        icon = Icons.Default.ContentCopy,
                        title = "Копировать ссылку",
                        subtitle = profile.publicLink,
                        onClick = { onCopyProfileLink(profile.publicLink) },
                    )
                    TaktSheetAction(
                        icon = Icons.Default.Share,
                        title = "Поделиться профилем",
                        subtitle = "Отправить ссылку в другое приложение",
                        onClick = { onShareProfileLink(profile.publicLink) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaktPersonProfileScreen(
    profile: TaktPersonProfileUi,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onBlock: (String) -> Unit,
    onUnblock: (String) -> Unit,
    onReport: (String) -> Unit,
    onCopyProfileLink: (String) -> Unit,
) {
    var confirmBlock by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TaktAvatar(
                    title = profile.displayName,
                    color = profile.avatarColor,
                    size = 92,
                )
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    profile.username?.takeIf(String::isNotBlank)?.let {
                        Text("@$it", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(profile.statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaktRoundAction(Icons.Default.Chat, "Написать", onMessage)
                    TaktRoundAction(Icons.Default.Call, "Аудио", onAudioCall)
                    TaktRoundAction(Icons.Default.VideoCall, "Видео", onVideoCall)
                }
            }
            if (profile.about.isNotBlank()) {
                item {
                    TaktInfoCard("О себе", profile.about)
                }
            }
            profile.username?.takeIf(String::isNotBlank)?.let { username ->
                item {
                    TaktInfoCard("Имя пользователя", "@$username")
                }
            }
            if (!profile.publicLink.isNullOrBlank()) {
                item {
                    TaktClickableRow(
                        icon = Icons.Default.Link,
                        title = "Копировать ссылку на профиль",
                        subtitle = profile.publicLink,
                        onClick = { onCopyProfileLink(profile.publicLink) },
                    )
                }
            }
            item {
                TaktClickableRow(
                    icon = if (profile.isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                    title = if (profile.isBlocked) "Разблокировать" else "Заблокировать",
                    subtitle = if (profile.isBlocked) "Снова разрешить сообщения и звонки" else "Запретить сообщения, звонки и приглашения",
                    danger = !profile.isBlocked,
                    onClick = {
                        if (profile.isBlocked) onUnblock(profile.id) else confirmBlock = true
                    },
                )
            }
            item {
                TaktClickableRow(
                    icon = Icons.Default.Report,
                    title = "Пожаловаться",
                    subtitle = "Отправить жалобу модерации",
                    danger = true,
                    onClick = { onReport(profile.id) },
                )
            }
        }
    }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Заблокировать пользователя?") },
            text = { Text("${profile.displayName} не сможет писать вам, звонить и приглашать в группы.") },
            confirmButton = {
                TextButton(onClick = {
                    onBlock(profile.id)
                    confirmBlock = false
                }) { Text("Заблокировать", color = TaktCoral) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaktPrivacyScreen(
    privacy: TaktPrivacyUi,
    onBack: () -> Unit,
    onPrivacyChange: (TaktPrivacyUi) -> Unit,
    onOpenBlockedUsers: () -> Unit,
) {
    var editor by remember { mutableStateOf<TaktPrivacyField?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Конфиденциальность") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TaktSectionTitle("Кто видит информацию") }
            item {
                TaktPrivacyRow(
                    icon = Icons.Default.Visibility,
                    title = "Фото и цвет профиля",
                    audience = privacy.showAvatarTo,
                    onClick = { editor = TaktPrivacyField.Avatar },
                )
            }
            item {
                TaktPrivacyRow(
                    icon = Icons.Default.Visibility,
                    title = "Время последнего посещения",
                    audience = privacy.showLastSeenTo,
                    onClick = { editor = TaktPrivacyField.LastSeen },
                )
            }
            item { TaktSectionTitle("Кто может связаться") }
            item {
                TaktPrivacyRow(
                    icon = Icons.Default.Chat,
                    title = "Сообщения",
                    audience = privacy.allowMessagesFrom,
                    onClick = { editor = TaktPrivacyField.Messages },
                )
            }
            item {
                TaktPrivacyRow(
                    icon = Icons.Default.Call,
                    title = "Звонки",
                    audience = privacy.allowCallsFrom,
                    onClick = { editor = TaktPrivacyField.Calls },
                )
            }
            item {
                TaktPrivacyRow(
                    icon = Icons.Default.Group,
                    title = "Приглашения в группы",
                    audience = privacy.allowGroupInvitesFrom,
                    onClick = { editor = TaktPrivacyField.Groups },
                )
            }
            item {
                TaktClickableRow(
                    icon = Icons.Default.PersonOff,
                    title = "Чёрный список",
                    subtitle = "Заблокированные пользователи",
                    onClick = onOpenBlockedUsers,
                )
            }
            item {
                Text(
                    "Заблокированные люди не смогут писать, звонить или приглашать вас в группы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
    editor?.let { field ->
        TaktAudienceDialog(
            title = field.title,
            selected = field.current(privacy),
            onDismiss = { editor = null },
            onConfirm = { audience ->
                onPrivacyChange(field.update(privacy, audience))
                editor = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaktBlockedUsersScreen(
    users: List<TaktBlockedUserUi>,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onUnblock: (String) -> Unit,
    onAddBlock: () -> Unit,
    isLoading: Boolean = false,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Чёрный список") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = onAddBlock) { Icon(Icons.Default.Add, "Заблокировать пользователя") } },
            )
        },
    ) { padding ->
        if (users.isEmpty() && !isLoading) {
            TaktEmptyState(
                icon = Icons.Default.PersonOff,
                title = "Чёрный список пуст",
                subtitle = "Заблокированные пользователи появятся здесь.",
                actionLabel = "Заблокировать",
                onAction = onAddBlock,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        if (isLoading) "Загрузка списка…" else "${users.size} ${pluralUsers(users.size)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(users, key = { it.id }) { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProfile(user.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TaktAvatar(user.displayName, user.avatarColor, 48)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    user.username?.let { "@$it" } ?: "Без имени пользователя",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (user.blockedAtText.isNotBlank()) {
                                    Text(user.blockedAtText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            TextButton(onClick = { onUnblock(user.id) }) { Text("Снять") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaktFoldersScreen(
    folders: List<TaktFolderUi>,
    onBack: () -> Unit,
    onOpenAllChats: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: (name: String, color: String) -> Unit,
    onUpdateFolder: (id: String, name: String, color: String) -> Unit,
    onDeleteFolder: (String) -> Unit,
) {
    var editor by remember { mutableStateOf<TaktFolderUi?>(null) }
    var createNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaktFolderUi?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Папки чатов") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = { createNew = true }) { Icon(Icons.Default.Add, "Создать папку") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Text(
                    "Собирайте важные чаты в отдельные вкладки.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                TaktFolderRow(
                    title = "Все чаты",
                    color = "#5ED8C7",
                    count = null,
                    onOpen = onOpenAllChats,
                    onMore = null,
                )
            }
            items(folders.sortedBy { it.position }, key = { it.id }) { folder ->
                TaktFolderRow(
                    title = folder.name,
                    color = folder.color,
                    count = folder.chatCount,
                    onOpen = { onOpenFolder(folder.id) },
                    onMore = { editor = folder },
                )
            }
            item {
                OutlinedButton(
                    onClick = { createNew = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Новая папка")
                }
            }
        }
    }
    if (createNew) {
        TaktFolderEditorDialog(
            initial = null,
            onDismiss = { createNew = false },
            onSave = { name, color ->
                onCreateFolder(name, color)
                createNew = false
            },
        )
    }
    editor?.let { folder ->
        TaktFolderEditorDialog(
            initial = folder,
            onDismiss = { editor = null },
            onSave = { name, color ->
                onUpdateFolder(folder.id, name, color)
                editor = null
            },
            onDelete = { deleting = folder },
        )
    }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить папку «${folder.name}»?") },
            text = { Text("Чаты останутся в мессенджере, исчезнет только папка.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(folder.id)
                    deleting = null
                    editor = null
                }) { Text("Удалить", color = TaktCoral) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaktGlobalSearchScreen(
    query: String,
    results: List<TaktSearchResultUi>,
    recentQueries: List<String>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenResult: (TaktSearchResultUi) -> Unit,
    onClearRecent: () -> Unit,
    isLoading: Boolean = false,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск чатов, людей и сообщений") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (query.isNotBlank()) {
                            { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Очистить") } }
                        } else {
                            null
                        },
                        singleLine = true,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when {
            isLoading -> TaktEmptyState(
                icon = Icons.Default.Search,
                title = "Ищем…",
                subtitle = "Проверяем чаты, людей и сообщения.",
                modifier = Modifier.padding(padding),
            )
            query.isBlank() && recentQueries.isNotEmpty() -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Недавние запросы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearRecent) { Text("Очистить") }
                    }
                }
                items(recentQueries, key = { it }) { recent ->
                    TaktClickableRow(
                        icon = Icons.Default.Search,
                        title = recent,
                        subtitle = "Повторить поиск",
                        onClick = { onQueryChange(recent) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .24f))
                }
            }
            query.isBlank() -> TaktEmptyState(
                icon = Icons.Default.Search,
                title = "Найдите нужное",
                subtitle = "Ищите чаты, людей, файлы и слова из сообщений.",
                modifier = Modifier.padding(padding),
            )
            query.isNotBlank() && results.isEmpty() -> TaktEmptyState(
                icon = Icons.Default.Search,
                title = "Ничего не найдено",
                subtitle = "Попробуйте имя, @username или слова из сообщения.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text("Результаты", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results, key = { "${it.kind}:${it.id}" }) { result ->
                    TaktSearchResultRow(result = result, onClick = { onOpenResult(result) })
                }
            }
        }
    }
}

@Composable
private fun TaktSheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(min = 300.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .16f)) {
            Icon(icon, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaktRoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(82.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(54.dp)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TaktInfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value)
        }
    }
}

@Composable
private fun TaktClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TaktPrivacyRow(icon: ImageVector, title: String, audience: TaktPrivacyAudience, onClick: () -> Unit) {
    TaktClickableRow(
        icon = icon,
        title = title,
        subtitle = audience.title,
        onClick = onClick,
    )
}

@Composable
private fun TaktAudienceDialog(
    title: String,
    selected: TaktPrivacyAudience,
    onDismiss: () -> Unit,
    onConfirm: (TaktPrivacyAudience) -> Unit,
) {
    var current by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaktPrivacyAudience.entries.forEach { audience ->
                    AssistChip(
                        onClick = { current = audience },
                        label = { Text(audience.title) },
                        leadingIcon = if (current == audience) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private enum class TaktPrivacyField(val title: String) {
    Avatar("Кто видит фото и цвет профиля"),
    LastSeen("Кто видит время посещения"),
    Messages("Кто может писать сообщения"),
    Calls("Кто может звонить"),
    Groups("Кто может приглашать в группы");

    fun current(privacy: TaktPrivacyUi): TaktPrivacyAudience = when (this) {
        Avatar -> privacy.showAvatarTo
        LastSeen -> privacy.showLastSeenTo
        Messages -> privacy.allowMessagesFrom
        Calls -> privacy.allowCallsFrom
        Groups -> privacy.allowGroupInvitesFrom
    }

    fun update(privacy: TaktPrivacyUi, audience: TaktPrivacyAudience): TaktPrivacyUi = when (this) {
        Avatar -> privacy.copy(showAvatarTo = audience)
        LastSeen -> privacy.copy(showLastSeenTo = audience)
        Messages -> privacy.copy(allowMessagesFrom = audience)
        Calls -> privacy.copy(allowCallsFrom = audience)
        Groups -> privacy.copy(allowGroupInvitesFrom = audience)
    }
}

@Composable
private fun TaktFolderRow(
    title: String,
    color: String,
    count: Int?,
    onOpen: () -> Unit,
    onMore: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(13.dp), color = taktColor(color).copy(alpha = .2f)) {
                Icon(
                    if (count == null) Icons.Default.FolderOpen else Icons.Default.Folder,
                    null,
                    tint = taktColor(color),
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                count?.let { Text("$it ${pluralChats(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            onMore?.let { more ->
                IconButton(onClick = more) { Icon(Icons.Default.MoreVert, "Настроить папку") }
            }
        }
    }
}

@Composable
private fun TaktFolderEditorDialog(
    initial: TaktFolderUi?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: "#8C78FF") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая папка" else "Настроить папку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Цвет", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf("#5ED8C7", "#8C78FF", "#FF817A", "#FFC857", "#5D9CFF").forEach { candidate ->
                        Surface(
                            shape = CircleShape,
                            color = taktColor(candidate),
                            modifier = Modifier
                                .size(if (color == candidate) 34.dp else 28.dp)
                                .clickable { color = candidate },
                        ) {
                            if (color == candidate) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), color) },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete -> TextButton(onClick = delete) { Text("Удалить", color = TaktCoral) } }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun TaktSearchResultRow(result: TaktSearchResultUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (result.kind == TaktSearchResultKind.Person || result.kind == TaktSearchResultKind.Chat) {
                TaktAvatar(result.title, result.avatarColor, 46)
            } else {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .16f)) {
                    Icon(searchKindIcon(result.kind), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            result.timeLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TaktEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(18.dp).size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun TaktAvatar(title: String, color: String, size: Int) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = taktColor(color),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (size >= 80) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TaktSectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 7.dp, top = 4.dp, end = 7.dp),
    )
}

private fun searchKindIcon(kind: TaktSearchResultKind): ImageVector = when (kind) {
    TaktSearchResultKind.Chat -> Icons.Default.Chat
    TaktSearchResultKind.Person -> Icons.Default.PersonOff
    TaktSearchResultKind.Message -> Icons.Default.Send
    TaktSearchResultKind.File -> Icons.Default.Folder
    TaktSearchResultKind.Call -> Icons.Default.Call
}

private fun taktColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(TaktPurple)

private fun pluralUsers(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "пользователь"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "пользователя"
    else -> "пользователей"
}

private fun pluralChats(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "чат"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "чата"
    else -> "чатов"
}
