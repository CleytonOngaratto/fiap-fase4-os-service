package br.com.fiap.postech.carworkshop.workorder.usecase.interactor;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.InvalidOperationException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.DiagnosedItem;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.OpenWorkOrderCommand;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderTransition;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.AutoServiceDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.BranchDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.CorrelationIdPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.CustomerDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.InventoryDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.VehicleDataPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderHistoryPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderMetricsPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderNotificationPort;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.out.WorkOrderRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderInteractorTest {

    private static final String BRANCH = "SP-01";
    private static final String OWNER_CPF = "12345678901";
    private static final String INTRUDER_CPF = "98765432100";
    private static final String TRACE = "EVIDENCIA-B4-001";
    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");
    private static final OpenWorkOrderCommand COMMAND =
            new OpenWorkOrderCommand(1L, 1L, BRANCH, "Barulho no motor");

    private static final AutoService OIL_CHANGE = AutoService.builder()
            .id(1L).description("Troca de oleo").price(new BigDecimal("150.00")).build();
    private static final PartsAndSupply OIL_FILTER = PartsAndSupply.builder()
            .id(3L).code("P003").description("Filtro de oleo").price(new BigDecimal("35.00"))
            .quantity(20).build();

    @Mock
    private WorkOrderRepositoryPort workOrderRepository;
    @Mock
    private CustomerDataPort customerDataPort;
    @Mock
    private VehicleDataPort vehicleDataPort;
    @Mock
    private BranchDataPort branchDataPort;
    @Mock
    private AutoServiceDataPort autoServiceDataPort;
    @Mock
    private InventoryDataPort inventoryDataPort;
    @Mock
    private WorkOrderHistoryPort historyPort;
    @Mock
    private WorkOrderNotificationPort notificationPort;
    @Mock
    private WorkOrderMetricsPort metricsPort;
    @Mock
    private SagaOrchestratorUseCase saga;
    @Mock
    private CorrelationIdPort correlationIdPort;

    private WorkOrderInteractor interactor;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        interactor = new WorkOrderInteractor(workOrderRepository, customerDataPort, vehicleDataPort,
                branchDataPort, autoServiceDataPort, inventoryDataPort, historyPort,
                notificationPort, metricsPort, saga, correlationIdPort);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void givenCustomerVehicleAndBranchExist() {
        when(customerDataPort.findById(1L))
                .thenReturn(Optional.of(new CustomerDataPort.CustomerInfo(1L, "joao@test.com", OWNER_CPF)));
        when(vehicleDataPort.findById(1L))
                .thenReturn(Optional.of(new VehicleDataPort.VehicleInfo(1L, "ABC-1234")));
        when(branchDataPort.exists(BRANCH)).thenReturn(true);
    }

    private static WorkOrder workOrderAt(StatusWO status, Long id, LocalDateTime creationDate) {
        WorkOrder workOrder = WorkOrder.create(1L, "joao@test.com", OWNER_CPF, 1L, "ABC-1234",
                BRANCH, "Barulho no motor");
        workOrder.setId(id);
        workOrder.setStatus(status);
        workOrder.setCreationDate(creationDate);
        return workOrder;
    }

    private static WorkOrder savedWorkOrder() {
        return workOrderAt(StatusWO.RECEIVED, 42L, LocalDateTime.now());
    }

    private WorkOrder given(StatusWO status) {
        WorkOrder workOrder = workOrderAt(status, 42L, LocalDateTime.now());
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(workOrder));
        when(workOrderRepository.save(workOrder)).thenReturn(workOrder);
        return workOrder;
    }

    @Test
    void create_opensTheWorkOrderAndHandsItToTheOrchestrator() {
        givenCustomerVehicleAndBranchExist();
        when(correlationIdPort.current()).thenReturn(TRACE);
        when(workOrderRepository.save(any(WorkOrder.class))).thenReturn(savedWorkOrder());

        WorkOrder created = interactor.create(COMMAND);

        assertEquals(42L, created.getId());
        assertEquals(StatusWO.RECEIVED, created.getStatus());
        verify(saga).start(42L, BRANCH, "ABC-1234", "Barulho no motor", TRACE);
    }

    @Test
    void create_snapshotsTheCustomerDocumentForTheOwnershipCheck() {
        givenCustomerVehicleAndBranchExist();
        when(workOrderRepository.save(any(WorkOrder.class))).thenReturn(savedWorkOrder());

        interactor.create(COMMAND);

        ArgumentCaptor<WorkOrder> workOrder = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderRepository).save(workOrder.capture());
        assertEquals(OWNER_CPF, workOrder.getValue().getCustomerDocument());
        assertTrue(workOrder.getValue().getServices().isEmpty());
        assertTrue(workOrder.getValue().getParts().isEmpty());
    }

    @Test
    void create_writesTheFirstLineOfTheStatusHistory() {
        givenCustomerVehicleAndBranchExist();
        when(workOrderRepository.save(any(WorkOrder.class))).thenReturn(savedWorkOrder());

        interactor.create(COMMAND);

        ArgumentCaptor<WorkOrderStatusChange> change = ArgumentCaptor.forClass(WorkOrderStatusChange.class);
        verify(historyPort).record(change.capture());
        assertEquals(StatusWO.RECEIVED, change.getValue().status());
        assertEquals(BRANCH, change.getValue().branchId());
        verify(metricsPort).recordStatusChange(StatusWO.RECEIVED, BRANCH);
    }

    @Test
    void create_requiresCustomerVehicleBranchAndComplaint() {
        assertEquals("customer_id is required.", assertThrows(ValidationException.class,
                () -> interactor.create(new OpenWorkOrderCommand(null, 1L, BRANCH, "ruido"))).getMessage());

        assertEquals("vehicle_id is required.", assertThrows(ValidationException.class,
                () -> interactor.create(new OpenWorkOrderCommand(1L, null, BRANCH, "ruido"))).getMessage());

        assertEquals("branch_id is required.", assertThrows(ValidationException.class,
                () -> interactor.create(new OpenWorkOrderCommand(1L, 1L, "  ", "ruido"))).getMessage());

        assertEquals("complaint is required.", assertThrows(ValidationException.class,
                () -> interactor.create(new OpenWorkOrderCommand(1L, 1L, BRANCH, null))).getMessage());

        verify(saga, never()).start(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void create_withAnUnknownCustomer_isNotFound() {
        when(customerDataPort.findById(1L)).thenReturn(Optional.empty());

        assertEquals("Customer not found.",
                assertThrows(EntityNotFoundException.class, () -> interactor.create(COMMAND)).getMessage());
    }

    @Test
    void create_withAnUnknownVehicle_isNotFound() {
        when(customerDataPort.findById(1L))
                .thenReturn(Optional.of(new CustomerDataPort.CustomerInfo(1L, "joao@test.com", OWNER_CPF)));
        when(vehicleDataPort.findById(1L)).thenReturn(Optional.empty());

        assertEquals("Vehicle not found.",
                assertThrows(EntityNotFoundException.class, () -> interactor.create(COMMAND)).getMessage());
    }

    @Test
    void create_withAnUnknownBranch_isNotFoundAndNothingIsWritten() {
        when(customerDataPort.findById(1L))
                .thenReturn(Optional.of(new CustomerDataPort.CustomerInfo(1L, "joao@test.com", OWNER_CPF)));
        when(vehicleDataPort.findById(1L))
                .thenReturn(Optional.of(new VehicleDataPort.VehicleInfo(1L, "ABC-1234")));
        when(branchDataPort.exists(BRANCH)).thenReturn(false);

        assertEquals("Branch not found.",
                assertThrows(EntityNotFoundException.class, () -> interactor.create(COMMAND)).getMessage());
        verify(workOrderRepository, never()).save(any());
        verify(saga, never()).start(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void applySagaTransition_movesTheWorkOrderAndRecordsTheHistoryWithTheSagaId() {
        WorkOrder workOrder = given(StatusWO.RECEIVED);

        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.START_DIAGNOSIS, SAGA_ID, null));

        assertEquals(StatusWO.UNDER_DIAGNOSIS, workOrder.getStatus());
        ArgumentCaptor<WorkOrderStatusChange> change = ArgumentCaptor.forClass(WorkOrderStatusChange.class);
        verify(historyPort).record(change.capture());
        assertEquals(SAGA_ID, change.getValue().sagaId());
        assertEquals(StatusWO.UNDER_DIAGNOSIS, change.getValue().status());
    }

    @Test
    void applySagaTransition_walksTheWholeHappyPath() {
        WorkOrder diagnosing = given(StatusWO.RECEIVED);
        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.START_DIAGNOSIS, SAGA_ID, null));
        assertEquals(StatusWO.UNDER_DIAGNOSIS, diagnosing.getStatus());

        WorkOrder pending = given(StatusWO.UNDER_DIAGNOSIS);
        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.PEND_APPROVAL, SAGA_ID, null));
        assertEquals(StatusWO.PENDING_APPROVAL, pending.getStatus());

        WorkOrder awaiting = given(StatusWO.PENDING_APPROVAL);
        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.AWAIT_PAYMENT, SAGA_ID, null));
        assertEquals(StatusWO.AWAITING_PAYMENT, awaiting.getStatus());

        WorkOrder repairing = given(StatusWO.AWAITING_PAYMENT);
        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.START_REPAIR, SAGA_ID, null));
        assertEquals(StatusWO.IN_PROGRESS, repairing.getStatus());
    }

    @Test
    void applySagaTransition_onAnAlreadyAdvancedWorkOrder_isANoOp() {
        WorkOrder workOrder = workOrderAt(StatusWO.IN_PROGRESS, 42L, LocalDateTime.now());
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(workOrder));

        assertFalse(interactor.applySagaTransition(42L, WorkOrderTransition.START_DIAGNOSIS, SAGA_ID, null));

        verify(workOrderRepository, never()).save(any());
        verify(historyPort, never()).record(any());
    }

    @Test
    void applySagaTransition_toComplete_notifiesTheCustomerAndTimesTheService() {
        WorkOrder workOrder = given(StatusWO.IN_PROGRESS);

        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.COMPLETE, SAGA_ID, null));

        assertEquals(StatusWO.COMPLETED, workOrder.getStatus());
        verify(metricsPort).recordCompletion(eq(BRANCH), any(Duration.class));
        verify(notificationPort).notifyCompleted(any(WorkOrderNotificationPort.CompletedNotification.class));
    }

    @Test
    void applySagaTransition_toCancel_recordsTheReasonInTheHistory() {
        WorkOrder workOrder = given(StatusWO.AWAITING_PAYMENT);

        assertTrue(interactor.applySagaTransition(42L, WorkOrderTransition.CANCEL, SAGA_ID,
                CancelReason.PAYMENT_EXPIRED));

        assertEquals(StatusWO.CANCELED, workOrder.getStatus());
        assertEquals(CancelReason.PAYMENT_EXPIRED, workOrder.getCancelReason());
        ArgumentCaptor<WorkOrderStatusChange> change = ArgumentCaptor.forClass(WorkOrderStatusChange.class);
        verify(historyPort).record(change.capture());
        assertEquals(CancelReason.PAYMENT_EXPIRED, change.getValue().reason());
    }

    @Test
    void recordDiagnosis_pricesTheItemsByTheCatalogAndFreezesTheTotal() {
        WorkOrder workOrder = given(StatusWO.UNDER_DIAGNOSIS);
        when(autoServiceDataPort.findByIds(List.of(1L))).thenReturn(List.of(OIL_CHANGE));
        when(inventoryDataPort.findByIds(List.of(3L))).thenReturn(List.of(OIL_FILTER));

        assertTrue(interactor.recordDiagnosis(42L, "Vazamento", List.of(
                new DiagnosedItem(false, 1L, 1),
                new DiagnosedItem(true, 3L, 2)), SAGA_ID));

        assertEquals("Vazamento", workOrder.getDiagnosticDescription());
        assertEquals(new BigDecimal("220.00"), workOrder.getBudgetValue());
        assertEquals(2, workOrder.getParts().get(0).getQuantity());
    }

    @Test
    void recordDiagnosis_sumsARepeatedCatalogIdIntoASingleItem() {
        WorkOrder workOrder = given(StatusWO.UNDER_DIAGNOSIS);
        when(inventoryDataPort.findByIds(List.of(3L))).thenReturn(List.of(OIL_FILTER));

        assertTrue(interactor.recordDiagnosis(42L, "Duas unidades", List.of(
                new DiagnosedItem(true, 3L, 1),
                new DiagnosedItem(true, 3L, 2)), SAGA_ID));

        assertEquals(1, workOrder.getParts().size());
        assertEquals(3, workOrder.getParts().get(0).getQuantity());
        assertEquals(new BigDecimal("105.00"), workOrder.getBudgetValue());
    }

    @Test
    void recordDiagnosis_withAnItemOutsideTheCatalog_isNotFound() {
        given(StatusWO.UNDER_DIAGNOSIS);
        when(autoServiceDataPort.findByIds(anyList())).thenReturn(List.of());

        assertThrows(EntityNotFoundException.class, () -> interactor.recordDiagnosis(42L, "Vazamento",
                List.of(new DiagnosedItem(false, 99L, 1)), SAGA_ID));
    }

    @Test
    void recordDiagnosis_onAnAlreadyPricedWorkOrder_isANoOp() {
        WorkOrder workOrder = given(StatusWO.UNDER_DIAGNOSIS);
        workOrder.setBudgetValue(new BigDecimal("10.00"));

        assertFalse(interactor.recordDiagnosis(42L, "Outro", List.of(), SAGA_ID));
        assertEquals(new BigDecimal("10.00"), workOrder.getBudgetValue());
    }

    @Test
    void historyOf_returnsTheRecordedTransitions() {
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(savedWorkOrder()));
        when(historyPort.findByWorkOrderId(42L)).thenReturn(List.of(
                new WorkOrderStatusChange(42L, BRANCH, StatusWO.RECEIVED, null, SAGA_ID, Instant.now())));

        assertEquals(1, interactor.historyOf(42L).size());
    }

    @Test
    void historyOf_onAnUnknownWorkOrder_isNotFound() {
        when(workOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> interactor.historyOf(99L));
    }

    @Test
    void findForOwner_returnsTheWorkOrderToItsOwner() {
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(savedWorkOrder()));

        assertEquals(42L, interactor.findForOwner(42L, OWNER_CPF).getId());
    }

    @Test
    void findForOwner_refusesAnotherCustomer() {
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(savedWorkOrder()));

        assertEquals("This work order belongs to another customer.",
                assertThrows(ForbiddenException.class,
                        () -> interactor.findForOwner(42L, INTRUDER_CPF)).getMessage());
    }

    @Test
    void findForOwner_onAnUnknownWorkOrder_isNotFound() {
        when(workOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> interactor.findForOwner(99L, OWNER_CPF));
    }

    @Test
    void findForCustomer_doesNotCheckOwnership() {
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(savedWorkOrder()));

        assertEquals(42L, interactor.findForCustomer(42L).getId());
    }

    @Test
    void findAll_returnsEveryWorkOrder() {
        when(workOrderRepository.findAll()).thenReturn(List.of(savedWorkOrder()));

        assertEquals(1, interactor.findAll().size());
    }

    @Test
    void findAllActive_ordersByStatusPriorityThenByCreationDate() {
        WorkOrder received = workOrderAt(StatusWO.RECEIVED, 1L, LocalDateTime.now().minusHours(5));
        WorkOrder awaitingPayment = workOrderAt(StatusWO.AWAITING_PAYMENT, 2L, LocalDateTime.now().minusHours(1));
        WorkOrder inProgress = workOrderAt(StatusWO.IN_PROGRESS, 3L, LocalDateTime.now());
        when(workOrderRepository.findAllActive()).thenReturn(List.of(received, awaitingPayment, inProgress));

        assertEquals(List.of(3L, 2L, 1L), interactor.findAllActive().stream().map(WorkOrder::getId).toList());
    }

    @Test
    void findById_returnsTheWorkOrder() {
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(savedWorkOrder()));

        assertEquals(42L, interactor.findById(42L).getId());
    }

    @Test
    void findById_onAnUnknownWorkOrder_isNotFound() {
        when(workOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> interactor.findById(99L));
    }

    @Test
    void deliverWorkOrder_movesACompletedWorkOrderToDelivered() {
        WorkOrder workOrder = given(StatusWO.COMPLETED);

        assertEquals(StatusWO.DELIVERED, interactor.deliverWorkOrder(42L).getStatus());
        assertEquals(StatusWO.DELIVERED, workOrder.getStatus());
        verify(metricsPort).recordStatusChange(StatusWO.DELIVERED, BRANCH);
    }

    @Test
    void deliverWorkOrder_beforeTheRepairIsComplete_isRefused() {
        when(workOrderRepository.findById(42L)).thenReturn(Optional.of(savedWorkOrder()));

        assertThrows(InvalidOperationException.class, () -> interactor.deliverWorkOrder(42L));
    }

    @Test
    void deleteWorkOrder_softDeletesADeliveredWorkOrder() {
        WorkOrder workOrder = given(StatusWO.DELIVERED);

        interactor.deleteWorkOrder(42L);

        assertTrue(workOrder.isDeleted());
        verify(workOrderRepository).save(workOrder);
    }

    @Test
    void deleteWorkOrder_onAnUnknownWorkOrder_isNotFound() {
        when(workOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> interactor.deleteWorkOrder(99L));
    }

    @Test
    void getAverageCompletionTimeInHours_averagesTheCompletedWorkOrders() {
        LocalDateTime now = LocalDateTime.now();
        WorkOrder twoHours = workOrderAt(StatusWO.COMPLETED, 1L, now.minusHours(2));
        twoHours.setEndDate(now);
        WorkOrder fourHours = workOrderAt(StatusWO.COMPLETED, 2L, now.minusHours(4));
        fourHours.setEndDate(now);
        when(workOrderRepository.findCompleted()).thenReturn(List.of(twoHours, fourHours));

        assertEquals(3.0, interactor.getAverageCompletionTimeInHours(), 0.0001);
    }

    @Test
    void getAverageCompletionTimeInHours_withNothingCompleted_isZero() {
        when(workOrderRepository.findCompleted()).thenReturn(List.of());

        assertEquals(0.0, interactor.getAverageCompletionTimeInHours());
    }
}
