package com.example.booking_system;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        long nextBookingId = bookAsUser(slotId);
        mvc.perform(post("/bookings/{id}/cancel", bookingId).with(httpBasic("user", "api-test-user")))
                .andExpect(status().isNoContent());
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
            bookings.book(slotId, 1L);
            entityManager.flush();
            transaction.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select status from slots where id = ?", String.class, slotId))
                .isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from bookings where slot_id = ?", Integer.class, slotId))
                .isZero();
    }

    private long createSlot() throws Exception {
        return readId(mvc.perform(post("/slots").with(httpBasic("admin", "api-test-admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(SLOT_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn());
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
