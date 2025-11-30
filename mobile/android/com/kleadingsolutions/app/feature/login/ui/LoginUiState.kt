package com.kleadingsolutions.app.feature.login.ui;

data class LoginUiState(
  val email: String = "",
  val password: String = "",
  val rememberMe: Boolean = false,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val failureCount: Int = 0,
  val lockedUntilMillis: Long? = null
) {
  fun isLocked(nowMillis: Long = System.currentTimeMillis()): Boolean =
    lockedUntilMillis?.let { nowMillis < it } ?: false

  fun isLoginEnabled(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !isLoading &&
            !isLocked(nowMillis) &&
            email.isNotBlank() &&
            password.isNotBlank()
}