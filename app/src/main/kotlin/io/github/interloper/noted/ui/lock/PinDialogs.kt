package io.github.interloper.noted.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.interloper.noted.R
import io.github.interloper.noted.security.PinHasher
import io.github.interloper.noted.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= PinHasher.MAX_LENGTH && new.all { it.isDigit() }) onValueChange(new) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

/** Asks for the current PIN and calls [onVerified] once it matches. */
@Composable
fun PinEntryDialog(settings: Settings, title: String, onVerified: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            PinField(pin, { pin = it; wrong = false }, stringResource(R.string.pin), if (wrong) stringResource(R.string.pin_wrong) else null)
        },
        confirmButton = {
            TextButton(
                enabled = pin.isNotEmpty() && !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        // Hashing is deliberately slow; keep it off the main thread.
                        val ok = withContext(Dispatchers.Default) {
                            PinHasher.verify(pin, settings.pinHash.orEmpty(), settings.pinSalt.orEmpty())
                        }
                        checking = false
                        if (ok) onVerified() else wrong = true
                    }
                },
            ) { Text(stringResource(R.string.unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks for a new PIN twice and returns its hash through [onPinChosen]. */
@Composable
fun PinSetupDialog(title: String, onPinChosen: (PinHasher.Hashed) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mismatch = confirm.isNotEmpty() && confirm != pin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.pin_setup_hint, PinHasher.MIN_LENGTH, PinHasher.MAX_LENGTH))
                Spacer(Modifier.height(12.dp))
                PinField(pin, { pin = it }, stringResource(R.string.pin_new))
                Spacer(Modifier.height(8.dp))
                PinField(confirm, { confirm = it }, stringResource(R.string.pin_confirm), if (mismatch) stringResource(R.string.pin_mismatch) else null)
            }
        },
        confirmButton = {
            TextButton(
                enabled = PinHasher.isValidPin(pin) && pin == confirm && !working,
                onClick = {
                    working = true
                    scope.launch {
                        val hashed = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
                        onPinChosen(hashed)
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
