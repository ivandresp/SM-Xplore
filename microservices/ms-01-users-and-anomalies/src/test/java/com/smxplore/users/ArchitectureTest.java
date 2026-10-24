package com.smxplore.users;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.smxplore.users", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworks = noClasses()
            .that()
            .resideInAPackage("..identity.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.springframework.data..",
                    "tools.jackson..",
                    "com.fasterxml..");

    @ArchTest
    static final ArchRule domainMustNotDependOnApplication = noClasses()
            .that()
            .resideInAPackage("..identity.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..identity.application..");

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..identity.application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..identity.infrastructure..");
}
