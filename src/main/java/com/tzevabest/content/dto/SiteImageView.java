package com.tzevabest.content.dto;

/** Слот картинки сайта для админки: текущий URL (null — не задан, витрина берёт дефолт). */
public record SiteImageView(String slot, String url, String contentType, String updatedAt) {
}
