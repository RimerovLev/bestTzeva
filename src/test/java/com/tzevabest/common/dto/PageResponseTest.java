package com.tzevabest.common.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void of_computes_total_pages_by_ceiling() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 0, 20, 45);

        assertThat(response.totalElements()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void of_handles_empty_result() {
        PageResponse<String> response = PageResponse.of(List.of(), 0, 20, 0);
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void from_spring_page_maps_all_fields() {
        var page = new PageImpl<>(List.of("x", "y", "z"), PageRequest.of(1, 10), 25);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.items()).containsExactly("x", "y", "z");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
