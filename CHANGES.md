# Security Audit & Hardening Log

This document records the architectural analysis, hardening decisions, and engineering considerations implemented in `iamdevroyal/mobile-biometrics`.

---

## ⚠️ Critical Architectural Notice — UX Signal vs Security Boundary

A `success: true` response from this plugin is a **client-side UX verification signal, not an absolute server-side security boundary**.

- On compromised devices (jailbroken iOS or rooted Android), native return values can be hooked or bypassed.
- Any XSS vulnerability in a hybrid WebView environment could trigger or intercept bridge calls.
- **Rule of thumb:** Any backend endpoint executing critical financial transactions or sensitive state changes (e.g., wallet withdrawals, password reset, recipient changes) **must independently re-verify the action server-side** (e.g., fresh session tokens, transaction PIN, secondary OTP, anomaly/velocity checks).

---

## Hardening Improvements

### 1. Bounded Timeouts & Cancellation (Prevents ANRs / Watchdog Kills)

- **Issue:** Synchronously blocking indefinitely on user biometric prompts can cause severe UI freezes, thread exhaustion, Android ANR (App Not Responding) dialogs, or iOS watchdog kills if execute runs on the main thread or an OEM HAL hangs.
- **Fix:** Both platforms now enforce a bounded wait:
  - **Android:** `CountDownLatch.await(60_000, TimeUnit.MILLISECONDS)`. If the timeout fires, `prompt.cancelAuthentication()` is explicitly called and `{success: false, message: "Authentication timed out."}` is returned cleanly.
  - **iOS:** `DispatchSemaphore.wait(timeout: .now() + 60)`. If the timeout fires, `context.invalidate()` is called to terminate the in-flight evaluation and `{success: false, message: "Authentication timed out."}` is returned.

### 2. Android: Strict `BIOMETRIC_STRONG` Enforcement (Class 3 Only)

- **Issue:** Accepting `BIOMETRIC_WEAK` (Class 2) allows 2D software camera face-unlock systems that Android CDD does not guarantee against spoofing attacks (e.g. static photo bypass).
- **Fix:** `allowedAuthenticators()` enforces `BiometricManager.Authenticators.BIOMETRIC_STRONG` exclusively.

### 3. Android: Pre-flight Hardware & Enrollment Checks

- **Issue:** Launching `BiometricPrompt.authenticate()` directly without checking enrollment causes inconsistent OEM crash / error behaviors.
- **Fix:** Pre-checks `canAuthenticate(BIOMETRIC_STRONG)` and returns an immediate informative `{success: false, message: "Biometric authentication is not available or not enrolled on this device."}` matching iOS `canEvaluatePolicy` behavior.

### 4. iOS: Automatic `NSFaceIDUsageDescription` Manifest Default

- **Issue:** iOS immediately crashes on first Face ID evaluation if `NSFaceIDUsageDescription` is missing in Info.plist.
- **Fix:** `nativephp.json` declares a default `NSFaceIDUsageDescription` under `ios.info_plist`.

---

## Verification Checklist

- [ ] Verify `NSFaceIDUsageDescription` in generated iOS build project or host app `config/nativephp.php`.
- [ ] Real device test: verify clean timeout handling when prompt is ignored for 60 seconds.
- [ ] Real device test: verify fingerprint, Face ID, and Touch ID hardware recognition on both platforms.
