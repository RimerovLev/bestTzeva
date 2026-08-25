package com.tzevabest.service.domain;

/** Источник заявки. varchar + check ck_service_requests_source. */
public enum RequestSource {
    SITE_FORM, PHONE, WHATSAPP
}
