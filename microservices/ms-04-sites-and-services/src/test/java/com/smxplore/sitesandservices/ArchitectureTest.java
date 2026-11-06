package com.smxplore.sitesandservices;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.smxplore.sitesandservices",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * El núcleo del dominio (modelo, excepciones y eventos) no puede depender de
     * frameworks. Los puertos de {@code domain.ports} sí exponen Mono/Flux a propósito
     * como contrato reactivo del servicio, por eso quedan fuera de esta regla.
     */
    @ArchTest
    static final ArchRule domainCoreMustNotDependOnFrameworks = noClasses()
            .that()
            .resideInAnyPackage("..domain.model..", "..domain.exception..", "..domain.event..")
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
                    "com.mongodb..",
                    "io.micrometer..");

    @ArchTest
    static final ArchRule domainMustNotDependOnApplication = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule inputPortsAreUseCases = classes()
            .that()
            .resideInAPackage("..domain.ports.in..")
            .should()
            .haveSimpleNameEndingWith("UseCase");

    /**
     * Excepción justificada: {@code ReviewAiFilter} es el puerto del filtro de
     * moderación por IA y conserva el nombre de la abstracción de dominio que implementa
     * (no es un repositorio ni un adaptador con el sufijo Port).
     */
    @ArchTest
    static final ArchRule outputPortsArePorts = classes()
            .that()
            .resideInAPackage("..domain.ports.out..")
            .and()
            .doNotHaveSimpleName("ReviewAiFilter")
            .should()
            .haveSimpleNameEndingWith("Port");

    @ArchTest
    static final ArchRule controllersReturnReactiveTypes = methods()
            .that()
            .arePublic()
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("..infrastructure.adapters.in.web.controller..")
            .should(new ArchCondition<>("return Mono or Flux") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    String returnType = method.getRawReturnType().getName();
                    boolean reactive = "reactor.core.publisher.Mono".equals(returnType)
                            || "reactor.core.publisher.Flux".equals(returnType);
                    if (!reactive) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName() + " returns " + returnType
                                        + "; los controladores WebFlux deben devolver Mono o Flux"));
                    }
                }
            });
}
