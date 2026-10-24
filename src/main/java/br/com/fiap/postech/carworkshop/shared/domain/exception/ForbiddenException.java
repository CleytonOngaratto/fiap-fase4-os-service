package br.com.fiap.postech.carworkshop.shared.domain.exception;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super(message);
    }
}
