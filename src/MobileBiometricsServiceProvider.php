<?php

namespace Iamdevroyal\MobileBiometrics;

use Illuminate\Support\ServiceProvider;

class MobileBiometricsServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton('mobile-biometrics', function () {
            return new BiometricsManager();
        });
    }

    public function boot(): void
    {
        //
    }
}
