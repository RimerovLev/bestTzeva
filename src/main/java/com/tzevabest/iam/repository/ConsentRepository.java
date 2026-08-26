package com.tzevabest.iam.repository;

import com.tzevabest.iam.domain.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Хранилище согласий (append-only: только вставка). */
public interface ConsentRepository extends JpaRepository<Consent, Long> {
}
