package com.kleadingsolutions.app.feature.login.data;

data class AuthToken(val token: String, val expiresAtMillis: Long)

sealed interface AuthResult {
  data class Success(val token: AuthToken): AuthResult
  data class Failure(val reason: String): AuthResult
}

interface AuthRepository {
  suspend fun login(email: String, password: String): AuthResult
}