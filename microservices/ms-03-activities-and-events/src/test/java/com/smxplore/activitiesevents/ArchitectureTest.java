package com.smxplore.activitiesevents;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.reactivestreams.Publisher;

/**
 * Reglas de arquitectura del hexagono de ms-03.
 *
 * <p>Es el espejo de {@code microservices/ms-01-users-and-anomalies/src/test/java/com/smxplore/users/
 * ArchitectureTest.java}: dominio sin frameworks, dominio sin application y application sin
 * infrastructure. Las diferencias con ms-01 estan marcadas y justificadas en cada regla.
 */
@AnalyzeClasses(packages = "com.smxplore.activitiesevents", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworks = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "org.springframework.data..",
                    "jakarta..",
                    "tools.jackson..",
                    "com.fasterxml..",
                    "io.r2dbc..");

    /**
     * Excepción justificada: en ms-03 los puertos del dominio son reactivos por diseño, porque WebFlux y
     * R2DBC exponen {@code Mono}/{@code Flux} tambien en los contratos internos (el adaptador no podria
     * cumplirlos sin bloquear). Por eso {@code reactor..} se permite solo en {@code domain.ports} y se
     * sigue prohibiendo en {@code domain.model}/{@code domain.exceptions}, para que el nucleo del negocio
     * sea Java puro y testeable sin el motor reactivo.
     */
    @ArchTest
    static final ArchRule domainCoreMustNotDependOnReactor = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .and()
            .resideOutsideOfPackage("..domain.ports..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("reactor..");

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

    /**
     * Regla extra inspirada en el criterio de T16.3 (controladores reactivos): ms-03 es WebFlux puro y
     * ningun handler puede bloquear. Se comprueba el tipo crudo de retorno contra {@link Publisher},
     * asi que acepta {@code Mono<T>}, {@code Flux<T>} y {@code Mono<ResponseEntity<T>>} sin atarse al
     * parametro generico; si algun handler devolviera un tipo bloqueante la regla falla.
     */
    @ArchTest
    static final ArchRule webControllersMustReturnReactiveTypes = methods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("..infrastructure.adapters.in.web.controller..")
            .and()
            .arePublic()
            .should()
            .haveRawReturnType(assignableTo(Publisher.class));
}
