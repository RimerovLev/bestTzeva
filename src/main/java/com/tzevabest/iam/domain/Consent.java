package com.tzevabest.iam.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.generator.EventType;

import java.time.Instant;

/** Согласие пользователя (append-only, израильский закон о защите приватности). */
@Entity
@Table(name = "consents")
@Immutable
@Getter
@Setter
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contact", nullable = false)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 24)
    private ConsentType consentType;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "source", length = 64)
    private String source;

    /** Версия редакции текста согласия, на которую согласился пользователь (T-14.2). */
    @Column(name = "policy_version", length = 32)
    private String policyVersion;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;
}
