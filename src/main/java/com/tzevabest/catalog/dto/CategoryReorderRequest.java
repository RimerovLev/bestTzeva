package com.tzevabest.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Массовое изменение sort_order и parent_id одним запросом. */
public record CategoryReorderRequest(@NotEmpty @Valid List<ReorderEntry> items) {
}
