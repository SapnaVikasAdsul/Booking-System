package com.example.booking_system;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.example.booking_system.service.BookingService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking-api-tests",
        "booking.security.accounts.user.password=api-test-user",
        "booking.security.accounts.admin.password=api-test-admin",
        "booking.security.accounts.other.id=3",
        "booking.security.accounts.other.role=USER",
        "booking.security.accounts.other.password=api-test-other"
})
@AutoConfigureMockMvc
class BookingApiTests {

    private static final AtomicLong NEXT_SLOT_HOUR = new AtomicLong();

    private static final String SLOT_REQUEST = """
            {"startTime":"2030-01-01T10:00:00Z","endTime":"2030-01-01T11:00:00Z"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BookingService bookings;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Test
    void requiresAuthenticationForEveryApiAction() throws Exception {
        mvc.perform(get("/slots"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/slots").contentType(MediaType.APPLICATION_JSON).content(SLOT_REQUEST))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(bookingRequest(1L)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/bookings").with(httpBasic("user", "incorrect-test-password"))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest(1L)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/bookings/{id}/cancel", 1L))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/bookings/{id}/cancel", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesRolesAndBookingOwnership() throws Exception {
        mvc.perform(post("/slots").with(httpBasic("user", "api-test-user"))
                        .contentType(MediaType.APPLICATION_JSON).content(SLOT_REQUEST))
                .andExpect(status().isForbidden());

        long slotId = createSlot();
        mvc.perform(get("/slots").with(httpBasic("admin", "api-test-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d && @.status == '%s')]".formatted(slotId, "AVAILABLE")).isNotEmpty());
        mvc.perform(post("/bookings").with(httpBasic("admin", "api-test-admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest(slotId)))
                .andExpect(status().isForbidden());

        long bookingId = bookAsUser(slotId);
        var duplicate = mvc.perform(post("/bookings").with(httpBasic("other", "api-test-other"))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest(slotId)))
                .andReturn();
        assertAlreadyBooked(duplicate, slotId);
        mvc.perform(get("/slots").with(httpBasic("user", "api-test-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d && @.status == '%s')]".formatted(slotId, "BOOKED")).isNotEmpty());

        mvc.perform(post("/admin/bookings/{id}/cancel", bookingId).with(httpBasic("user", "api-test-user")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("admin", "api-test-admin")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("user", "api-test-user"))
                        .header(HttpHeaders.ORIGIN, "https://other.example"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("other", "api-test-other")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("user", "api-test-user")))
                .andExpect(status().isNoContent());

        mvc.perform(post("/bookings/{id}/cancel", Long.MAX_VALUE).with(httpBasic("user", "api-test-user")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/bookings/{id}/cancel", Long.MAX_VALUE).with(httpBasic("admin", "api-test-admin")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("user", "api-test-user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.bookingId").value(bookingId));
        mvc.perform(post("/admin/bookings/{id}/cancel", bookingId).with(httpBasic("admin", "api-test-admin")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_CANCELLED"));

        long nextBookingId = bookAsUser(slotId);
        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("user", "api-test-user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_CANCELLED"));
        mvc.perform(get("/slots").with(httpBasic("user", "api-test-user")))
                .andExpect(jsonPath("$[?(@.id == %d && @.status == '%s')]".formatted(slotId, "BOOKED")).isNotEmpty());

        mvc.perform(post("/admin/bookings/{id}/cancel", nextBookingId).with(httpBasic("admin", "api-test-admin")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/slots").with(httpBasic("user", "api-test-user")))
                .andExpect(jsonPath("$[?(@.id == %d && @.status == '%s')]".formatted(slotId, "AVAILABLE")).isNotEmpty());
    }

    @Test
    void onlyOneConcurrentRequestCanBookTheSameSlot() throws Exception {
        long slotId = createSlot();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> bookAfterStart(start, slotId, "user", "api-test-user"));
            var second = executor.submit(() -> bookAfterStart(start, slotId, "other", "api-test-other"));
            start.countDown();

            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results).extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
            var conflict = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst().orElseThrow();
            assertAlreadyBooked(conflict, slotId);
        }

        assertThat(jdbc.queryForObject("select count(*) from bookings where slot_id = ?", Integer.class, slotId))
                .isEqualTo(1);
    }

    @Test
    void lockTimeoutReturnsARetryableConflict() throws Exception {
        long slotId = createSlot();

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from slots where id = ? for update")) {
                statement.setLong(1, slotId);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
                mvc.perform(post("/bookings").with(httpBasic("user", "api-test-user"))
                                .contentType(MediaType.APPLICATION_JSON).content(bookingRequest(slotId)))
                        .andExpect(status().isConflict())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.code").value("BOOKING_UPDATE_IN_PROGRESS"))
                        .andExpect(jsonPath("$.detail").value("Another request is updating this slot or booking. Please retry."));
            } finally {
                connection.rollback();
            }
        }

        bookAsUser(slotId);
    }

    private MvcResult bookAfterStart(CountDownLatch start, long slotId, String username, String password)
            throws Exception {
        start.await();
        return mvc.perform(post("/bookings").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest(slotId)))
                .andReturn();
    }

    private static void assertAlreadyBooked(MvcResult result, long slotId) throws Exception {
        status().isConflict().match(result);
        content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
        jsonPath("$.code").value("SLOT_ALREADY_BOOKED").match(result);
        jsonPath("$.slotId").value(slotId).match(result);
        jsonPath("$.detail").value("Slot " + slotId + " is already booked.").match(result);
    }

    @Test
    void rollingBackTheBookingAlsoRollsBackTheSlotStatus() throws Exception {
        long slotId = createSlot();

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            long bookingId = bookings.book(slotId, 1L).getId();
            entityManager.flush();

            assertThat(bookingStatus(bookingId)).isEqualTo("ACTIVE");
            assertThat(slotStatus(slotId)).isEqualTo("BOOKED");
            transaction.setRollbackOnly();
        });

        assertThat(slotStatus(slotId)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from bookings where slot_id = ?", Integer.class, slotId))
                .isZero();
    }

    @Test
    void rollingBackCancellationKeepsTheBookingActiveAndTheSlotBooked() throws Exception {
        long slotId = createSlot();
        long bookingId = bookAsUser(slotId);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            bookings.cancel(bookingId, 1L, false);
            entityManager.flush();

            assertThat(bookingStatus(bookingId)).isEqualTo("CANCELLED");
            assertThat(slotStatus(slotId)).isEqualTo("AVAILABLE");
            transaction.setRollbackOnly();
        });

        assertThat(bookingStatus(bookingId)).isEqualTo("ACTIVE");
        assertThat(slotStatus(slotId)).isEqualTo("BOOKED");
    }

    private String slotStatus(long slotId) {
        return jdbc.queryForObject("select status from slots where id = ?", String.class, slotId);
    }

    private String bookingStatus(long bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }

    @Test
    void rejectsMatchingSlotTimesAndAllowsAnOverlappingRange() throws Exception {
        createSlot(slotRequest("2035-01-01T10:00:00.123456789Z", "2035-01-01T11:00:00Z"));

        mvc.perform(createSlotRequest(
                        slotRequest("2035-01-01T15:30:00.123456789+05:30", "2035-01-01T16:30:00+05:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail").value("A slot already exists with this start and end time."));

        createSlot(slotRequest("2035-01-01T10:30:00Z", "2035-01-01T11:30:00Z"));
    }

    @Test
    void onlyOneConcurrentRequestCanCreateTheSameTimeRange() throws Exception {
        String request = slotRequest("2036-01-01T10:00:00Z", "2036-01-01T11:00:00Z");
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcResult> create = () -> {
            start.await();
            return mvc.perform(createSlotRequest(request)).andReturn();
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();

            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results).extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
            var conflict = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst().orElseThrow();
            jsonPath("$.code").value("SLOT_ALREADY_EXISTS").match(conflict);
        }
    }

    @Test
    void rejectsAnAdditionalSlotWhenIdenticalLegacySlotsExist() throws Exception {
        String start = "2037-01-01T10:00:00Z";
        String end = "2037-01-01T11:00:00Z";
        String insert = """
                insert into slots (start_time, end_time, status)
                values (cast(? as timestamp with time zone), cast(? as timestamp with time zone), 'AVAILABLE')
                """;
        jdbc.update(insert, start, end);
        jdbc.update(insert, start, end);

        mvc.perform(createSlotRequest(slotRequest(start, end)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_ALREADY_EXISTS"));
    }

    private long createSlot() throws Exception {
        Instant start = Instant.parse("2030-01-01T10:00:00Z")
                .plusSeconds(3600 * NEXT_SLOT_HOUR.getAndIncrement());
        return createSlot(slotRequest(start.toString(), start.plusSeconds(3600).toString()));
    }

    private long createSlot(String request) throws Exception {
        return readId(mvc.perform(createSlotRequest(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn());
    }

    private static MockHttpServletRequestBuilder createSlotRequest(String request) {
        return post("/slots").with(httpBasic("admin", "api-test-admin"))
                .contentType(MediaType.APPLICATION_JSON).content(request);
    }

    private static String slotRequest(String start, String end) {
        return """
                {"startTime":"%s","endTime":"%s"}
                """.formatted(start, end);
    }

    private long bookAsUser(long slotId) throws Exception {
        return readId(mvc.perform(post("/bookings").with(httpBasic("user", "api-test-user"))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingRequest(slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn());
    }

    private static long readId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String bookingRequest(long slotId) {
        return "{\"slotId\":" + slotId + "}";
    }
}
