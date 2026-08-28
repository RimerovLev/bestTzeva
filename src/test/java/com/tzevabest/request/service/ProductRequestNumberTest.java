package com.tzevabest.request.service;

import com.tzevabest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-46: номера заявок из последовательности уникальны при конкуренции — без повторных попыток и
 * коллизий случайного числа.
 */
class ProductRequestNumberTest extends IntegrationTest {

    @Autowired
    private ProductRequestService service;

    @Test
    void concurrent_numbers_are_unique() throws Exception {
        int n = 500;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(service::nextNumber);
        }
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        for (Future<String> f : pool.invokeAll(tasks)) {
            numbers.add(f.get());
        }
        pool.shutdown();

        assertThat(numbers).hasSize(n); // 500 различных номеров
    }
}
