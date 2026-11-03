package com.tzevabest.content.domain;

import java.util.Optional;

/** Допустимые слоты редактируемых картинок сайта (овнер меняет их из админки). */
public enum SiteImageSlot {
    HERO_CAN("hero_can"),
    WORKS_BEFORE("works_before"),
    WORKS_AFTER("works_after"),
    BANNER("banner"),
    LOGO("logo"),
    SOCIAL_1("social_1"),
    SOCIAL_2("social_2"),
    SOCIAL_3("social_3"),
    SOCIAL_4("social_4"),
    SOCIAL_5("social_5"),
    SOCIAL_6("social_6");

    private final String slug;

    SiteImageSlot(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Optional<SiteImageSlot> fromSlug(String slug) {
        for (SiteImageSlot s : values()) {
            if (s.slug.equals(slug)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
