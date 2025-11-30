//
// Created by Keabetswe Mputle on 2025/11/28.
//

#if os(iOS)
import SwiftUI

public struct LoginView: View {
    @ObservedObject var viewModel: LoginViewModel

    public init(viewModel: LoginViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(spacing: 16) {
            TextField("Email", text: Binding(
                get: { viewModel.state.email },
                set: { viewModel.onEmailChanged($0) }
            ))
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .accessibilityIdentifier("emailField")

            SecureField("Password", text: Binding(
                get: { viewModel.state.password },
                set: { viewModel.onPasswordChanged($0) }
            ))
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .accessibilityIdentifier("passwordField")

            Toggle(isOn: Binding(
                get: { viewModel.state.rememberMe },
                set: { viewModel.onRememberChanged($0) }
            )) {
                Text("Remember me")
            }
            .accessibilityIdentifier("rememberToggle")

            if let err = viewModel.state.errorMessage {
                Text(err).foregroundColor(.red).accessibilityIdentifier("errorText")
            }

            Button(action: {
                Task { await viewModel.attemptLogin() }
            }) {
                if viewModel.state.isLoading {
                    ProgressView()
                } else {
                    Text("Login")
                }
            }
            .disabled(!viewModel.state.isLoginEnabled())
            .accessibilityIdentifier("loginButton")
        }
        .padding()
    }
}
#endif