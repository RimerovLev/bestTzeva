package com.tzevabest.iam.web;

import com.tzevabest.common.web.ratelimit.RateLimit;
import com.tzevabest.common.web.security.AuthPrincipal;
import com.tzevabest.iam.dto.VerifyOtpRequest;
import com.tzevabest.iam.service.PhoneVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerification;

    @PostMapping("/send-otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimit(key = "user", limit = 3, windowSeconds = 3600)
    public void sendOtp(@AuthenticationPrincipal AuthPrincipal principal) {
        phoneVerification.sendOtp(principal.userId());
    }

    @PostMapping("/verify-otp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyOtp(@AuthenticationPrincipal AuthPrincipal principal,
                          @Valid @RequestBody VerifyOtpRequest request) {
        phoneVerification.verifyOtp(principal.userId(), request.code());
    }
}
