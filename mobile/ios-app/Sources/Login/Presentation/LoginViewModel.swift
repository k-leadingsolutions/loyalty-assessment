//
// Created by Keabetswe Mputle on 2025/11/28.
//

import Foundation
import Combine

@MainActor
public class LoginViewModel: ObservableObject {
    @Published public private(set) var state = LoginState()
    private let authService: AuthService
    private let tokenStore: TokenStore
    private let networkMonitor: NetworkMonitor

    // private subject, expose as read-only AnyPublisher
    private let navigationSubject = PassthroughSubject<Void, Never>()
    public let navigation: AnyPublisher<Void, Never>

    private let lockoutThreshold: Int
    private let lockoutDurationSeconds: TimeInterval
    private let dateProvider: () -> Date

    public init(
        authService: AuthService,
        tokenStore: TokenStore,
        networkMonitor: NetworkMonitor,
        lockoutThreshold: Int = 3,
        lockoutDurationSeconds: TimeInterval = 300,
        dateProvider: @escaping () -> Date = { Date() } // injected for deterministic tests
    ) {
        self.authService = authService
        self.tokenStore = tokenStore
        self.networkMonitor = networkMonitor
        self.lockoutThreshold = lockoutThreshold
        self.lockoutDurationSeconds = lockoutDurationSeconds
        self.dateProvider = dateProvider
        self.navigation = navigationSubject.eraseToAnyPublisher()
    }

    public func onEmailChanged(_ email: String) {
        state.email = email
        state.errorMessage = nil
    }

    public func onPasswordChanged(_ password: String) {
        state.password = password
        state.errorMessage = nil
    }

    public func onRememberChanged(_ remember: Bool) {
        state.rememberMe = remember
    }

    public func attemptLogin() async {
        let now = dateProvider()
        if state.isLocked(now: now) {
            state.errorMessage = "Account locked. Try later."
            return
        }
        if state.email.isEmpty || state.password.isEmpty {
            state.errorMessage = "Enter email and password"
            return
        }
        if !networkMonitor.isOnline() {
            state.errorMessage = "Offline. Please connect to network."
            return
        }

        state.isLoading = true
        do {
            let token = try await authService.login(email: state.email, password: state.password)
            if state.rememberMe {
                // in-memory
                try? await tokenStore.saveToken(token)
            }
            state.failureCount = 0
            state.isLoading = false
            navigationSubject.send(())
        } catch {
            state.failureCount += 1
            state.isLoading = false
            if state.failureCount >= lockoutThreshold {
                state.lockedUntil = dateProvider().addingTimeInterval(lockoutDurationSeconds)
                state.errorMessage = "Too many failed attempts. Locked for \(Int(lockoutDurationSeconds))s"
            } else {
                state.errorMessage = "Login failed"
            }
        }
    }
}