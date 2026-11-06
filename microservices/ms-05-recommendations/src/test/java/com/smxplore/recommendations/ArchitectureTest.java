package com.smxplore.recommendations;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Espejo de {@code com.smxplore.users.ArchitectureTest} (ms-01) adaptado al hexagono de ms-05.
 *
 * <p>Diferencias justificadas frente a ms-01:
 * <ul>
 *   <li>Los puertos de dominio ({@code ..domain.ports..}) declaran {@code Mono}/{@code Flux}: el
 *       contrato reactivo de la plataforma obliga a que el dominio conozca Reactor. Por eso la
 *       regla "el dominio no depende de frameworks" excluye ese subpaquete, y una regla aparte
 *       garantiza que los puertos no arrastren el resto de frameworks.</li>
 *   <li>Ademas de Spring, Jakarta, Jackson, Reactor y R2DBC (los de ms-01), se prohiben en el
 *       dominio los clientes de la infraestructura propia de ms-05: Neo4j, Kafka y Lettuce/Redis.
 *       En ms-01 esos clientes no existian, asi que su espejo no los necesitaba.</li>
 * </ul>
 *
 * <p>Las reglas de direccion entre capas (dominio -> aplicacion -> infraestructura) se mantienen
 * identicas al original.
 */
@AnalyzeClasses(packages = "com.smxplore.recommendations", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworks = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .and()
            .resideOutsideOfPackage("..domain.ports..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "org.springframework.data..",
                    "tools.jackson..",
                    "com.fasterxml..",
                    "reactor..",
                    "io.r2dbc..",
                    "org.neo4j..",
                    "org.apache.kafka..",
                    "io.lettuce..");

    @ArchTest
    static final ArchRule domainPortsMustNotDependOnFrameworkInternals = noClasses()
            .that()
            .resideInAPackage("..domain.ports..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "org.springframework.data..",
                    "tools.jackson..",
                    "com.fasterxml..",
                    "io.r2dbc..",
                    "org.neo4j..",
                    "org.apache.kafka..",
                    "io.lettuce..");

    @ArchTest
    static final ArchRule domainMustNotDependOnApplication = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");
}
