<?php

namespace Iamdevroyal\MobileBiometrics;

class BiometricsManager
{
    protected function callNative(string $method, array $params = []): mixed
    {
        if (! function_exists('nativephp_call')) {
            return [
                'success' => false,
                'message' => 'NativePHP bridge helper not available.',
            ];
        }

        $json = json_encode($params);

        if ($json === false) {
            return [
                'success' => false,
                'message' => 'Failed to encode NativePHP bridge parameters.',
            ];
        }

        return nativephp_call($method, $json);
    }

    public function isAvailable(): mixed
    {
        return $this->callNative('Biometrics.IsAvailable');
    }

    public function getTypes(): mixed
    {
        return $this->callNative('Biometrics.GetTypes');
    }

    /**
     * Prompt the user for biometric confirmation.
     *
     * ⚠️ IMPORTANT: a `success: true` response here is a client-side UX
     * signal, not a security boundary. It can be bypassed on a compromised
     * device or via a WebView-layer bug. Any endpoint that moves money or
     * changes account-sensitive state MUST independently re-verify the
     * action server-side (fresh session check, transaction PIN, OTP,
     * rate-limiting/anomaly detection) rather than trusting this alone.
     * See CHANGES.md for the full rationale.
     */
    public function authenticate(
        string $reason = 'Authenticate to continue',
        string $title = 'Biometric Authentication',
        string $subtitle = '',
        string $negativeButton = 'Cancel'
    ): mixed {
        return $this->callNative('Biometrics.Authenticate', [
            'reason' => $reason,
            'title' => $title,
            'subtitle' => $subtitle,
            'negativeButton' => $negativeButton,
        ]);
    }
}
