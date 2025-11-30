//
//  LoginViewModelTests.swift
//  LoginTests
//
//  Created by Keabetswe Mputle on 2025/11/28.
//

import XCTest
@testable import Login
import Combine

// NOTE: FakeAuthServiceSpy should live in Tests/LoginTests/Support/FakeAuthServiceSpy.swift

final class InMemoryTokenStore: TokenStore {
    private var token: AuthToken?
    func saveToken(_ token: AuthToken) async {
        self.token = token
    }
    func getToken() async -> AuthToken? { token }
}

final class FakeNetworkMonitor: NetworkMonitor {
    private let online: Bool
    init(online: Bool) { self.online = online }
    func isOnline() -> Bool { online }
}

final class LoginViewModelTests: XCTestCase {
    private var cancellables = Set<AnyCancellable>()

    override func tearDown() {
        cancellables.removeAll()
        super.tearDown()
    }

    func testValidationEnablesButton() async {
        let svc = FakeAuthServiceSpy()
        let store = InMemoryTokenStore()
        let net = FakeNetworkMonitor(online: true)

        // Initialize the @MainActor ViewModel on the main actor
        let vm = await MainActor.run {
            LoginViewModel(authService: svc, tokenStore: store, networkMonitor: net)
        }

        // Access actor-isolated state on the main actor
        await MainActor.run {
            XCTAssertFalse(vm.state.isLoginEnabled())
        }

        // Call actor-isolated mutating methods (implicitly async)
        await vm.onEmailChanged("a@b.com")
        await vm.onPasswordChanged("pw")

        await MainActor.run {
            XCTAssertTrue(vm.state.isLoginEnabled())
        }
    }

    func testSuccessNavigationAndRememberMe() async {
        let svc = FakeAuthServiceSpy()
        let store = InMemoryTokenStore()
        let net = FakeNetworkMonitor(online: true)
        let token = AuthToken(token: "abc", expiresAt: Date().addingTimeInterval(1000))
        svc.result = .success(token)

        let vm = await MainActor.run {
            LoginViewModel(authService: svc, tokenStore: store, networkMonitor: net)
        }

        await vm.onEmailChanged("a@b.com")
        await vm.onPasswordChanged("pw")
        await vm.onRememberChanged(true)

        let exp = expectation(description: "nav")

        // subscribe to navigation on the main actor and store the cancellable
        await MainActor.run {
            vm.navigation
            .sink { _ in exp.fulfill() }
            .store(in: &cancellables)
        }

        await vm.attemptLogin()

        await fulfillment(of: [exp], timeout: 1.0)

        let saved = await store.getToken()
        XCTAssertNotNil(saved)
    }

    func testRememberMeFalseDoesNotPersistToken() async {
        let svc = FakeAuthServiceSpy()
        let store = InMemoryTokenStore()
        let net = FakeNetworkMonitor(online: true)
        let token = AuthToken(token: "xyz", expiresAt: Date().addingTimeInterval(1000))
        svc.result = .success(token)

        let vm = await MainActor.run {
            LoginViewModel(authService: svc, tokenStore: store, networkMonitor: net)
        }

        await vm.onEmailChanged("a@b.com")
        await vm.onPasswordChanged("pw")
        await vm.onRememberChanged(false) // explicitly ensure rememberMe is false

        let exp = expectation(description: "nav_no_persist")
        await MainActor.run {
            vm.navigation
            .sink { _ in exp.fulfill() }
            .store(in: &cancellables)
        }

        await vm.attemptLogin()
        await fulfillment(of: [exp], timeout: 1.0)

        let saved = await store.getToken()
        XCTAssertNil(saved)
    }

    func testFailureIncrementsAndLockoutAfterThreshold() async {
        let svc = FakeAuthServiceSpy()
        svc.result = .failure(AuthError.invalidCredentials)
        let store = InMemoryTokenStore()
        let net = FakeNetworkMonitor(online: true)

        let vm = await MainActor.run {
            LoginViewModel(authService: svc, tokenStore: store, networkMonitor: net, lockoutThreshold: 3, lockoutDurationSeconds: 1)
        }

        await vm.onEmailChanged("a@b.com")
        await vm.onPasswordChanged("pw")

        await vm.attemptLogin()
        await MainActor.run { XCTAssertEqual(vm.state.failureCount, 1) }

        await vm.attemptLogin()
        await MainActor.run { XCTAssertEqual(vm.state.failureCount, 2) }

        await vm.attemptLogin()
        await MainActor.run {
            XCTAssertEqual(vm.state.failureCount, 3)
            XCTAssertTrue(vm.state.isLocked())
        }

        // Optional: verify auth service call count (spy)
        XCTAssertEqual(svc.calls, 3, "Auth service should have been called for each failed attempt")
    }

    func testOfflineDoesNotCallAuth() async {
        let svc = FakeAuthServiceSpy()
        let store = InMemoryTokenStore()
        let net = FakeNetworkMonitor(online: false)

        let vm = await MainActor.run {
            LoginViewModel(authService: svc, tokenStore: store, networkMonitor: net)
        }

        await vm.onEmailChanged("a@b.com")
        await vm.onPasswordChanged("pw")

        await vm.attemptLogin()

        // Assert the spy recorded zero calls
        XCTAssertEqual(svc.calls, 0, "AuthService should not be invoked while offline")

        // Read actor-isolated state on MainActor
        await MainActor.run {
            XCTAssertEqual(vm.state.errorMessage, "Offline. Please connect to network.")
        }
    }
}