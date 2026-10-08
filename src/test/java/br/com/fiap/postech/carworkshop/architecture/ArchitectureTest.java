package br.com.fiap.postech.carworkshop.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

class ArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.com.fiap.postech.carworkshop");
    }

    @Test
    void domain_is_framework_free() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..", "io.quarkus..", "org.hibernate..")
                .as("domain must not depend on jakarta.*, io.quarkus.* or org.hibernate.*")
                .check(productionClasses);
    }

    @Test
    void usecase_does_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage("..usecase..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .as("usecase must not depend on infrastructure..")
                .check(productionClasses);
    }

    @Test
    void usecase_does_not_depend_on_adapter() {
        freeze(
                noClasses()
                        .that().resideInAPackage("..usecase..")
                        .should().dependOnClassesThat().resideInAPackage("..adapter..")
                        .as("usecase must not depend on adapter..")
        ).check(productionClasses);
    }

    @Test
    void workorder_gateways_do_not_reach_other_modules_infrastructure() {
        noClasses()
                .that().resideInAPackage("..workorder.adapter.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..customer.infrastructure..",
                        "..vehicle.infrastructure..",
                        "..autoservice.infrastructure..",
                        "..inventory.infrastructure..")
                .as("workorder gateways must depend on other modules' public gateways, not their infrastructure")
                .check(productionClasses);
    }
}
