package com.tzevabest.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Защита от дрейфа группировки OpenAPI: группы public/admin разделены по пути
 * ({@code /api/v1/admin/**}), поэтому любой контроллер в пакете {@code ..admin..} обязан
 * иметь классовый {@code @RequestMapping} с путём, начинающимся на {@code /admin}. Иначе
 * админский эндпоинт тихо уедет в публичную схему.
 */
class AdminControllerMappingTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tzevabest");

    @Test
    void admin_controllers_must_be_mapped_under_admin() {
        classes()
                .that().resideInAPackage("..admin..")
                .and().areAnnotatedWith(RestController.class)
                .should(beMappedUnderAdmin())
                .allowEmptyShould(true)
                .check(classes);
    }

    private static ArchCondition<JavaClass> beMappedUnderAdmin() {
        return new ArchCondition<>("be mapped under /admin") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                boolean ok = false;
                Optional<RequestMapping> mapping = controller.tryGetAnnotationOfType(RequestMapping.class);
                if (mapping.isPresent()) {
                    for (String path : mapping.get().value()) {
                        if (path.startsWith("/admin")) {
                            ok = true;
                        }
                    }
                    for (String path : mapping.get().path()) {
                        if (path.startsWith("/admin")) {
                            ok = true;
                        }
                    }
                }
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(controller,
                            controller.getName() + " в пакете ..admin.. должен объявлять "
                                    + "@RequestMapping с путём, начинающимся на /admin"));
                }
            }
        };
    }
}
