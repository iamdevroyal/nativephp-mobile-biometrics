import Foundation
import LocalAuthentication

// Hardened fork of projectmata/mobile-biometrics. See CHANGES.md (repo root)
// for the full audit. Summary of changes from upstream:
//
//  - semaphore.wait() is now bounded by AUTH_TIMEOUT_SECONDS instead of
//    waiting forever. LAContext's evaluatePolicy completion handler is not
//    guaranteed to run on any particular thread/queue; if execute() is ever
//    called on the main thread, an unbounded wait risks iOS's watchdog
//    killing the app for unresponsiveness during what's meant to be a
//    routine (if slow) user interaction.
//  - context.invalidate() is called on timeout to actively cancel the
//    in-flight evaluation rather than leaving it dangling.
//
//  ⚠️ STILL NEEDS VERIFICATION ON A REAL DEVICE/BUILD: confirm which thread/
//  queue NativePHP's bridge actually calls execute() on. If it's the main
//  thread, blocking it at all — even with a timeout — makes the UI
//  unresponsive for the duration of the prompt. See the equivalent note in
//  the Android BiometricsPlugin.kt for the full explanation; the same
//  caveat applies here.

@objc(BiometricsPlugin)
class BiometricsPlugin: NSObject {

    private static let authTimeoutSeconds: TimeInterval = 60

    @objc(BiometricsPluginIsAvailable)
    class IsAvailable: NSObject {
        @objc
        func execute(_ parameters: [String: Any]) -> [String: Any] {
            let context = LAContext()
            var error: NSError?

            let available = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)

            return [
                "success": true,
                "available": available,
                "status": available ? "available" : "unavailable"
            ]
        }
    }

    @objc(BiometricsPluginGetTypes)
    class GetTypes: NSObject {
        @objc
        func execute(_ parameters: [String: Any]) -> [String: Any] {
            let context = LAContext()
            var error: NSError?
            let available = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)

            var types: [String] = []

            if available {
                switch context.biometryType {
                case .faceID:
                    types.append("faceid")
                case .touchID:
                    types.append("touchid")
                default:
                    types.append("biometric")
                }
            }

            return [
                "success": true,
                "available": available,
                "types": types
            ]
        }
    }

    @objc(BiometricsPluginAuthenticate)
    class Authenticate: NSObject {
        @objc
        func execute(_ parameters: [String: Any]) -> [String: Any] {
            let context = LAContext()
            var error: NSError?

            let reason = (parameters["reason"] as? String) ?? "Authenticate to continue"

            guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
                return [
                    "success": false,
                    "authenticated": false,
                    "message": error?.localizedDescription ?? "Biometric authentication is not available."
                ]
            }

            let semaphore = DispatchSemaphore(value: 0)
            var response: [String: Any] = [
                "success": false,
                "authenticated": false,
                "message": "Authentication failed."
            ]

            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authError in
                response = [
                    "success": success,
                    "authenticated": success,
                    "message": success ? "Authentication successful." : (authError?.localizedDescription ?? "Authentication failed.")
                ]
                semaphore.signal()
            }

            let timeoutResult = semaphore.wait(timeout: .now() + BiometricsPlugin.authTimeoutSeconds)
            if timeoutResult == .timedOut {
                context.invalidate()
                return [
                    "success": false,
                    "authenticated": false,
                    "message": "Authentication timed out."
                ]
            }

            return response
        }
    }
}
