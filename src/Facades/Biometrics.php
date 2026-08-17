<?php

namespace Iamdevroyal\MobileBiometrics\Facades;

use Illuminate\Support\Facades\Facade;

class Biometrics extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return 'mobile-biometrics';
    }
}
