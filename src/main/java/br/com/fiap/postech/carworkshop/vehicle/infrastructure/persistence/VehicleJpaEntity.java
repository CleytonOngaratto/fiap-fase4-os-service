package br.com.fiap.postech.carworkshop.vehicle.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleJpaEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehicles_seq")
    @SequenceGenerator(name = "vehicles_seq", sequenceName = "vehicles_seq", allocationSize = 1)
    public Long id;

    private String vehiclePlate;
    private String manufacturer;
    private String modelName;
    private Integer modelYear;

    @Column(name = "owner_id")
    private Long customerId;
}
