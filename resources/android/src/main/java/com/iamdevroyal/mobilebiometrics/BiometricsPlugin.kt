package com.iamdevroyal.mobilebiometrics

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import com.nativephp.mobile.bridge.BridgeError
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Hardened fork of projectmata/mobile-biometrics. See ../../../../../../CHANGES.md
 * (repo root) for the full audit this addresses. Summary of changes from upstream:
 *
 *  - BIOMETRIC_STRONG only (was STRONG or WEAK) — WEAK-class authenticators are not
 *    guaranteed spoof-resistant per Android's own CDD; unacceptable for gating a
 *    financial transaction.
 *  - Authenticate() now pre-checks canAuthenticate() before showing the prompt,
 *    matching the iOS implementation, instead of relying on the OS to fail
 *    gracefully inside the prompt itself.
 *  - The blocking wait for the prompt's callback is now bounded by AUTH_TIMEOUT_MS
 *    via CountDownLatch.await(timeout), not an unbounded Object.wait(). An
 *    unbounded wait on whatever thread the NativePHP bridge dispatches
 *    execute() on risks an ANR (if that's the main thread) or a permanently
 *    stuck call (if a callback edge case never fires) with no way out.
 *
 *  ⚠️ STILL NEEDS VERIFICATION ON A REAL DEVICE/BUILD: which thread NativePHP's
 *  bridge actually calls BridgeFunction.execute() on. If it's the main/UI
 *  thread, blocking it at all — even with a timeout — will make the UI
 *  unresponsive for the duration of the prompt and may still trigger an ANR
 *  warning before the timeout elapses. The structurally correct fix is an
 *  async execute() that doesn't block any thread, which requires knowing
 *  whether NativePHP's BridgeFunction interface supports that. Treat this
 *  file as "meaningfully safer than upstream," not "provably correct" until
 *  that's confirmed against NativePHP core's actual dispatch behavior.
 */
class BiometricsPlugin {

    companion object {
        /** Hard ceiling so a stuck callback can never hang forever. */
        private const val AUTH_TIMEOUT_MS = 60_000L

        private fun makeError(code: String, message: String): BridgeError {
            val ctor = BridgeError::class.java.getDeclaredConstructor(
                String::class.java,
                String::class.java
            )
            ctor.isAccessible = true
            return ctor.newInstance(code, message)
        }

        private fun getExecutor(activity: FragmentActivity): Executor {
            return activity.mainExecutor
        }

        private fun biometricManager(activity: FragmentActivity): BiometricManager {
            return BiometricManager.from(activity)
        }

        /**
         * STRONG only. Do not add BIOMETRIC_WEAK back in for anything gating a
         * money-movement flow — see the audit note above.
         */
        private fun allowedAuthenticators(): Int {
            return BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    }

    class IsAvailable(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val result = biometricManager(activity).canAuthenticate(allowedAuthenticators())

                BridgeResponse.success(
                    mapOf<String, Any>(
                        "success" to true,
                        "available" to (result == BiometricManager.BIOMETRIC_SUCCESS),
                        "status" to result
                    )
                )
            } catch (e: Exception) {
                BridgeResponse.error(
                    makeError("BIOMETRIC_CHECK_ERROR", e.message ?: "Failed to check biometric availability.")
                )
            }
        }
    }

    class GetTypes(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val manager = biometricManager(activity)
                val canAuth = manager.canAuthenticate(allowedAuthenticators())

                val types = mutableListOf<String>()

                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    types.add("biometric")
                }

                BridgeResponse.success(
                    mapOf<String, Any>(
                        "success" to true,
                        "available" to (canAuth == BiometricManager.BIOMETRIC_SUCCESS),
                        "types" to types
                    )
                )
            } catch (e: Exception) {
                BridgeResponse.error(
                    makeError("BIOMETRIC_TYPES_ERROR", e.message ?: "Failed to get biometric types.")
                )
            }
        }
    }

    class Authenticate(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                // Pre-flight check — fail fast with a clear reason instead of
                // relying on the prompt UI to handle "not available" gracefully
                // across every OEM's BiometricPrompt implementation.
                val availability = biometricManager(activity).canAuthenticate(allowedAuthenticators())
                if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                    return BridgeResponse.success(
                        mapOf<String, Any>(
                            "success" to false,
                            "authenticated" to false,
                            "message" to "Biometric authentication is not available or not enrolled on this device.",
                            "status" to availability
                        )
                    )
                }

                val title = parameters["title"]?.toString()?.ifBlank { "Biometric Authentication" }
                    ?: "Biometric Authentication"
                val subtitle = parameters["subtitle"]?.toString() ?: ""
                val reason = parameters["reason"]?.toString()?.ifBlank { "Authenticate to continue" }
                    ?: "Authenticate to continue"
                val negativeButton = parameters["negativeButton"]?.toString()?.ifBlank { "Cancel" }
                    ?: "Cancel"

                val resultHolder = mutableMapOf<String, Any>(
                    "success" to false,
                    "authenticated" to false,
                    "message" to "Authentication did not complete."
                )

                val latch = CountDownLatch(1)

                val prompt = BiometricPrompt(
                    activity,
                    getExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            resultHolder["success"] = true
                            resultHolder["authenticated"] = true
                            resultHolder["message"] = "Authentication successful."
                            latch.countDown()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            resultHolder["success"] = false
                            resultHolder["authenticated"] = false
                            resultHolder["code"] = errorCode
                            resultHolder["message"] = errString.toString()
                            latch.countDown()
                        }

                        override fun onAuthenticationFailed() {
                            // A single failed attempt (e.g. wrong finger) — the
                            // system prompt stays open for retry, so we do NOT
                            // count down the latch here. Only Succeeded/Error
                            // end the prompt lifecycle.
                            resultHolder["success"] = false
                            resultHolder["authenticated"] = false
                            resultHolder["message"] = "Biometric not recognized."
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(reason)
                    .setNegativeButtonText(negativeButton)
                    .build()

                activity.runOnUiThread {
                    prompt.authenticate(promptInfo)
                }

                val completedInTime = latch.await(AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!completedInTime) {
                    activity.runOnUiThread { prompt.cancelAuthentication() }
                    return BridgeResponse.success(
                        mapOf<String, Any>(
                            "success" to false,
                            "authenticated" to false,
                            "message" to "Authentication timed out."
                        )
                    )
                }

                BridgeResponse.success(resultHolder)
            } catch (e: Exception) {
                BridgeResponse.error(
                    makeError("BIOMETRIC_AUTH_ERROR", e.message ?: "Authentication failed.")
                )
            }
        }
    }
}
