package com.tzevabest.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Таблицу {@code inventory} разрешено менять только из {@code com.tzevabest.inventory} (там весь
 * учёт: журнал движений, денормализация, инвариант резерва). Прямые SQL-обращения к ней из других
 * модулей запрещены — они обошли бы {@code InventoryService} и порвали журнал.
 * <p>
 * ArchUnit не видит содержимое строковых литералов (SQL живёт в строках), поэтому правило проверяется
 * сканированием исходников. Ищем табличные упоминания {@code (from|join|into|update) inventory} —
 * граница слова отсекает {@code inventory_movements} и {@code v_inventory_available} (подчёркивание
 * — символ слова, границы нет). Представления доступности (только чтение) не запрещены.
 * <p>
 * Исключение — {@code common.dev.LoadDataGenerator}: dev-only сидер (profile {@code local}),
 * наполняет базовый остаток пакетной вставкой при генерации нагрузочных данных, а не двигает остаток
 * в рантайме.
 */
class InventoryTableAccessTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static final Pattern SQL_INVENTORY =
            Pattern.compile("(?i)\\b(from|join|into|update)\\s+inventory\\b");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");

    @Test
    void only_inventory_module_touches_inventory_table_in_sql() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(InventoryTableAccessTest::isEnforced)
                    .forEach(p -> {
                        String code = stripComments(read(p));
                        Matcher m = SQL_INVENTORY.matcher(code);
                        if (m.find()) {
                            offenders.add(MAIN.relativize(p).toString() + " → \"" + m.group() + "\"");
                        }
                    });
        }
        assertThat(offenders)
                .as("Прямые SQL-обращения к таблице inventory вне модуля inventory: %s", offenders)
                .isEmpty();
    }

    /** Правило действует на все классы, кроме самого модуля inventory и dev-сидера. */
    private static boolean isEnforced(Path p) {
        String path = p.toString().replace('\\', '/');
        return !path.contains("/com/tzevabest/inventory/")
                && !path.contains("/com/tzevabest/common/dev/");
    }

    private static String stripComments(String code) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(code).replaceAll(" ")).replaceAll(" ");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
