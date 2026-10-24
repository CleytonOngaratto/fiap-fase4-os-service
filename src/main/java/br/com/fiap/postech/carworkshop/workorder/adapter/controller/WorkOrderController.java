package br.com.fiap.postech.carworkshop.workorder.adapter.controller;

import br.com.fiap.postech.carworkshop.saga.usecase.port.in.SagaOrchestratorUseCase;
import br.com.fiap.postech.carworkshop.shared.domain.CancelReason;
import br.com.fiap.postech.carworkshop.workorder.adapter.dto.WorkOrderRequest;
import br.com.fiap.postech.carworkshop.workorder.adapter.presenter.WorkOrderDetailResponse;
import br.com.fiap.postech.carworkshop.workorder.adapter.presenter.WorkOrderResponse;
import br.com.fiap.postech.carworkshop.workorder.adapter.presenter.WorkOrderStatusChangeResponse;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.OpenWorkOrderCommand;
import br.com.fiap.postech.carworkshop.workorder.usecase.port.in.WorkOrderUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/work-orders")
@Tag(name = "Work Orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkOrderController {

    @Inject
    WorkOrderUseCase useCase;

    @Inject
    SagaOrchestratorUseCase saga;

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "List all work orders (including deleted)")
    public Response findAll() {
        List<WorkOrderDetailResponse> workOrders = useCase.findAll().stream()
                .map(WorkOrderDetailResponse::from).toList();
        return Response.ok(workOrders).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Path("/active")
    @Operation(summary = "List active work orders ordered by priority")
    public Response findAllActive() {
        List<WorkOrderDetailResponse> workOrders = useCase.findAllActive().stream()
                .map(WorkOrderDetailResponse::from).toList();
        return Response.ok(workOrders).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Path("/{id}")
    @Operation(summary = "Get work order by ID")
    public Response findById(@Parameter(description = "Work order ID") @PathParam("id") Long id) {
        return Response.ok(WorkOrderDetailResponse.from(useCase.findById(id))).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Path("/stats/average-completion-time")
    @Operation(summary = "Get average completion time in hours")
    public Response getAverageCompletionTime() {
        return Response.ok(Map.of("average_hours", useCase.getAverageCompletionTimeInHours())).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(summary = "Open a work order for a customer vehicle at a branch")
    // A transacao vive aqui porque o interactor e produzido por @Produces (instancia criada com new, que o CDI
    // nao intercepta): sem este @Transactional, a OS e a linha do outbox comitariam separadas.
    @Transactional
    public Response create(WorkOrderRequest request) {
        if (request == null) throw new BadRequestException("Request body is required.");
        WorkOrderResponse created = WorkOrderResponse.from(useCase.create(new OpenWorkOrderCommand(
                request.getCustomerId(), request.getVehicleId(),
                request.getBranchId(), request.getComplaint())));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @RolesAllowed("ADMIN")
    @Path("/{id}/history")
    @Operation(summary = "List every status transition of a work order")
    public Response history(@Parameter(description = "Work order ID") @PathParam("id") Long id) {
        List<WorkOrderStatusChangeResponse> history = useCase.historyOf(id).stream()
                .map(WorkOrderStatusChangeResponse::from).toList();
        return Response.ok(history).build();
    }

    @POST
    @RolesAllowed("ADMIN")
    @Path("/{id}/cancel")
    @Operation(summary = "Cancel a work order, undoing whatever the saga already did")
    @Transactional
    public Response cancel(@Parameter(description = "Work order ID") @PathParam("id") Long id) {
        saga.cancel(id, CancelReason.CANCELED_BY_STAFF);
        return Response.ok(WorkOrderDetailResponse.from(useCase.findById(id))).build();
    }

    @PATCH
    @RolesAllowed("ADMIN")
    @Path("/{id}/deliver")
    @Operation(summary = "Mark vehicle as delivered")
    public Response deliverWorkOrder(@Parameter(description = "Work order ID") @PathParam("id") Long id) {
        return Response.ok(WorkOrderDetailResponse.from(useCase.deliverWorkOrder(id))).build();
    }

    @DELETE
    @RolesAllowed("ADMIN")
    @Path("/{id}")
    @Operation(summary = "Soft-delete a completed or delivered work order (logical deletion)")
    public Response deleteWorkOrder(@Parameter(description = "Work order ID") @PathParam("id") Long id) {
        useCase.deleteWorkOrder(id);
        return Response.noContent().build();
    }
}
