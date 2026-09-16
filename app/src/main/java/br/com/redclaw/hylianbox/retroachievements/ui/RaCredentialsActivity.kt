package br.com.redclaw.hylianbox.retroachievements.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.hylianbox.utils.ScaledAppCompatActivity
import br.com.redclaw.hylianbox.HylianBoxApp
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.databinding.ActivityRaCredentialsBinding
import br.com.redclaw.hylianbox.retroachievements.auth.RaApiKeyFetchResult
import br.com.redclaw.hylianbox.retroachievements.auth.RaApiKeyLoginHelper
import br.com.redclaw.hylianbox.retroachievements.auth.RaCredentialStore
import br.com.redclaw.hylianbox.ui.switchui.SwitchImmersive
import kotlinx.coroutines.launch

/**
 * Subtela segura para credenciais RetroAchievements.
 *
 * Toda a Window é marcada com FLAG_SECURE, então login/senha/API key ficam pretos em
 * gravação/screenshot, enquanto a SettingsActivity principal permanece gravável (clearFlags).
 * Isolamento por Window é a única forma de proteger só um elemento — FLAG_SECURE não existe por
 * View.
 */
class RaCredentialsActivity : ScaledAppCompatActivity() {

    private lateinit var binding: ActivityRaCredentialsBinding

    /** Last typed credentials, kept in memory only to feed the key capture. */
    private var lastUsername: String = ""
    private var lastPassword: String = ""

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            lastPassword = ""
            if (result.resultCode == RESULT_OK) {
                binding.settingsRaApiKey.text.clear()
                binding.settingsRaStatus.setText(R.string.settings_ra_api_key_auto_saved)
            }
            updateRaStatus(HylianBoxApp.raCredentialStore)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bloqueia captura só desta subtela.
        window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityRaCredentialsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        setSupportActionBar(binding.raCredentialsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_ra_credentials_title)
        binding.raCredentialsToolbar.setNavigationOnClickListener { finish() }

        setupCredentialsUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    private fun setupCredentialsUi() {
        val credentials = HylianBoxApp.raCredentialStore
        val authService = HylianBoxApp.raAuthService

        // Prefill API key mascarada (inputType textPassword).
        credentials.getApiKey()?.takeIf { it.isNotBlank() }?.let {
            binding.settingsRaApiKey.setText(it)
        }

        binding.settingsRaLogin.setOnClickListener {
            val username = binding.settingsRaUsername.text.toString().trim()
            val password = binding.settingsRaPassword.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_error_missing)
                return@setOnClickListener
            }
            // Kept in memory only so the capture fallback can auto-login.
            lastUsername = username
            lastPassword = password
            binding.settingsRaLogin.isEnabled = false
            binding.settingsRaStatus.setText(R.string.settings_ra_logging_in)
            lifecycleScope.launch {
                val result = authService.login(username, password)
                binding.settingsRaLogin.isEnabled = true
                binding.settingsRaPassword.text.clear()
                result.fold(
                        onSuccess = {
                            binding.settingsRaUsername.text.clear()
                            // Try to fetch the Web API key automatically with
                            // the credentials just typed; never blocks login.
                            binding.settingsRaStatus.setText(R.string.settings_ra_fetching_api_key)
                            when (RaApiKeyLoginHelper.fetchAfterLogin(
                                            applicationContext,
                                            credentials,
                                            username,
                                            password
                                    )
                            ) {
                                RaApiKeyFetchResult.Fetched ->
                                        binding.settingsRaStatus.setText(
                                                R.string.settings_ra_api_key_auto_saved
                                        )
                                RaApiKeyFetchResult.AlreadyStored,
                                RaApiKeyFetchResult.Unavailable -> Unit
                            }
                            updateRaStatus(credentials)
                        },
                        onFailure = { e ->
                            val detail = e.message?.takeIf { it.isNotBlank() }
                            binding.settingsRaStatus.text =
                                    if (detail != null) {
                                        getString(R.string.settings_ra_status_failed_detail, detail)
                                    } else {
                                        getString(R.string.settings_ra_status_failed)
                                    }
                        }
                )
            }
        }

        binding.settingsRaLogout.setOnClickListener {
            authService.logout()
            lastUsername = ""
            lastPassword = ""
            binding.settingsRaUsername.text.clear()
            binding.settingsRaPassword.text.clear()
            binding.settingsRaApiKey.text.clear()
            updateRaStatus(credentials)
        }

        binding.settingsRaSaveApiKey.setOnClickListener {
            val key = binding.settingsRaApiKey.text.toString().trim()
            if (key.isEmpty()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_api_key_error_missing)
                return@setOnClickListener
            }
            credentials.setApiKey(key)
            binding.settingsRaApiKey.text.clear()
            binding.settingsRaStatus.setText(R.string.settings_ra_api_key_saved)
            updateRaStatus(credentials)
        }

        binding.settingsRaCaptureApiKey.setOnClickListener {
            val username = lastUsername.ifBlank {
                credentials.getUsername()?.trim().orEmpty()
            }
            if (username.isBlank() || !credentials.hasCredentials()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_error_missing)
                return@setOnClickListener
            }
            captureLauncher.launch(
                RaKeyCaptureActivity.intent(this, username, lastPassword)
            )
        }

        updateRaStatus(credentials)
    }

    private fun updateRaStatus(credentials: RaCredentialStore) {
        val enabled = br.com.redclaw.hylianbox.utils.CorePrefs.getRetroAchievementsEnabled(this)
        binding.settingsRaStatus.setText(
                when {
                    !enabled -> R.string.settings_ra_status_disabled
                    credentials.hasCredentials() && credentials.hasApiKey() ->
                            R.string.settings_ra_status_logged_in
                    credentials.hasCredentials() -> R.string.settings_ra_status_logged_in_no_key
                    else -> R.string.settings_ra_status_logged_out
                }
        )
        val username = credentials.getUsername()?.trim().orEmpty()
        val showSubtitle = username.isNotEmpty() && credentials.hasCredentials()
        binding.settingsRaEnabledSubtitle.apply {
            if (showSubtitle) {
                text = getString(R.string.settings_ra_enabled_subtitle, username)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }
}
