package br.com.fiap.postech.carworkshop.branch.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BranchPanacheRepository implements PanacheRepositoryBase<BranchJpaEntity, String> {
}
