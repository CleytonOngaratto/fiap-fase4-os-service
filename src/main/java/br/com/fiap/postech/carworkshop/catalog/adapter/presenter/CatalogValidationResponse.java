package br.com.fiap.postech.carworkshop.catalog.adapter.presenter;

import br.com.fiap.postech.carworkshop.catalog.adapter.dto.CatalogItemRequest;

import java.util.List;

public record CatalogValidationResponse(List<CatalogItemRequest> invalid) {
}
