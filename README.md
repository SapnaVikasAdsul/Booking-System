# Booking-System

A Spring Boot REST API for creating time slots, booking them, and cancelling bookings. The API uses HTTP Basic authentication and stores data in a file-backed H2 database.

## Setup and run on Windows

Install **JDK 21**. Open Windows Terminal and select a **Command Prompt** profile. If you already have the project, change to its root folder, the one containing `pom.xml`. Otherwise, clone it first.

```bat
git clone https://github.com/SapnaVikasAdsul/Booking-System.git
cd Booking-System
```

Check that `java -version` reports Java 21. Replace the password placeholders below and run all four commands in the same Command Prompt window. Use those passwords for the `user` and `admin` accounts when making API requests.

```bat
java -version
set "BOOKING_USER_PASSWORD=<choose-a-user-password>"
set "BOOKING_ADMIN_PASSWORD=<choose-an-admin-password>"
mvnw.cmd spring-boot:run
```

The Maven Wrapper downloads Maven and the project dependencies on the first run. Wait for `Started BookingSystemApplication`, then send API requests to `http://localhost:8080`. Leave the terminal open while testing and press **Ctrl+C** to stop the app.

H2 stores its files under `./data/` using the database name `bookingdb`. Bookings and slot statuses survive a restart when the app uses the same database.

## API request flow

Send HTTP Basic credentials on every request. The `user` account has the `USER` role, and the `admin` account has the `ADMIN` role.

| Method and path | Who can use it | Request and result |
| --- | --- | --- |
| `POST /slots` | `ADMIN` | Send `startTime` and `endTime` as ISO 8601 timestamps. Returns `201 Created` with the new `AVAILABLE` slot and its ID. Matching start and end times return `409 Conflict` with `SLOT_ALREADY_EXISTS`. |
| `GET /slots` | `USER` or `ADMIN` | Returns `200 OK` and lists both `AVAILABLE` and `BOOKED` slots, ordered by start time and ID. |
| `POST /bookings` | `USER` | Send `{"slotId": 1}` using a slot ID from the list. Returns `201 Created` with the booking ID and status `ACTIVE`. |
| `POST /bookings/{id}/cancel` | `USER` who owns the booking | Use the booking ID in the path. Returns `204 No Content`. |
| `POST /admin/bookings/{id}/cancel` | `ADMIN` | Use the booking ID in the path for any booking. Returns `204 No Content`. |

## Locking and transaction boundaries

The project uses **pessimistic write locks in H2**. `SlotRepository.findByIdForUpdate` takes a database row lock using JPA's `PESSIMISTIC_WRITE`. The lock lasts until the transaction commits or rolls back.

- **Booking.** `BookingService.book` runs in one `@Transactional` method. It locks the slot before reading its status. If the slot is `AVAILABLE`, the method marks it `BOOKED` and inserts an `ACTIVE` booking. Both writes commit or roll back together.
- **Cancellation.** `BookingService.cancel` locks the booking row, checks who can cancel it, and locks its slot before updating either record. The booking becomes `CANCELLED` and the slot becomes `AVAILABLE` in the same transaction. Cancelling an already-cancelled booking returns `409 Conflict` with `BOOKING_ALREADY_CANCELLED`. A later booking for that slot stays active.

### Two requests for the same slot

Suppose slot `42` is `AVAILABLE` and two users both send `POST /bookings` with `{"slotId": 42}`.

| Step | Request A | Request B |
| --- | --- | --- |
| 1 | Acquires the row lock for slot `42`. | Waits for the same row lock. |
| 2 | Inserts an `ACTIVE` booking, marks the slot `BOOKED`, and commits. It returns `201 Created`. | Acquires the lock after A commits. |
| 3 | | Reads `BOOKED` and returns `409 Conflict` with code `SLOT_ALREADY_BOOKED`. |

If A rolls back, B can acquire the lock and book the still-available slot. If B reaches the database lock timeout, it receives `409 Conflict` with code `BOOKING_UPDATE_IN_PROGRESS` and can retry. Both conflict responses include a human-readable message.

## Assumptions and design decisions

- Each slot can have **one active booking**. Cancellation keeps the existing booking for history and lets another user book the slot.
- Slots represent independent entries created by an admin. A slot's start time must precede its end time. Slots with different time ranges can overlap. Slot creation locks an H2 row while checking for duplicates and inserting a new slot, so identical concurrent requests cannot both succeed.
- The service derives the booking's `userId` from the authenticated account. The H2 foreign key `bookings.slot_id` keeps each booking linked to its slot.
- The default local accounts use passwords from environment variables. Each account is enabled when its password is set.
