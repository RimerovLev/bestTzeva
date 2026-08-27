package com.tzevabest.catalog.dto;

/** Один элемент массового переупорядочивания: новая позиция и родитель категории. */
public record ReorderEntry(long id, Long parentId, int sortOrder) {
}
