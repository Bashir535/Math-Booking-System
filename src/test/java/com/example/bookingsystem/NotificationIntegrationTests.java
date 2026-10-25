package com.example.bookingsystem;

import static org.assertj.core.api.Assertions.*;

import com.example.bookingsystem.notification.*;
import com.example.bookingsystem.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "notification.worker.enabled=false",
        "notification.timeout-ms=150",
        "notification.api-key=test-key",
    }
)
@Testcontainers
class NotificationIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static HttpServer proxy;
    static final ExecutorService proxyThreads = Executors.newCachedThreadPool();
    static final AtomicInteger requests = new AtomicInteger();
    static volatile String mode = "UP";
    static volatile int applicationPort;
    static volatile boolean sawCommittedBooking;
    static JdbcTemplate database;
    static final ObjectMapper mapper = new ObjectMapper();
    static final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws Exception {
        proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.setExecutor(proxyThreads);
        proxy.createContext("/notify", exchange -> {
            requests.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            try {
                UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
                sawCommittedBooking = Boolean.TRUE.equals(
                    database.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM notification_outbox n JOIN appointments a ON a.id=n.appointment_id WHERE n.id=?)
                        """,
                        Boolean.class,
                        id
                    )
                );
                if (mode.equals("DOWN")) {
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
                var response = http.send(
                    HttpRequest.newBuilder(
                        URI.create(
                            "http://localhost:" + applicationPort + "/internal/notifications"
                        )
                    )
                        .header("Content-Type", "application/json")
                        .header("X-Notification-Key", "test-key")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                    HttpResponse.BodyHandlers.discarding()
                );
                if (mode.equals("SLOW_ACK")) Thread.sleep(500);
                exchange.sendResponseHeaders(response.statusCode(), -1);
            } catch (Exception e) {
                exchange.close();
            } finally {
                exchange.close();
            }
        });
        proxy.start();
        r.add(
            "notification.url",
            () -> "http://127.0.0.1:" + proxy.getAddress().getPort() + "/notify"
        );
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    BookingService bookings;

    @Autowired
    NotificationDispatcher dispatcher;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactions;

    @LocalServerPort
    int port;

    long customer, slot, subject;

    @BeforeEach
    void setup() {
        mode = "UP";
        requests.set(0);
        sawCommittedBooking = false;
        applicationPort = port;
        database = jdbc;
        jdbc.update("DELETE FROM notification_receipts");
        jdbc.update("DELETE FROM appointments");
        customer = jdbc.queryForObject(
            "SELECT id FROM users WHERE username='alex.student'",
            Long.class
        );
        slot = jdbc.queryForObject(
            "SELECT id FROM availability_slots WHERE NOT removed AND starts_at>CURRENT_TIMESTAMP ORDER BY id LIMIT 1",
            Long.class
        );
        subject = jdbc.queryForObject(
            "SELECT service_id FROM availability_slots WHERE id=?",
            Long.class,
            slot
        );
    }

    @AfterAll
    static void close() {
        if (proxy != null) proxy.stop(0);
        proxyThreads.shutdownNow();
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    void due() {
        jdbc.update(
            "UPDATE notification_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE delivered_at IS NULL"
        );
    }

    HttpResponse<String> get(String path) throws Exception {
        return http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void bookingSucceedsWhileNotificationServiceIsDownAndRecovers() throws Exception {
        mode = "DOWN";
        long id = bookings.book(customer, slot, subject);
        assertThat(requests.get()).isZero();
        assertThat(
            jdbc.queryForObject("SELECT status FROM appointments WHERE id=?", String.class, id)
        ).isEqualTo("BOOKED");
        assertThat(dispatcher.dispatchNext()).isTrue();
        assertThat(requests.get()).isEqualTo(3);
        assertThat(sawCommittedBooking).isTrue();
        assertThat(count("notification_receipts")).isZero();
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE delivered_at IS NULL",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            mapper.readTree(get("/metrics").body()).get("bookings_created_total").asInt()
        ).isEqualTo(1);
        mode = "UP";
        due();
        dispatcher.dispatchNext();
        assertThat(count("notification_receipts")).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE delivered_at IS NOT NULL",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    void uncommittedAndRolledBackBookingsNeverNotify() {
        new TransactionTemplate(transactions).execute(status -> {
            bookings.book(customer, slot, subject);
            try (var worker = Executors.newSingleThreadExecutor()) {
                assertThat(
                    worker.submit(() -> dispatcher.dispatchNext()).get(5, TimeUnit.SECONDS)
                ).isFalse();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            status.setRollbackOnly();
            return null;
        });
        assertThat(count("appointments")).isZero();
        assertThat(count("notification_outbox")).isZero();
        assertThat(requests.get()).isZero();
    }

    @Test
    void slowAcknowledgementRetriesHaveOneSideEffect() {
        mode = "SLOW_ACK";
        bookings.book(customer, slot, subject);
        long start = System.nanoTime();
        dispatcher.dispatchNext();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        assertThat(requests.get()).isEqualTo(3);
        assertThat(count("notification_receipts")).isEqualTo(1);
        mode = "UP";
        due();
        dispatcher.dispatchNext();
        assertThat(count("notification_receipts")).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE delivered_at IS NOT NULL",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    void cancellationSendsItsOwnConfirmationOnlyOnce() {
        long id = bookings.book(customer, slot, subject);
        dispatcher.dispatchNext();
        bookings.cancel(customer, id);
        bookings.cancel(customer, id);
        dispatcher.dispatchNext();
        assertThat(count("notification_receipts")).isEqualTo(2);
        assertThat(
            jdbc.queryForList("SELECT type FROM notification_receipts", String.class)
        ).containsExactlyInAnyOrder("BOOKED", "CANCELLED");
        assertThat(count("notification_outbox")).isEqualTo(2);
    }

    @Test
    void concurrentWorkersClaimAnEventOnlyOnce() throws Exception {
        bookings.book(customer, slot, subject);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Boolean> task = () -> {
                gate.await();
                return dispatcher.dispatchNext();
            };
            var a = workers.submit(task);
            var b = workers.submit(task);
            gate.countDown();
            assertThat(
                java.util.List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(true, false);
        }
        assertThat(count("notification_receipts")).isEqualTo(1);
    }

    @Test
    void healthIncludesDatabaseConnectivity() throws Exception {
        var response = get("/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("database").asText()).isEqualTo("UP");
    }

    @Test
    void mockRejectsUnauthorizedAndChangedDuplicatePayload() throws Exception {
        var id = UUID.randomUUID();
        String body = mapper.writeValueAsString(
            new NotificationRequest(
                id,
                "alex.student",
                NotificationRequest.Type.BOOKED,
                "Confirmed."
            )
        );
        var request = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/internal/notifications")
        ).header("Content-Type", "application/json");
        assertThat(
            http
                .send(
                    request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                .statusCode()
        ).isEqualTo(403);
        request.header("X-Notification-Key", "test-key");
        assertThat(
            http
                .send(
                    request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                .statusCode()
        ).isEqualTo(200);
        assertThat(
            http
                .send(
                    request
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                body.replace("Confirmed.", "Changed.")
                            )
                        )
                        .build(),
                    HttpResponse.BodyHandlers.discarding()
                )
                .statusCode()
        ).isEqualTo(409);
        assertThat(count("notification_receipts")).isEqualTo(1);
    }

    @Test
    void crashedDeliveryLeaseCanBeReclaimed() {
        bookings.book(customer, slot, subject);
        var repository = new NotificationRepository(jdbc);
        assertThat(repository.claim()).isPresent();
        assertThat(dispatcher.dispatchNext()).isFalse();
        due();
        assertThat(dispatcher.dispatchNext()).isTrue();
        assertThat(count("notification_receipts")).isEqualTo(1);
    }
}
