package br.com.fiap.postech.carworkshop.shared.domain.exception;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
