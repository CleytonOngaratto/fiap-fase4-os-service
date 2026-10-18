package br.com.fiap.postech.carworkshop.workorder.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SagaReplyHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkOrderUseCase useCase = mock(WorkOrderUseCase.class);
    private SagaReplyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SagaReplyHandler();
        handler.workOrderUseCase = useCase;
    }

    private void apply(String type) throws Exception {
        String body = """
                { "type": "%s", "workOrderId": 42, "sagaId": "6f1d0000-0000-4000-8000-000000000002" }
                """.formatted(type);
        handler.apply(objectMapper.readTree(body));
    }

    @Test
    void diagnosisStarted_startsTheDiagnosisOfTheWorkOrder() throws Exception {
        apply("DiagnosisStarted");

        verify(useCase).startDiagnosis(42L);
    }

    @Test
    void unknownType_touchesNothing() throws Exception {
        apply("SomethingElse");

        verifyNoInteractions(useCase);
    }
}
