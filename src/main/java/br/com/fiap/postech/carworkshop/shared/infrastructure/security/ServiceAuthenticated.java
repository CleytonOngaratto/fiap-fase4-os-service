package br.com.fiap.postech.carworkshop.shared.infrastructure.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@NameBinding
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceAuthenticated {
}
