const baseUrl = '/_native/api/call';

async function bridgeCall(method, params = {}) {
    const response = await fetch(baseUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
        },
        body: JSON.stringify({ method, params }),
    });

    const data = await response.json();

    if (!response.ok) {
        throw new Error(data?.message || `Native bridge error: ${response.status}`);
    }

    return data;
}

export async function isAvailable() {
    return bridgeCall('Biometrics.IsAvailable', {});
}

export async function getTypes() {
    return bridgeCall('Biometrics.GetTypes', {});
}

export async function authenticate(options = {}) {
    return bridgeCall('Biometrics.Authenticate', {
        reason: options.reason ?? 'Authenticate to continue',
        title: options.title ?? 'Biometric Authentication',
        subtitle: options.subtitle ?? '',
        negativeButton: options.negativeButton ?? 'Cancel',
    });
}

const Biometrics = {
    IsAvailable() {
        return isAvailable();
    },
    GetTypes() {
        return getTypes();
    },
    Authenticate(options = {}) {
        return authenticate(options);
    },
};

// This global name is a runtime contract — korpabee-user/src/lib/nativeBridge.js
// calls window.NativePHP.Biometrics directly. Do not rename without updating
// that file too.
if (typeof window !== 'undefined') {
    window.NativePHP = window.NativePHP || {};
    window.NativePHP.Biometrics = Biometrics;
}

export default Biometrics;
