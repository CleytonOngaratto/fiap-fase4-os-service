package br.com.fiap.postech.carworkshop.auth.usecase.port.in;

import br.com.fiap.postech.carworkshop.auth.adapter.dto.LoginRequest;
import br.com.fiap.postech.carworkshop.auth.adapter.presenter.AuthTokenResponse;
import br.com.fiap.postech.carworkshop.auth.domain.entity.User;

public interface AuthUseCase {
    User signup(RegisterAdminCommand command);
    AuthTokenResponse login(LoginRequest request);
}
