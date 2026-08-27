package com.tzevabest.request.domain;

/** Статус заявки на товар. Переходы (машина состояний) — T-15.2. */
public enum ProductRequestStatus { NEW, CONTACTED, RESERVED, COMPLETED, CANCELLED, LOST }
