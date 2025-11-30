// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "Login",
    platforms: [
        .iOS(.v15),
        .macOS(.v10_15)
    ],
    products: [
        .library(name: "Login", targets: ["Login"])
    ],
    targets: [
        .target(
            name: "Login",
            path: "Sources/Login"
        ),
        .testTarget(
            name: "LoginTests",
            dependencies: ["Login"],
            path: "Tests/LoginTests"
        )
    ]
)