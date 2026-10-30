package br.com.fiap.postech.carworkshop.catalog.adapter.controller;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.autoservice.usecase.port.out.AutoServiceRepositoryPort;
import br.com.fiap.postech.carworkshop.catalog.adapter.dto.CatalogItemRequest;
import br.com.fiap.postech.carworkshop.catalog.adapter.dto.CatalogValidationRequest;
import br.com.fiap.postech.carworkshop.catalog.adapter.presenter.CatalogValidationResponse;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.out.InventoryRepositoryPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import br.com.fiap.postech.carworkshop.shared.infrastructure.security.ServiceAuthenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Recurso proprio sob /internal, fora da allowlist do Gateway: o catalogo publico exige JWT de ADMIN, e este
// caminho responde a servico, com o segredo compartilhado. Em lote porque quem chama valida o diagnostico
// inteiro de uma vez e precisa de uma unica chamada para o circuit breaker contar.
@Path("/internal/catalog")
@Tag(name = "Internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalCatalogController {

    private static final int MAX_ITEMS = 100;

    @Inject
    AutoServiceRepositoryPort services;

    @Inject
    InventoryRepositoryPort parts;

    @POST
    @PermitAll
    @ServiceAuthenticated
    @Path("/validate")
    @Transactional
    @Operation(summary = "List the diagnosed items the catalog does not know (service to service)")
    public CatalogValidationResponse validate(CatalogValidationRequest request) {
        List<CatalogItemRequest> items = required(request);
        Set<Long> knownServices = services.findByIds(idsOf(items, false)).stream()
                .map(AutoService::getId)
                .collect(Collectors.toSet());
        Set<Long> knownParts = parts.findByIds(idsOf(items, true)).stream()
                .map(PartsAndSupply::getId)
                .collect(Collectors.toSet());

        List<CatalogItemRequest> invalid = new ArrayList<>();
        for (CatalogItemRequest item : items) {
            Set<Long> known = item.isPart() ? knownParts : knownServices;
            if (item.catalogId() == null || !known.contains(item.catalogId())) {
                invalid.add(item);
            }
        }
        return new CatalogValidationResponse(invalid);
    }

    private static List<CatalogItemRequest> required(CatalogValidationRequest request) {
        if (request == null || request.itemsOrEmpty().isEmpty()) {
            throw new ValidationException("At least one item is required.");
        }
        if (request.itemsOrEmpty().size() > MAX_ITEMS) {
            throw new ValidationException("At most " + MAX_ITEMS + " items can be validated at once.");
        }
        return request.itemsOrEmpty();
    }

    private static List<Long> idsOf(List<CatalogItemRequest> items, boolean part) {
        return items.stream()
                .filter(item -> item.isPart() == part)
                .map(CatalogItemRequest::catalogId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
