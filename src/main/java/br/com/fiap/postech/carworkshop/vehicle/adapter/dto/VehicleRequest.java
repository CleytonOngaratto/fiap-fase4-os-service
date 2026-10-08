package br.com.fiap.postech.carworkshop.vehicle.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record VehicleRequest(String vehiclePlate, String manufacturer, String modelName, Integer modelYear,
                              Long customerId) {}
