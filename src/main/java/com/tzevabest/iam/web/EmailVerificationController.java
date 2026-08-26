package com.tzevabest.iam.web;

import com.tzevabest.common.web.ratelimit.RateLimit;
import com.tzevabest.common.web.security.AuthPrincipal;
import com.tzevabest.iam.dto.VerifyEmailRequest;
import com.tzevabest.iam.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerification;

    @PostMapping("/send-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimit(key = "user", limit = 3, windowSeconds = 3600)
    public void send(@AuthenticationPrincipal AuthPrincipal principal) {
        emailVerification.sendVerification(principal.userId());
    }

    @GetMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyByLink(@RequestParam String token) {
        emailVerification.verify(token);
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerification.verify(request.token());
    }
}
