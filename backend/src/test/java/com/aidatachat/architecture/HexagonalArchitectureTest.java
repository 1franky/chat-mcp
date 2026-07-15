package com.aidatachat.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.aidatachat")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_HAS_NO_FRAMEWORK_DEPENDENCIES =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "org.springframework.ai..",
                            "io.modelcontextprotocol..");

    @ArchTest
    static final ArchRule APPLICATION_HAS_NO_ADAPTER_DEPENDENCIES =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..adapters..", "..configuration..", "..infrastructure..", "..web..");
}
