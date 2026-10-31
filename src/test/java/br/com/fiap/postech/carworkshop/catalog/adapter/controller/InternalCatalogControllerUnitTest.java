package br.com.fiap.postech.carworkshop.catalog.adapter.controller;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.autoservice.usecase.port.out.AutoServiceRepositoryPort;
import br.com.fiap.postech.carworkshop.catalog.adapter.dto.CatalogItemRequest;
import br.com.fiap.postech.carworkshop.catalog.adapter.dto.CatalogValidationRequest;
import br.com.fiap.postech.carworkshop.catalog.adapter.presenter.CatalogValidationResponse;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.inventory.usecase.port.out.InventoryRepositoryPort;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalCatalogControllerUnitTest {

    private AutoServiceRepositoryPort services;
    private InventoryRepositoryPort parts;
    private InternalCatalogController controller;

    @BeforeEach
    void setUp() {
        services = mock(AutoServiceRepositoryPort.class);
        parts = mock(InventoryRepositoryPort.class);
        when(services.findByIds(anyList())).thenReturn(List.of());
        when(parts.findByIds(anyList())).thenReturn(List.of());
        controller = new InternalCatalogController();
        controller.services = services;
        controller.parts = parts;
    }

    @Test
    void answersNothingInvalidWhenTheCatalogKnowsEveryItem() {
        when(services.findByIds(List.of(7L))).thenReturn(List.of(service(7L)));
        when(parts.findByIds(List.of(12L))).thenReturn(List.of(part(12L)));

        CatalogValidationResponse response = controller.validate(request(
                new CatalogItemRequest("SERVICE", 7L), new CatalogItemRequest("PART", 12L)));

        assertTrue(response.invalid().isEmpty());
    }

    @Test
    void namesEveryItemTheCatalogDoesNotKnow() {
        when(parts.findByIds(List.of(12L, 99L))).thenReturn(List.of(part(12L)));

        CatalogValidationResponse response = controller.validate(request(
                new CatalogItemRequest("PART", 12L), new CatalogItemRequest("PART", 99L)));

        assertEquals(1, response.invalid().size());
        assertEquals(99L, response.invalid().get(0).catalogId());
    }

    @Test
    void refusesAnItemWithoutAnIdentifier() {
        CatalogValidationResponse response = controller.validate(
                request(new CatalogItemRequest("PART", null)));

        assertEquals(1, response.invalid().size());
    }

    // Peca e servico sao catalogos diferentes: cada id e consultado na tabela do seu tipo, e nunca na outra.
    @Test
    void looksEachTypeUpInItsOwnCatalog() {
        controller.validate(request(new CatalogItemRequest("SERVICE", 7L),
                new CatalogItemRequest("PART", 12L)));

        verify(services).findByIds(List.of(7L));
        verify(parts).findByIds(List.of(12L));
    }

    @Test
    void sendsEachIdentifierOnlyOnce() {
        controller.validate(request(new CatalogItemRequest("PART", 12L),
                new CatalogItemRequest("PART", 12L)));

        verify(parts).findByIds(List.of(12L));
    }

    @Test
    void refusesAnEmptyOrAbsentBody() {
        assertThrows(ValidationException.class, () -> controller.validate(null));
        assertThrows(ValidationException.class, () -> controller.validate(new CatalogValidationRequest(null)));
        assertThrows(ValidationException.class, () -> controller.validate(request()));
    }

    @Test
    void refusesMoreItemsThanItValidatesAtOnce() {
        List<CatalogItemRequest> items = IntStream.rangeClosed(1, 101)
                .mapToObj(id -> new CatalogItemRequest("PART", (long) id))
                .toList();

        assertThrows(ValidationException.class,
                () -> controller.validate(new CatalogValidationRequest(items)));
    }

    private static CatalogValidationRequest request(CatalogItemRequest... items) {
        return new CatalogValidationRequest(List.of(items));
    }

    private static AutoService service(Long id) {
        return AutoService.builder().id(id).description("Troca de oleo")
                .price(new BigDecimal("180.00")).build();
    }

    private static PartsAndSupply part(Long id) {
        return PartsAndSupply.builder().id(id).description("Filtro").price(new BigDecimal("45.50")).build();
    }
}
