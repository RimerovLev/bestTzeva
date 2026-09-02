package com.tzevabest.service;

import com.tzevabest.iam.service.JwtService;
import com.tzevabest.service.domain.ServiceRequestStatus;
import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T-11.3: матрица переходов машины состояний заявки на покраску. */
class ServiceRequestStatesTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JwtService jwt;

    private static final Map<ServiceRequestStatus, Set<ServiceRequestStatus>> ALLOWED =
            new EnumMap<>(ServiceRequestStatus.class);

    static {
        ALLOWED.put(ServiceRequestStatus.NEW, EnumSet.of(ServiceRequestStatus.CONTACTED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.CONTACTED, EnumSet.of(ServiceRequestStatus.MEASUREMENT_SCHEDULED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.MEASUREMENT_SCHEDULED, EnumSet.of(ServiceRequestStatus.MEASURED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.MEASURED, EnumSet.of(ServiceRequestStatus.QUOTED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.QUOTED, EnumSet.of(ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.ACCEPTED, EnumSet.of(ServiceRequestStatus.IN_PROGRESS, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.IN_PROGRESS, EnumSet.of(ServiceRequestStatus.COMPLETED, ServiceRequestStatus.REJECTED, ServiceRequestStatus.LOST));
        ALLOWED.put(ServiceRequestStatus.COMPLETED, EnumSet.noneOf(ServiceRequestStatus.class));
        ALLOWED.put(ServiceRequestStatus.REJECTED, EnumSet.noneOf(ServiceRequestStatus.class));
        ALLOWED.put(ServiceRequestStatus.LOST, EnumSet.noneOf(ServiceRequestStatus.class));
    }

    private long managerId;

    @BeforeEach
    void manager() {
        managerId = jdbcTemplate.queryForObject("""
                insert into users (email, password_hash, first_name, last_name, role, status)
                values (?, 'x', 'M', 'G', 'MANAGER', 'ACTIVE') returning id""",
                Long.class, "mgr-" + UUID.randomUUID().toString().substring(0, 8) + "@x.test");
    }

    static Stream<Arguments> matrix() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (ServiceRequestStatus from : ServiceRequestStatus.values()) {
            for (ServiceRequestStatus to : ServiceRequestStatus.values()) {
                boolean allowed = ALLOWED.get(from).contains(to);
                b.add(Arguments.of(from, to, allowed));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("matrix")
    void transition_matrix(ServiceRequestStatus from, ServiceRequestStatus to, boolean allowed) {
        long id = seedRequest(from);
        ResponseEntity<String> resp = rest.exchange("/api/v1/admin/service-requests/" + id + "/transitions",
                HttpMethod.POST, new HttpEntity<>("{\"to\":\"" + to.name() + "\"}", headers()), String.class);
        if (allowed) {
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            String status = jdbcTemplate.queryForObject(
                    "select status from service_requests where id = ?", String.class, id);
            assertThat(status).isEqualTo(to.name());
        } else {
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    private long seedRequest(ServiceRequestStatus status) {
        return jdbcTemplate.queryForObject("""
                insert into service_requests (request_number, customer_name, customer_phone, property_type,
                        source, consent_given_at, status, locale)
                values (?, 'Dana', '+972500000000', 'APARTMENT', 'SITE_FORM', now(), ?, 'he') returning id""",
                Long.class, "SR-" + UUID.randomUUID().toString().substring(0, 10), status.name());
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.generateAccess(managerId, "MANAGER", "m@x.test", "he"));
        return h;
    }
}
