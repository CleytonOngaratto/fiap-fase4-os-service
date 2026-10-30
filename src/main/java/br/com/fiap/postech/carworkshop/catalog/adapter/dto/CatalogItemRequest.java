package br.com.fiap.postech.carworkshop.catalog.adapter.dto;

public record CatalogItemRequest(String type, Long catalogId) {

    public static final String PART = "PART";

    public boolean isPart() {
        return PART.equals(type);
    }
}
