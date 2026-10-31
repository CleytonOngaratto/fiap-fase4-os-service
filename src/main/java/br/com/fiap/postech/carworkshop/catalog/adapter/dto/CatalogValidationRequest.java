package br.com.fiap.postech.carworkshop.catalog.adapter.dto;

import java.util.List;

public record CatalogValidationRequest(List<CatalogItemRequest> items) {

    public List<CatalogItemRequest> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }
}
