package br.com.fiap.postech.carworkshop.customer.adapter.presenter;

import br.com.fiap.postech.carworkshop.customer.domain.entity.Customer;

// So id e nome: a Lambda precisa confirmar que o CPF e de um cliente, nao do cadastro inteiro.
public record InternalCustomerResponse(Long id, String name) {

    public static InternalCustomerResponse from(Customer customer) {
        return new InternalCustomerResponse(customer.getId(), customer.getName());
    }
}
