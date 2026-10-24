package br.com.fiap.postech.carworkshop.auth.adapter.dto;

// Sem roles: o papel e decidido no servidor. Aceitar a lista do cliente deixava qualquer um criar um ADMIN.
public record SignupRequest(String username, String password) {}
