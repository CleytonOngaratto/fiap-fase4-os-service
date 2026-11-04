package br.com.fiap.postech.carworkshop.bdd.e2e;

import br.com.fiap.postech.carworkshop.bdd.SagaDriver;

import java.lang.reflect.Proxy;

final class EksSagaDriver {

    private EksSagaDriver() {
    }

    static SagaDriver create() {
        return (SagaDriver) Proxy.newProxyInstance(SagaDriver.class.getClassLoader(), new Class<?>[]{SagaDriver.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
