package app.takt.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    busy: Boolean,
    confirmationEmail: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGoogle: () -> Unit,
    onDismissConfirmation: () -> Unit,
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Waves, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                }
            }
            Text("такт", style = MaterialTheme.typography.displaySmall)
            Text(
                if (registering) "Создайте защищённый аккаунт" else "Ваши сообщения, голос и звонки",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (registering) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Как вас зовут") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Gmail или другой email") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, null) },
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Пароль") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    if (registering) onSignUp(email, password, displayName) else onSignIn(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (registering) "Создать аккаунт" else "Войти")
            }
            OutlinedButton(onClick = onGoogle, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text("Продолжить через Google")
            }
            TextButton(onClick = { registering = !registering }, enabled = !busy) {
                Text(if (registering) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
            }
            Text(
                "После регистрации придёт письмо подтверждения. Пароль не передаётся другим пользователям.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmationEmail != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissConfirmation,
            confirmButton = { TextButton(onClick = onDismissConfirmation) { Text("Понятно") } },
            icon = { Icon(Icons.Default.CheckCircle, null) },
            title = { Text("Подтвердите email") },
            text = { Text("Мы отправили письмо на $confirmationEmail. Откройте ссылку из письма и затем войдите.") },
        )
    }
}
