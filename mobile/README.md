```markdown
# Mobile Login Component — README

Short summary
- This repo implements a minimal, testable Login component for Android (Kotlin + Jetpack Compose + MVVM) and iOS (Swift + SwiftUI + MVVM).
- Focus: clear state management, deterministic async behavior, and small, focused tests that mirror the same functional requirements on both platforms:
    - Validation enables/disables the Login button
    - Successful login emits a navigation event
    - Errors increment failure count
    - Lockout after 3 failures
    - Offline path shows message and prevents network call
    - Remember-me persists token when requested

What’s included (high level)
- Android (mobile/android)
    - UI: `LoginScreen.kt` (Compose) — includes test tags for deterministic UI tests
    - State: `LoginUiState.kt`
    - ViewModel: `LoginViewModel.kt`
    - Network / Data contracts: `AuthRepository`, `AuthToken`, `TokenStore`, `NetworkMonitor`
    - Tests:
        - Unit: `LoginViewModelTest.kt`
        - Compose UI test: `LoginScreenTest.kt` (user interactions)
- iOS (ios-app / Swift Package)
    - UI: `LoginView.swift` (SwiftUI)
    - State: `LoginState.swift`
    - ViewModel: `LoginViewModel.swift` (async/await, @MainActor)
    - Network / Data contracts: `AuthService.swift`, `TokenStore.swift`, `NetworkMonitor.swift`
    - Tests: `LoginViewModelTests.swift` (XCTest async tests and test doubles like `FakeAuthServiceSpy`, `InMemoryTokenStore`, `FakeNetworkMonitor`)

Design approach (why and how)
- MVVM on both platforms:
    - ViewModels orchestrate business logic and side-effects via small, testable abstractions (repositories/services). 
    - The UI remains passive and drives state updates only.
- Small abstraction boundaries for dependencies(repos/services):
    - `AuthRepository` / `AuthService`, `TokenStore`, and `NetworkMonitor` 
    - Each has in-memory or spy implementations for tests to avoid real network/disk access.
- Deterministic async:
    - Android: ViewModel accepts an `ioDispatcher` (test dispatcher) so tests control coroutine scheduling.
    - iOS: Tests use `async` XCTest and in-memory/spy implementations; ViewModel marked `@MainActor` and tests `await` `attemptLogin()` to make async behavior deterministic.

How to run tests locally

Android
1. Change directory:
   cd mobile/android
2. Run tests:
     ```sh
   ./gradlew clean test --no-daemon --warning-mode=all --stacktrace
    ```
   Notes:
- Commit the Gradle wrapper files so CI uses the exact Gradle version: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
- If Compose UI tests run on the JVM, ensure `androidx.compose.ui:ui-test-junit4` and `robolectric` are in `testImplementation`.

iOS (Swift Package)
1. Change directory:
   cd ios-app
2. Run tests:
   ```sh
   swift test --enable-test-discovery
    ```
   Notes:
- Tests use in-memory and spy doubles for all network and persistence actions—no real network or disk access.
- If using Xcode, open the package and run the LoginTests scheme.

Key files:
- Android:
    - mobile/android/src/main/kotlin/com/kleadingsolutions/app/feature/login/ui/LoginScreen.kt (testTag usage)
    - mobile/android/src/main/kotlin/com/kleadingsolutions/app/feature/login/ui/LoginViewModel.kt
    - mobile/android/src/test/kotlin/.../LoginScreenViewModelTest.kt (Compose + VM integration)
    - mobile/android/src/test/kotlin/.../LoginViewModelTest.kt (pure VM tests)
- iOS:
    - ios-app/Sources/Login/LoginViewModel.swift
    - ios-app/Sources/Login/LoginView.swift
    - ios-app/Sources/Login/LoginState.swift
    - ios-app/Tests/LoginTests/LoginViewModelTests.swift
    - ios-app/Tests/LoginTests/Support/FakeAuthServiceSpy.swift (spy)

Trade-offs & limitations:
- Simplicity over completeness: tests and implementations focus on the brief — no networking code, no secure disk persistence, no analytics.
- Token persistence interface only: `TokenStore`/`TokenStore.kt` and `TokenStore.swift` are abstractions; actual secure storage (Keystore, Keychain) is out of scope.
- No end-to-end UI automation: Used ViewModel-backed Compose tests (Android) and XCTest ViewModel tests (iOS) for high signal and low flakiness.

Future improvements:
- Android:
    - Add Espresso UI tests running on an emulator/device for end-to-end validation.
    - Integrate Hilt for dependency injection to manage ViewModel and dependencies.
- iOS:
    - Add UI tests using XCUITest to validate the full user flow.
    - Integrate a DI framework like Swinject for better dependency management.

Contact
- keamp84@gmail.com — for questions or clarifications.
```