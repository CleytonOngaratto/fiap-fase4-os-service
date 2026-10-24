package br.com.fiap.postech.carworkshop.workorder.usecase.interactor;

import br.com.fiap.postech.carworkshop.autoservice.domain.entity.AutoService;
import br.com.fiap.postech.carworkshop.inventory.domain.entity.PartsAndSupply;
import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.shared.domain.exception.EntityNotFoundException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ForbiddenException;
import br.com.fiap.postech.carworkshop.shared.domain.exception.ValidationException;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.StatusWO;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrder;
import br.com.fiap.postech.carworkshop.workorder.domain.entity.WorkOrderStatusChange;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.DiagnosedItem;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.OpenWorkOrderCommand;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderTransition;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
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
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class WorkOrderInteractor implements WorkOrderUseCase {

    private static final Map<StatusWO, Integer> STATUS_PRIORITY = Map.of(
            StatusWO.IN_PROGRESS, 1,
            StatusWO.AWAITING_PAYMENT, 2,
            StatusWO.PENDING_APPROVAL, 3,
            StatusWO.UNDER_DIAGNOSIS, 4,
            StatusWO.RECEIVED, 5
    );

    private final WorkOrderRepositoryPort workOrderRepository;
    private final CustomerDataPort customerDataPort;
    private final VehicleDataPort vehicleDataPort;
    private final BranchDataPort branchDataPort;
    private final AutoServiceDataPort autoServiceDataPort;
    private final InventoryDataPort inventoryDataPort;
    private final WorkOrderHistoryPort historyPort;
    private final WorkOrderNotificationPort notificationPort;
    private final WorkOrderMetricsPort metricsPort;
    private final SagaOrchestratorUseCase saga;
    private final CorrelationIdPort correlationIdPort;

    public WorkOrderInteractor(WorkOrderRepositoryPort workOrderRepository,
                               CustomerDataPort customerDataPort,
                               VehicleDataPort vehicleDataPort,
                               BranchDataPort branchDataPort,
                               AutoServiceDataPort autoServiceDataPort,
                               InventoryDataPort inventoryDataPort,
                               WorkOrderHistoryPort historyPort,
                               WorkOrderNotificationPort notificationPort,
                               WorkOrderMetricsPort metricsPort,
                               SagaOrchestratorUseCase saga,
                               CorrelationIdPort correlationIdPort) {
        this.workOrderRepository = workOrderRepository;
        this.customerDataPort = customerDataPort;
        this.vehicleDataPort = vehicleDataPort;
        this.branchDataPort = branchDataPort;
        this.autoServiceDataPort = autoServiceDataPort;
        this.inventoryDataPort = inventoryDataPort;
        this.historyPort = historyPort;
        this.notificationPort = notificationPort;
        this.metricsPort = metricsPort;
        this.saga = saga;
        this.correlationIdPort = correlationIdPort;
    }

    @Override
    public WorkOrder create(OpenWorkOrderCommand command) {
        if (command.customerId() == null) {
            throw new ValidationException("customer_id is required.");
        }
        if (command.vehicleId() == null) {
            throw new ValidationException("vehicle_id is required.");
        }
        if (isBlank(command.branchId())) {
            throw new ValidationException("branch_id is required.");
        }
        if (isBlank(command.complaint())) {
            throw new ValidationException("complaint is required.");
        }

        CustomerDataPort.CustomerInfo customer = customerDataPort.findById(command.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found."));

        VehicleDataPort.VehicleInfo vehicleInfo = vehicleDataPort.findById(command.vehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found."));

        if (!branchDataPort.exists(command.branchId())) {
            throw new EntityNotFoundException("Branch not found.");
        }

        WorkOrder workOrder = WorkOrder.create(
                customer.id(), customer.email(), customer.document(),
                vehicleInfo.id(), vehicleInfo.vehiclePlate(),
                command.branchId(), command.complaint()
        );
        WorkOrder saved = workOrderRepository.save(workOrder);
        saga.start(saved.getId(), saved.getBranchId(), saved.getVehiclePlate(), saved.getComplaint(),
                correlationIdPort.current());
        log.info("Work Order {} created.", saved.getId());
        recordTransition(saved, null, null);
        return saved;
    }

    @Override
    public boolean applySagaTransition(Long id, WorkOrderTransition transition, UUID sagaId,
                                      CancelReason reason) {
        WorkOrder workOrder = requireWorkOrder(id);
        boolean moved = switch (transition) {
            case START_DIAGNOSIS -> workOrder.startDiagnosis();
            case PEND_APPROVAL -> workOrder.pendApproval();
            case AWAIT_PAYMENT -> workOrder.awaitPayment();
            case START_REPAIR -> workOrder.startRepair();
            case COMPLETE -> workOrder.completeService();
            case CANCEL -> workOrder.cancel(reason);
        };
        if (!moved) {
            log.info("Work Order {} is already in {}: {} is a no-op.", id, workOrder.getStatus(), transition);
            return false;
        }
        WorkOrder saved = workOrderRepository.save(workOrder);
        recordTransition(saved, reason, sagaId);
        log.info("Work Order {} status changed to {}.", id, saved.getStatus());

        if (transition == WorkOrderTransition.COMPLETE) {
            metricsPort.recordCompletion(saved.getBranchId(),
                    Duration.between(saved.getCreationDate(), saved.getEndDate()));
            notificationPort.notifyCompleted(new WorkOrderNotificationPort.CompletedNotification(
                    saved.getId(), saved.getCustomerId(), saved.getCustomerEmail()));
        }
        return true;
    }

    @Override
    public boolean recordDiagnosis(Long id, String description, List<DiagnosedItem> items, UUID sagaId) {
        WorkOrder workOrder = requireWorkOrder(id);
        List<DiagnosedItem> merged = mergeByCatalogId(items);
        List<AutoService> services = priceServices(merged);
        List<PartsAndSupply> parts = priceParts(merged);
        BigDecimal total = total(services, parts);

        if (!workOrder.priceDiagnosis(description, services, parts, total)) {
            log.info("Work Order {} was already priced: nothing to do.", id);
            return false;
        }
        workOrderRepository.save(workOrder);
        log.info("Work Order {} priced at {} from the diagnosis.", id, total);
        return true;
    }

    @Override
    public List<WorkOrderStatusChange> historyOf(Long id) {
        requireWorkOrder(id);
        return historyPort.findByWorkOrderId(id);
    }

    @Override
    public List<WorkOrder> findAll() {
        return workOrderRepository.findAll();
    }

    @Override
    public List<WorkOrder> findAllActive() {
        return workOrderRepository.findAllActive().stream()
                .sorted(Comparator.comparingInt((WorkOrder wo) ->
                        STATUS_PRIORITY.getOrDefault(wo.getStatus(), 99))
                        .thenComparing(WorkOrder::getCreationDate))
                .toList();
    }

    @Override
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = requireWorkOrder(id);
        workOrder.softDelete();
        workOrderRepository.save(workOrder);
        log.info("Work Order {} soft-deleted.", id);
    }

    @Override
    public WorkOrder findById(Long id) {
        return requireWorkOrder(id);
    }

    @Override
    public WorkOrder deliverWorkOrder(Long id) {
        WorkOrder workOrder = requireWorkOrder(id);
        workOrder.deliverVehicle();
        WorkOrder saved = workOrderRepository.save(workOrder);
        log.info("Work Order {} delivered.", id);
        recordTransition(saved, null, null);
        return saved;
    }

    @Override
    public WorkOrder findForCustomer(Long id) {
        return requireWorkOrder(id);
    }

    @Override
    public WorkOrder findForOwner(Long id, String requesterDocument) {
        WorkOrder workOrder = requireWorkOrder(id);
        if (!workOrder.isOwnedBy(requesterDocument)) {
            log.warn("Customer tried to read Work Order {}, which belongs to someone else.", id);
            throw new ForbiddenException("This work order belongs to another customer.");
        }
        return workOrder;
    }

    @Override
    public Double getAverageCompletionTimeInHours() {
        List<WorkOrder> completed = workOrderRepository.findCompleted();
        if (completed.isEmpty()) return 0.0;
        double totalMinutes = completed.stream()
                .mapToLong(wo -> ChronoUnit.MINUTES.between(wo.getCreationDate(), wo.getEndDate()))
                .sum();
        return totalMinutes / 60.0 / completed.size();
    }

    // Itens repetidos somam numa linha: a chave de work_order_services e work_order_parts e
    // (work_order_id, catalog_id), e inserir duas vezes violaria a chave.
    private static List<DiagnosedItem> mergeByCatalogId(List<DiagnosedItem> items) {
        if (items == null) {
            return List.of();
        }
        Map<String, DiagnosedItem> merged = new LinkedHashMap<>();
        for (DiagnosedItem item : items) {
            merged.merge(item.part() + ":" + item.catalogId(), item,
                    (a, b) -> new DiagnosedItem(a.part(), a.catalogId(), a.quantity() + b.quantity()));
        }
        return new ArrayList<>(merged.values());
    }

    private List<AutoService> priceServices(List<DiagnosedItem> items) {
        List<Long> ids = items.stream().filter(item -> !item.part()).map(DiagnosedItem::catalogId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AutoService> found = autoServiceDataPort.findByIds(ids);
        if (found.size() != ids.size()) {
            throw new EntityNotFoundException("One or more diagnosed services do not exist in the catalog.");
        }
        return found;
    }

    private List<PartsAndSupply> priceParts(List<DiagnosedItem> items) {
        List<DiagnosedItem> wanted = items.stream().filter(DiagnosedItem::part).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Long> ids = wanted.stream().map(DiagnosedItem::catalogId).toList();
        List<PartsAndSupply> found = inventoryDataPort.findByIds(ids);
        if (found.size() != ids.size()) {
            throw new EntityNotFoundException("One or more diagnosed parts do not exist in the catalog.");
        }
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        wanted.forEach(item -> quantities.put(item.catalogId(), item.quantity()));

        // O snapshot guarda a quantidade PEDIDA pelo diagnostico, nao o estoque da peca no momento.
        return found.stream()
                .map(part -> PartsAndSupply.builder()
                        .id(part.getId())
                        .code(part.getCode())
                        .manufacturer(part.getManufacturer())
                        .description(part.getDescription())
                        .price(part.getPrice())
                        .type(part.getType())
                        .quantity(quantities.getOrDefault(part.getId(), 1))
                        .build())
                .toList();
    }

    private static BigDecimal total(List<AutoService> services, List<PartsAndSupply> parts) {
        BigDecimal total = BigDecimal.ZERO;
        for (AutoService service : services) {
            total = total.add(service.getPrice());
        }
        for (PartsAndSupply part : parts) {
            total = total.add(part.getPrice().multiply(BigDecimal.valueOf(part.getQuantity())));
        }
        return total;
    }

    private WorkOrder requireWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Work Order not found."));
    }

    private void recordTransition(WorkOrder workOrder, CancelReason reason, UUID sagaId) {
        historyPort.record(new WorkOrderStatusChange(workOrder.getId(), workOrder.getBranchId(),
                workOrder.getStatus(), reason, sagaId, Instant.now()));
        metricsPort.recordStatusChange(workOrder.getStatus(), workOrder.getBranchId());
        metricsPort.recordTimeToStatus(
                workOrder.getStatus(),
                workOrder.getBranchId(),
                Duration.between(workOrder.getCreationDate(), LocalDateTime.now()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
