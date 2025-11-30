//
// Created by Keabetswe Mputle on 2025/11/28.
//

import Foundation

public struct AuthToken {
    public let token: String
    public let expiresAt: Date
}

public enum AuthError: Error {
    case invalidCredentials
    case serverError(String)
}

public protocol AuthService {
    func login(email: String, password: String) async throws -> AuthToken
}