package br.com.fiap.postech.carworkshop.auth.usecase.interactor;

import br.com.fiap.postech.carworkshop.auth.adapter.dto.LoginRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.presenter.AuthTokenResponse;
import br.com.fiap.postech.carworkshop.auth.domain.entity.User;
import br.com.fiap.postech.carworkshop.auth.domain.entity.UserRoles;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.AuthUseCase;
import br.com.fiap.postech.carworkshop.auth.usecase.port.in.RegisterAdminCommand;
import br.com.fiap.postech.carworkshop.auth.usecase.port.out.SecurityPort;
import br.com.fiap.postech.carworkshop.auth.usecase.port.out.TokenProviderPort;
import br.com.fiap.postech.carworkshop.auth.usecase.port.out.UserRepositoryPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AuthInteractor implements AuthUseCase {

    private final UserRepositoryPort userRepository;
    private final TokenProviderPort tokenProvider;
    private final SecurityPort security;

    public AuthInteractor(UserRepositoryPort userRepository,
                          TokenProviderPort tokenProvider,
                          SecurityPort security) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.security = security;
    }

    @Override
    public User signup(RegisterAdminCommand command) {
        if (command.username() == null || command.username().isBlank()) {
            throw new ValidationException("Username must not be blank.");
        }
        if (command.password() == null || command.password().isBlank()) {
            throw new ValidationException("Password must not be blank.");
        }

        // Excecao de primeiro acesso: com a tabela vazia nao existe ADMIN para autorizar a criacao do
        // primeiro. A partir do segundo usuario, so um ADMIN cria outro.
        boolean bootstrap = !userRepository.hasAnyUser();
        if (!bootstrap && !command.requesterIsAdmin()) {
            throw new ForbiddenException("Only an administrator can create a user.");
        }
        if (bootstrap) {
            log.warn("Creating the first administrator ({}): the user table was empty.", command.username());
        }

        userRepository.findByUsername(command.username())
                .ifPresent(u -> { throw new ValidationException("Username already exists."); });

        String salt = security.generateSalt();
        String hashedPassword = security.hashPassword(command.username(), command.password(), salt);

        return userRepository.save(User.builder()
                .username(command.username())
                .password(hashedPassword)
                .salt(salt)
                .roles(List.of(UserRoles.ADMIN))
                .build());
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new EntityNotFoundException("User not found."));

        String hashedPassword = security.hashPassword(request.username(), request.password(), user.getSalt());
        if (!hashedPassword.equals(user.getPassword())) {
            throw new ValidationException("Invalid username or password.");
        }

        String token = tokenProvider.generateToken(request.username(), user.getRoles());
        return new AuthTokenResponse(token);
    }
}
