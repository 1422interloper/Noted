package io.github.interloper.noted.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.interloper.noted.R
import io.github.interloper.noted.security.LockState
import io.github.interloper.noted.settings.Settings

fun canUseBiometrics(activity: android.content.Context): Boolean =
    BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

private fun showBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    usePinLabel: String,
    onSuccess: () -> Unit,
    onUsePin: () -> Unit,
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // The negative button is "Use PIN"; lockout and missing hardware also fall back to the PIN.
            // Dismissing the prompt (back, tap outside) just cancels.
            when (errorCode) {
                BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> Unit
                else -> onUsePin()
            }
        }
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(usePinLabel)
        .setAllowedAuthenticators(BIOMETRIC_WEAK)
        .build()
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
}

/**
 * Returns a function that runs its argument once locked notes are viewable: immediately if already unlocked, otherwise
 * after the user passes the fingerprint prompt or enters the PIN. Does nothing if no PIN has been set. The PIN dialog
 * is hosted here, so call this once per screen.
 */
@Composable
fun rememberUnlockRequester(settings: Settings, lockState: LockState): (onUnlocked: () -> Unit) -> Unit {
    val activity = LocalActivity.current as? FragmentActivity
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val title = stringResource(R.string.unlock_title)
    val usePin = stringResource(R.string.use_pin)

    pending?.let { action ->
        PinEntryDialog(
            settings = settings,
            title = title,
            onVerified = {
                lockState.unlock()
                pending = null
                action()
            },
            onDismiss = { pending = null },
        )
    }

    return { action ->
        when {
            lockState.unlocked.value -> action()
            !settings.hasPin -> Unit
            settings.biometricUnlock && activity != null && canUseBiometrics(activity) -> showBiometricPrompt(
                activity = activity,
                title = title,
                usePinLabel = usePin,
                onSuccess = {
                    lockState.unlock()
                    action()
                },
                onUsePin = { pending = action },
            )
            else -> pending = action
        }
    }
}
