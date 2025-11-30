//
// Created by Keabetswe Mputle on 2025/11/28.
//

import Foundation

public protocol TokenStore {
    func saveToken(_ token: AuthToken) async throws
    func getToken() async -> AuthToken?
}