package com.example.bookingsystem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.bookingsystem.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingWorkflowTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:17-alpine"
    );

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BookingService service;

    @Autowired
    ObjectMapper json;

    long slot, subject, customer, other;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("UPDATE availability_slots SET removed=false");
        String hash = new BCryptPasswordEncoder().encode("TestPassword123!");
        jdbc.update("UPDATE users SET password_hash=?", hash);
        jdbc.update(
            "INSERT INTO users(username,password_hash,full_name,role) VALUES('second.student',?,'Second Student','CUSTOMER') ON CONFLICT(username) DO NOTHING",
            hash
        );
        slot = jdbc.queryForObject(
            "SELECT id FROM availability_slots WHERE starts_at>CURRENT_TIMESTAMP ORDER BY id LIMIT 1",
            Long.class
        );
        subject = jdbc.queryForObject(
            "SELECT service_id FROM availability_slots WHERE id=?",
            Long.class,
            slot
        );
        customer = jdbc.queryForObject(
            "SELECT id FROM users WHERE username='alex.student'",
            Long.class
        );
        other = jdbc.queryForObject(
            "SELECT id FROM users WHERE username='second.student'",
            Long.class
        );
    }

    MockHttpSession login(String username) throws Exception {
        var session = (MockHttpSession) mvc
            .perform(get("/api/auth/session"))
            .andReturn()
            .getRequest()
            .getSession();
        mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header("X-CSRF-Token", session.getAttribute("csrf"))
                .contentType("application/json")
                .content(
                    "{\"username\":\"" +
                        username +
                        "\",\"password\":\"TestPassword123!\"}"
                )
        ).andExpect(status().isOk());
        return session;
    }

    @Test
    void twoConcurrentCustomersProduceExactlyOneBooking() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.function.LongFunction<Callable<Integer>> attempt = id ->
                () -> {
                    ready.countDown();
                    if (
                        !start.await(5, TimeUnit.SECONDS)
                    ) throw new AssertionError("Start timed out");
                    try {
                        service.book(id, slot, subject);
                        return 201;
                    } catch (ResponseStatusException e) {
                        return e.getStatusCode().value();
                    }
                };
            var first = executor.submit(attempt.apply(customer));
            var second = executor.submit(attempt.apply(other));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                java.util.List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
                )
            ).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE slot_id=? AND status='BOOKED'",
                Integer.class,
                slot
            )
        ).isEqualTo(1);
    }

    @Test
    void bookingCancelAndRebookingPreserveHistory() throws Exception {
        var session = login("alex.student");
        var result = mvc
            .perform(
                post("/api/customer/appointments")
                    .session(session)
                    .header("X-CSRF-Token", session.getAttribute("csrf"))
                    .contentType("application/json")
                    .content(
                        "{\"slotId\":" +
                            slot +
                            ",\"serviceId\":" +
                            subject +
                            "}"
                    )
            )
            .andExpect(status().isCreated())
            .andReturn();
        long id = json
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asLong();
        var stranger = login("second.student");
        mvc.perform(
            delete("/api/customer/appointments/" + id)
                .session(stranger)
                .header("X-CSRF-Token", stranger.getAttribute("csrf"))
        ).andExpect(status().isForbidden());
        mvc.perform(
            delete("/api/customer/appointments/" + id)
                .session(session)
                .header("X-CSRF-Token", session.getAttribute("csrf"))
        ).andExpect(status().isNoContent());
        service.book(other, slot, subject);
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE slot_id=?",
                Integer.class,
                slot
            )
        ).isEqualTo(2);
    }

    @Test
    void sessionRoleAndCsrfAreEnforced() throws Exception {
        mvc.perform(get("/api/customer/appointments")).andExpect(
            status().isUnauthorized()
        );
        var customerSession = login("alex.student");
        mvc.perform(
            get("/api/provider;ignored/appointments").session(customerSession)
        ).andExpect(status().isForbidden());
        var tutor = login("maya.chen");
        mvc.perform(
            get("/api/provider/appointments").session(customerSession)
        ).andExpect(status().isForbidden());
        mvc.perform(get("/api/customer/appointments").session(tutor)).andExpect(
            status().isForbidden()
        );
        mvc.perform(
            post("/api/customer/appointments")
                .session(customerSession)
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isForbidden());
        mvc.perform(
            post("/api/customer/appointments")
                .session(customerSession)
                .header("X-CSRF-Token", customerSession.getAttribute("csrf"))
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isBadRequest());
    }

    @Test
    void filtersAndPaginationAreAppliedInDatabase() throws Exception {
        long provider = jdbc.queryForObject(
            "SELECT provider_id FROM availability_slots WHERE id=?",
            Long.class,
            slot
        );
        mvc.perform(
            get("/api/slots")
                .param("providerId", "" + provider)
                .param("serviceId", "" + subject)
                .param("size", "1")
                .param("page", "0")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].providerId").value(provider))
            .andExpect(jsonPath("$[0].serviceId").value(subject));
        mvc.perform(get("/api/slots").param("size", "0")).andExpect(
            status().isBadRequest()
        );
    }

    @Test
    void tutorCreatesAndRemovesAvailability() throws Exception {
        var tutor = login("maya.chen");
        var result = mvc
            .perform(
                post("/api/provider/slots")
                    .session(tutor)
                    .header("X-CSRF-Token", tutor.getAttribute("csrf"))
                    .contentType("application/json")
                    .content(
                        "{\"serviceId\":" +
                            subject +
                            ",\"startsAt\":\"" +
                            java.time.OffsetDateTime.now().plusDays(60) +
                            "\"}"
                    )
            )
            .andExpect(status().isCreated())
            .andReturn();
        long id = json
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asLong();
        mvc.perform(
            delete("/api/provider/slots/" + id)
                .session(tutor)
                .header("X-CSRF-Token", tutor.getAttribute("csrf"))
        ).andExpect(status().isNoContent());
        assertThat(
            jdbc.queryForObject(
                "SELECT removed FROM availability_slots WHERE id=?",
                Boolean.class,
                id
            )
        ).isTrue();
        jdbc.update("DELETE FROM availability_slots WHERE id=?", id);
    }

    @Test
    void endedAppointmentsBecomeCompleted() {
        long id = service.book(customer, slot, subject);
        jdbc.update(
            "UPDATE availability_slots SET starts_at=starts_at-INTERVAL '30 days',ends_at=ends_at-INTERVAL '30 days' WHERE id=?",
            slot
        );
        assertThat(service.appointments(customer, false)).anyMatch(
            a -> a.id() == id && a.status().equals("COMPLETED")
        );
        jdbc.update(
            "UPDATE availability_slots SET starts_at=starts_at+INTERVAL '30 days',ends_at=ends_at+INTERVAL '30 days' WHERE id=?",
            slot
        );
    }
}
