//
// FakeAuthServiceSpy.swift
// Tests/LoginTests/Support
//

import Foundation
@testable import Login

final class FakeAuthServiceSpy: AuthService {
    enum Outcome {
        case success(AuthToken)
        case failure(Error)
    }

    // Configure this in test before calling attemptLogin()
    var result: Outcome? = nil

    private(set) var calls: Int = 0

    func login(email: String, password: String) async throws -> AuthToken {
        calls += 1
        guard let r = result else {
            throw AuthError.serverError("No result configured")
        }
        switch r {
        case .success(let token):
            return token
        case .failure(let err):
            throw err
        }
    }
}