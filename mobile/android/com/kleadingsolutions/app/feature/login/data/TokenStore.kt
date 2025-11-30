package com.kleadingsolutions.app.feature.login.data;

interface TokenStore {
  suspend fun saveToken(token: AuthToken)
  suspend fun getToken(): AuthToken?
}