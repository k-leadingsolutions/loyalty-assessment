package com.kleadingsolutions.app.feature.login.util;

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {

  fun isOnline(): Boolean
  fun networkStateFlow(): Flow<Boolean>
}