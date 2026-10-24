package br.com.fiap.postech.carworkshop.auth.usecase.port.in;

public record RegisterAdminCommand(String username, String password, boolean requesterIsAdmin) {
}
