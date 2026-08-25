package com.tzevabest.catalog.domain;

/** Тип краски. Хранится как varchar + check ck_products_paint_type. */
public enum PaintType {
    INTERIOR, EXTERIOR, WOOD, METAL, PRIMER, ENAMEL, OTHER
}
