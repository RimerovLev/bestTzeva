package com.tzevabest.service.dto;

/** Пара фото «до/после», связанных pair_group. */
public record MediaPair(String pairGroup, String beforeUrl, String afterUrl) {
}
