package com.tzevabest.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Границы модулей: публичны только пакеты {@code service} и {@code dto}. Доступ к чужим
 * {@code domain}, {@code repository}, {@code web}, {@code mapper} запрещён. Модуль
 * {@code common} — общий фундамент (BaseEntity, DTO, web-инфраструктура) — доступен всем.
 */
class ModuleBoundaryTest {

    private static final String BASE = "com.tzevabest";

    private static final String[] MODULES = {
            "common", "iam", "catalog", "inventory", "cart",
            "order", "payment", "shipping", "service", "notification", "admin"
    };

    /** Непубличные подпакеты модуля — доступны только внутри своего модуля. */
    private static final String[] NON_PUBLIC = {"domain", "repository", "web", "mapper"};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void modules_access_foreign_modules_only_via_service_and_dto() {
        for (String module : MODULES) {
            for (String other : MODULES) {
                if (other.equals(module)) {
                    continue;
                }
                // common — общий фундамент, доступен всем модулям.
                if (other.equals("common")) {
                    continue;
                }
                for (String subpackage : NON_PUBLIC) {
                    String foreignPackage = BASE + "." + other + "." + subpackage + "..";

                    ArchRule rule = noClasses()
                            .that().resideInAPackage(BASE + "." + module + "..")
                            .should().accessClassesThat().resideInAPackage(foreignPackage)
                            .allowEmptyShould(true)
                            .because("Модуль '" + module + "' не должен обращаться к '" + other + "."
                                    + subpackage + "'. Публичны только service и dto.");

                    rule.check(classes);
                }
            }
        }
    }
}
