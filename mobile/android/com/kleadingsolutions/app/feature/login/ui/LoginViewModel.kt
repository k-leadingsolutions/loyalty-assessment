package com.kleadingsolutions.app.feature.login.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kleadingsolutions.app.feature.login.data.AuthRepository
import com.kleadingsolutions.app.feature.login.data.AuthResult
import com.kleadingsolutions.app.feature.login.data.TokenStore
import com.kleadingsolutions.app.feature.login.util.NetworkMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(
  private val authRepository: AuthRepository,
  private val tokenStore: TokenStore,
  private val networkMonitor: NetworkMonitor,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val lockoutThreshold: Int = 3,
  private val lockoutDurationMs: Long = 5 * 60 * 1000, // 5 minutes default
  private val nowProvider: () -> Long = { System.currentTimeMillis() } // injected clock for determinism
) : ViewModel() {

  private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(LoginUiState())
  val uiState: kotlinx.coroutines.flow.StateFlow<LoginUiState> = _uiState

  // navigation events (single-shot) - expose read-only SharedFlow
  private val _navigation = MutableSharedFlow<Unit>(replay = 0)
  val navigation: SharedFlow<Unit> = _navigation.asSharedFlow()

  fun onEmailChanged(email: String) {
    _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
  }

  fun onPasswordChanged(password: String) {
    _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
  }

  fun onRememberMeChanged(remember: Boolean) {
    _uiState.value = _uiState.value.copy(rememberMe = remember)
  }

  fun attemptLogin() {
    val state = _uiState.value

    // validation and lockout
    val now = nowProvider()
    if (state.isLocked(now)) {
      _uiState.value = state.copy(errorMessage = "Account locked. Try later.")
      return
    }
    if (state.email.isBlank() || state.password.isBlank()) {
      _uiState.value = state.copy(errorMessage = "Enter email and password")
      return
    }
    if (!networkMonitor.isOnline()) {
      _uiState.value = state.copy(errorMessage = "Offline. Please connect to network.")
      return
    }

    _uiState.value = state.copy(isLoading = true, errorMessage = null)

    viewModelScope.launch {
      // perform network IO on injected dispatcher for deterministic tests
      val res = withContext(ioDispatcher) {
        authRepository.login(state.email, state.password)
      }
      when (res) {
        is AuthResult.Success -> {
          if (state.rememberMe) {
            // persist token on IO dispatcher
            withContext(ioDispatcher) {
              tokenStore.saveToken(res.token)
            }
          }
          _uiState.value = _uiState.value.copy(isLoading = false, failureCount = 0, errorMessage = null)
          _navigation.emit(Unit)
        }
        is AuthResult.Failure -> {
          val newFailure = _uiState.value.failureCount + 1
          var lockedUntil: Long? = null
          var message = res.reason
          if (newFailure >= lockoutThreshold) {
            lockedUntil = nowProvider() + lockoutDurationMs
            message = "Too many failed attempts. Locked for ${lockoutDurationMs / 1000}s"
          }
          _uiState.value = _uiState.value.copy(
            isLoading = false,
            failureCount = newFailure,
            lockedUntilMillis = lockedUntil,
            errorMessage = message
          )
        }
      }
    }
  }
}