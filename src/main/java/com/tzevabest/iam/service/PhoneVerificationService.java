package com.tzevabest.iam.service;

import com.tzevabest.common.web.error.BadRequestException;
import com.tzevabest.common.web.error.BusinessException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.iam.domain.User;
import com.tzevabest.iam.repository.UserRepository;
import com.tzevabest.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/** Подтверждение телефона по OTP: 6 цифр, 5 минут, ≤5 попыток, повтор не чаще 60с. */
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PhoneOtpStore otp;
    private final NotificationSender notifications;

    public void sendOtp(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("common.resource.not_found"));
        if (user.getPhone() == null) {
            throw new BadRequestException("common.auth.no_phone");
        }
        if (!otp.canSend(userId)) {
            throw new BusinessException("common.auth.otp_cooldown", HttpStatus.TOO_MANY_REQUESTS);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otp.issue(userId, code, TTL, COOLDOWN);
        notifications.sendSms(user.getPhone(), "Tzeva Best code: " + code);
    }

    @Transactional
    public void verifyOtp(long userId, String code) {
        long result = otp.verify(userId, code, MAX_ATTEMPTS);
        if (result == 1) {
            users.findById(userId).ifPresent(u -> u.setPhoneVerifiedAt(Instant.now()));
            return;
        }
        if (result == -1) {
            throw new BusinessException("common.auth.otp_too_many", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (result == -2) {
            throw new BadRequestException("common.auth.otp_expired");
        }
        throw new BadRequestException("common.auth.otp_invalid");
    }
}
