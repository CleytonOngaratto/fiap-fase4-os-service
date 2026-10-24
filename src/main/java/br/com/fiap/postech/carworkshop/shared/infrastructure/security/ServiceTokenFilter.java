package br.com.fiap.postech.carworkshop.shared.infrastructure.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Slf4j
@Provider
@ServiceAuthenticated
@Priority(Priorities.AUTHENTICATION)
public class ServiceTokenFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Service-Token";

    @ConfigProperty(name = "os.internal.service-token")
    Optional<String> expected;

    @Override
    public void filter(ContainerRequestContext request) {
        // Fail closed: sem segredo configurado o endpoint interno nao responde a ninguem, em vez de ficar
        // aberto porque uma variavel de ambiente faltou.
        if (expected.isEmpty() || expected.get().isBlank()) {
            log.error("The internal service token is not configured: refusing {}.", request.getUriInfo().getPath());
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }
        String presented = request.getHeaderString(HEADER);
        if (presented == null || !matches(presented, expected.get())) {
            log.warn("Refused a call to {} without a valid service token.", request.getUriInfo().getPath());
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    // Comparacao em tempo constante: igualdade de String vaza o tamanho do prefixo correto pelo tempo.
    private static boolean matches(String presented, String expectedToken) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}
