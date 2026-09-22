package com.example.bookingsystem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class BookingSystemApplicationTests {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void homeReadsCatalogWithoutExposingAccountData() throws Exception {
        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Booking System"))
                .andExpect(jsonPath("$.providers", hasSize(2)))
                .andExpect(jsonPath("$.services", hasSize(3)))
                .andExpect(jsonPath("$.providers[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.providers[0].username").doesNotExist());
    }

    @Test
    void readsAvailableSlotsFromDatabase() throws Exception {
        mvc.perform(get("/api/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(28)))
                .andExpect(jsonPath("$[0].providerName").isString())
                .andExpect(jsonPath("$[0].serviceName").isString());
    }

    @Test
    void databaseRejectsTwoActiveAppointmentsForOneSlot() {
        long slot = firstSlot();
        book(slot);
        assertThatThrownBy(() -> book(slot)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancellationPreservesHistoryAndReopensAvailability() throws Exception {
        long slot = firstSlot();
        book(slot);
        mvc.perform(get("/api/slots"))
                .andExpect(jsonPath("$", hasSize(27)));
        jdbc.update("UPDATE appointments SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE slot_id = ?", slot);
        mvc.perform(get("/api/slots"))
                .andExpect(jsonPath("$", hasSize(28)));
        book(slot);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments WHERE slot_id = ?", Integer.class, slot)).isEqualTo(2);
    }

    @Test
    void databaseRejectsAppointmentForWrongService() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO appointments (customer_id, slot_id, service_id)
                SELECT u.id, sl.id, s.id FROM users u, availability_slots sl, services s
                WHERE u.username = 'alex.student' AND sl.id = ? AND s.id <> sl.service_id LIMIT 1
                """, firstSlot())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsOverlappingTutorAvailability() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO availability_slots (provider_id, service_id, starts_at, ends_at)
                SELECT provider_id, service_id, starts_at + INTERVAL '15 minutes', ends_at + INTERVAL '15 minutes'
                FROM availability_slots WHERE id = ?
                """, firstSlot())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void startupScriptsCanRunAgainWithoutDuplicatingData() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"),
                new ClassPathResource("seed.sql")).execute(dataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM availability_slots", Integer.class)).isEqualTo(28);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(3);
    }

    @Test
    void pastSlotsAreExcluded() throws Exception {
        jdbc.update("UPDATE availability_slots SET starts_at = starts_at - INTERVAL '30 days', ends_at = ends_at - INTERVAL '30 days'");
        mvc.perform(get("/api/slots")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private long firstSlot() {
        return jdbc.queryForObject("SELECT id FROM availability_slots ORDER BY starts_at, id LIMIT 1", Long.class);
    }

    private void book(long slot) {
        jdbc.update("""
                INSERT INTO appointments (customer_id, slot_id, service_id)
                SELECT u.id, sl.id, sl.service_id FROM users u, availability_slots sl
                WHERE u.username = 'alex.student' AND sl.id = ?
                """, slot);
    }
}
