//
// Created by Keabetswe Mputle on 2025/11/28.
//

import Foundation

public struct LoginState {
    public var email: String = ""
    public var password: String = ""
    public var rememberMe: Bool = false
    public var isLoading: Bool = false
    public var errorMessage: String? = nil
    public var failureCount: Int = 0
    public var lockedUntil: Date? = nil

    public init() {}
    public func isLocked(now: Date = Date()) -> Bool {
        if let until = lockedUntil { return now < until }
        return false
    }
    public func isLoginEnabled(now: Date = Date()) -> Bool {
        return !isLoading && !isLocked(now: now) && !email.isEmpty && !password.isEmpty
    }
}