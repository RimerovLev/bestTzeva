package com.tzevabest.catalog.dto;

import java.util.List;

/** Итог импорта: режим, счётчики и построчные ошибки. */
public record ImportResult(boolean dryRun, int created, int updated, int skipped, List<ImportError> errors) {
}
