# iamdevroyal/mobile-biometrics

**Free, MIT-licensed** biometric authentication plugin for [NativePHP Mobile](https://nativephp.com).  
Provides native **Face ID**, **Touch ID**, and **Android Biometric (Fingerprint/Class 3 Strong)** authentication for your Laravel + NativePHP mobile applications across Android and iOS.

Built with **security-hardened defaults** — timeout-bounded native execution (prevents ANRs and watchdog terminations), strict `BIOMETRIC_STRONG` validation on Android, pre-flight availability checks, and automatic manifest permission defaults.

---

## Table of Contents

1. [How It Works](#how-it-works)
2. [Key Features](#key-features)
3. [Security Architecture](#security-architecture)
4. [Requirements](#requirements)
5. [Installation](#installation)
6. [Platform Setup & Configuration](#platform-setup--configuration)
7. [Usage — PHP (Laravel)](#usage--php-laravel)
8. [Usage — JavaScript (Vue / React / Plain SPA)](#usage--javascript-vue--react--plain-spa)
9. [Bridge Methods API Reference](#bridge-methods-api-reference)
10. [Platform Details](#platform-details)
11. [Troubleshooting](#troubleshooting)
12. [License](#license)

---

## How It Works

This plugin connects NativePHP Mobile's web-runtime to native device biometric hardware:

| Layer | Component | Responsibility |
|---|---|---|
| **Laravel / PHP** | `Iamdevroyal\MobileBiometrics\Facades\Biometrics` | High-level PHP facade for Livewire / Blade backends |
| **JavaScript Bridge** | `window.NativePHP.Biometrics` & `resources/js/index.js` | Direct async JavaScript bridge methods for SPAs |
| **Android Native** | `BiometricsPlugin.kt` (AndroidX Biometric) | Hardware interaction with `BIOMETRIC_STRONG` (Class 3) sensors |
| **iOS Native** | `BiometricsPlugin.swift` (LocalAuthentication `LAContext`) | Touch ID and Face ID evaluation |

---

## Key Features

- 🔒 **`BIOMETRIC_STRONG` Enforcement** — Restricted to Class 3 authenticators on Android, rejecting spoofable 2D camera face unlocks.
- ⏱️ **Bounded Timeouts (60s)** — Replaces indefinite blocking with a 60-second latch/semaphore with automated prompt cancellation on timeout to protect against UI hangs and ANR/watchdog crashes.
- 🛡️ **Pre-flight Availability Verification** — Evaluates biometric availability before launching UI prompts for consistent cross-platform behavior.
- 📱 **Zero-Config Face ID Permissions** — Declares `NSFaceIDUsageDescription` in `nativephp.json` by default.
- ⚡ **Dual API Surface** — Full support for both Laravel PHP backend controllers and client-side JavaScript / SPA frameworks.

---

## Security Architecture

> ⚠️ **IMPORTANT SECURITY NOTICE**  
> A `success: true` response from this plugin (or any client-side biometric prompt) represents a **client-side UX confirmation signal, NOT an absolute server-side cryptographic authorization**.

On rooted or jailbroken devices, or in the presence of client-side WebView compromise (e.g. XSS), client return values can potentially be spoofed. 

**Best Practices for Financial & Sensitive Operations:**
- Never use client-side biometric success as the sole authorization for money withdrawals, fund transfers, or critical account changes.
- Combine biometric confirmation with server-side validation: short-lived step-up tokens, transaction PINs, OTP verification, or server-side velocity and anomaly detection.

---

## Requirements

| Requirement | Supported Versions |
|---|---|
| **PHP** | `^8.1` / `^8.2` / `^8.3` |
| **Laravel** | `^11.0`, `^12.0`, or `^13.0` |
| **NativePHP Mobile** | `nativephp/mobile` `^3.0` |
| **Android** | Minimum API Level 33 (`androidx.biometric:biometric:1.2.0-alpha05`) |
| **iOS** | Minimum iOS 18.2 (`LocalAuthentication` framework) |

---

## Installation

### 1. Require the Package

```bash
composer require iamdevroyal/mobile-biometrics
```

Laravel package auto-discovery registers `MobileBiometricsServiceProvider` and the `Biometrics` facade automatically.

### 2. Register the Plugin with NativePHP

```bash
php artisan native:plugin:register iamdevroyal/mobile-biometrics
```

### 3. Validate & Build

```bash
php artisan native:plugin:validate
php artisan native:run android
# or
php artisan native:run ios
```

---

## Platform Setup & Configuration

### iOS Configuration

Face ID requires a usage description in your app's `Info.plist`. While this plugin provides a default in `nativephp.json`, you can customize the string in your host app's `config/nativephp.php`:

```php
// config/nativephp.php
'permissions' => [
    'NSFaceIDUsageDescription' => 'Confirm your identity to securely access your account.',
],
```

### Android Configuration

Android dependencies (`androidx.biometric:biometric`) and permissions are linked automatically by the NativePHP plugin manifest. No manual Gradle modifications are required.

---

## Usage — PHP (Laravel)

Use the `Biometrics` facade in your controllers or Livewire components:

```php
use Iamdevroyal\MobileBiometrics\Facades\Biometrics;

// 1. Check if biometric authentication is available and enrolled
$status = Biometrics::isAvailable();
/*
Returns array:
[
    'success'   => true,
    'available' => true,
    'status'    => 0 // BiometricManager.BIOMETRIC_SUCCESS (Android) or 'available' (iOS)
]
*/

// 2. Query available biometric hardware types
$types = Biometrics::getTypes();
/*
Returns array:
[
    'success'   => true,
    'available' => true,
    'types'     => ['faceid'] // 'faceid', 'touchid', or 'biometric'
]
*/

// 3. Prompt user for biometric authentication
$result = Biometrics::authenticate(
    reason: 'Confirm your identity to authorize this transaction',
    title: 'Biometric Verification',
    subtitle: 'Scan your fingerprint or face',
    negativeButton: 'Use PIN'
);

if ($result['success'] ?? false) {
    // Biometric verified on device.
    // Ensure critical financial operations re-verify against server-side logic.
} else {
    $errorMessage = $result['message'] ?? 'Authentication failed';
}
```

---

## Usage — JavaScript (Vue / React / Plain SPA)

The plugin exposes global helper methods under `window.NativePHP.Biometrics` as well as ES module exports from `iamdevroyal/mobile-biometrics/resources/js/index.js`.

### Using `window.NativePHP.Biometrics`

```javascript
// 1. Check hardware availability
const availability = await window.NativePHP.Biometrics.IsAvailable();
if (availability.available) {
    console.log('Biometrics available!');
}

// 2. Query sensor types
const sensorInfo = await window.NativePHP.Biometrics.GetTypes();
console.log('Available sensor types:', sensorInfo.types); // e.g. ['faceid'] or ['biometric']

// 3. Trigger authentication prompt
try {
    const response = await window.NativePHP.Biometrics.Authenticate({
        title: 'Unlock Wallet',
        subtitle: 'Scan face or fingerprint',
        reason: 'Authorize transfer of $50.00',
        negativeButton: 'Cancel'
    });

    if (response.success && response.authenticated) {
        console.log('Authentication confirmed!');
    } else {
        console.warn('Authentication failed:', response.message);
    }
} catch (error) {
    console.error('Bridge invocation error:', error);
}
```

---

## Bridge Methods API Reference

| Method | Parameters | Return Schema | Description |
|---|---|---|---|
| `Biometrics.IsAvailable` | _None_ | `{ success: bool, available: bool, status: mixed }` | Checks if hardware is present, enabled, and has enrolled credentials. |
| `Biometrics.GetTypes` | _None_ | `{ success: bool, available: bool, types: string[] }` | Returns array of enrolled types: `['faceid']`, `['touchid']`, or `['biometric']`. |
| `Biometrics.Authenticate` | `title?: string`<br>`subtitle?: string`<br>`reason?: string`<br>`negativeButton?: string` | `{ success: bool, authenticated: bool, message: string }` | Opens native biometric prompt. Returns failure on cancel, timeout (60s), or unrecognized biometrics. |

---

## Platform Details

### Android
- Utilizes `androidx.biometric.BiometricPrompt`.
- Restricts authentication to `BiometricManager.Authenticators.BIOMETRIC_STRONG` (Class 3 hardware).
- Does not expose insecure 2D software facial recognition or unverified sensors.

### iOS
- Utilizes Apple's `LocalAuthentication` framework (`LAContext`).
- Evaluates `.deviceOwnerAuthenticationWithBiometrics`.
- Accurately distinguishes between `faceid`, `touchid`, and general biometrics in `GetTypes()`.

---

## Troubleshooting

### iOS crashes when triggering authentication
Ensure `NSFaceIDUsageDescription` is present in your app's `Info.plist` or configured in `config/nativephp.php`. iOS terminates apps that call Face ID without a declared usage string.

### Android prompt returns "not enrolled or not available"
Ensure the test device or emulator has at least one fingerprint or biometric credential configured in system settings.

### `NativePHP bridge helper not available`
Ensure you are running the app inside the NativePHP Mobile shell (`php artisan native:run android` or `php artisan native:run ios`). PHP facade calls outside the NativePHP environment will return this fallback.

---

## License

MIT License.
