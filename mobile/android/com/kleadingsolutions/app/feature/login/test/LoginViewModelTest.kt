package com.kleadingsolutions.app.feature.login.test;


import com.kleadingsolutions.app.feature.login.data.AuthResult
import com.kleadingsolutions.app.feature.login.data.AuthToken
import com.kleadingsolutions.app.feature.login.data.AuthRepository
import com.kleadingsolutions.app.feature.login.data.TokenStore
import com.kleadingsolutions.app.feature.login.ui.LoginViewModel
import com.kleadingsolutions.app.feature.login.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)

  private lateinit var authRepo: AuthRepository
  private lateinit var tokenStore: TokenStore
  private lateinit var networkMonitor: NetworkMonitor
  private lateinit var vm: LoginViewModel

  @Before
  fun setup() {
    authRepo = mockk()
    tokenStore = mockk(relaxed = true)
    networkMonitor = mockk()
    Dispatchers.setMain(testDispatcher)
    vm = LoginViewModel(authRepo, tokenStore, networkMonitor, testDispatcher, lockoutThreshold = 3, lockoutDurationMs = 1000)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `validation enables and disables login`() = testScope.runTest {
    // initial state: empty -> disabled
    assertTrue(!vm.uiState.value.isLoginEnabled())

    vm.onEmailChanged("a@b.com")
    vm.onPasswordChanged("pwd")
    // Now should be enabled (assuming network not considered for this test)
    assertTrue(vm.uiState.value.isLoginEnabled())
  }

  @Test
  fun `success emits navigation and persists token when remember me`() = testScope.runTest {
    vm.onEmailChanged("a@b.com")
    vm.onPasswordChanged("pw")
    vm.onRememberMeChanged(true)

    every { networkMonitor.isOnline() } returns true
    coEvery { authRepo.login(any(), any()) } returns AuthResult.Success(AuthToken("tok-1", System.currentTimeMillis() + 10000))

    vm.attemptLogin()
    // advance dispatcher to allow coroutine
    testDispatcher.scheduler.advanceUntilIdle()

    // verify token saved
    coVerify { tokenStore.saveToken(any()) }
    assertEquals(0, vm.uiState.value.failureCount)

    // verify navigation emitted
    val navEvent = withTimeout(1000L) { vm.navigation.first() }
    // If first() returns Unit, navEvent will be Unit; you can assert it's non-null or just that it didn't time out
  }

  @Test
  fun `error increments failure count and locks after threshold`() = testScope.runTest {
    vm.onEmailChanged("a@b.com")
    vm.onPasswordChanged("pw")

    coEvery { networkMonitor.isOnline() } returns true
    coEvery { authRepo.login(any(), any()) } returnsMany listOf(
      AuthResult.Failure("Bad creds"),
      AuthResult.Failure("Bad creds"),
      AuthResult.Failure("Bad creds")
    )

    // 1st attempt
    vm.attemptLogin()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(1, vm.uiState.value.failureCount)

    // 2nd attempt
    vm.attemptLogin()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(2, vm.uiState.value.failureCount)

    // 3rd attempt -> lock
    vm.attemptLogin()
    testDispatcher.scheduler.advanceUntilIdle()
    assertEquals(3, vm.uiState.value.failureCount)
    assertTrue(vm.uiState.value.isLocked())
  }

  @Test
  fun `offline shows message and does not call authRepo`() = testScope.runTest {
    vm.onEmailChanged("a@b.com")
    vm.onPasswordChanged("pw")

    coEvery { networkMonitor.isOnline() } returns false

    vm.attemptLogin()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) { authRepo.login(any(), any()) }
    assertEquals("Offline. Please connect to network.", vm.uiState.value.errorMessage)
  }
}