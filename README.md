# Booking-System

A Spring Boot REST API for creating time slots, booking them, and cancelling bookings. The API uses HTTP Basic authentication and stores data in a file-backed H2 database.

## Setup and run

Install **JDK 21** and run `java -version` to confirm the terminal sees it. The Maven Wrapper downloads Maven and the project dependencies on the first run.

Clone the repository and open its root folder, which contains `pom.xml`.

```text
git clone https://github.com/SapnaVikasAdsul/Booking-System.git
cd Booking-System
```

Set passwords for the `user` and `admin` accounts before starting the app.

**Windows PowerShell**

```powershell
$env:BOOKING_USER_PASSWORD = '<choose-a-user-password>'
$env:BOOKING_ADMIN_PASSWORD = '<choose-an-admin-password>'
.\mvnw.cmd spring-boot:run
```

**macOS or Linux**

```sh
export BOOKING_USER_PASSWORD='<choose-a-user-password>'
export BOOKING_ADMIN_PASSWORD='<choose-an-admin-password>'
sh ./mvnw spring-boot:run
```

The API listens at `http://localhost:8080`. Leave the startup terminal open while sending requests. Press **Ctrl+C** to stop the app. H2 stores its files under `./data/` using the database name `bookingdb`. Bookings and slot statuses survive a restart when the app uses the same database.

## API request flow

Send HTTP Basic credentials on every request. The `user` account has the `USER` role, and the `admin` account has the `ADMIN` role.

| Method and path | Who can use it | Request and result |
| --- | --- | --- |
| `POST /slots` | `ADMIN` | Send `startTime` and `endTime` as ISO 8601 timestamps. Returns `201 Created` with the new `AVAILABLE` slot and its ID. |
| `GET /slots` | `USER` or `ADMIN` | Returns `200 OK` and lists both `AVAILABLE` and `BOOKED` slots, ordered by start time and ID. |
| `POST /bookings` | `USER` | Send `{"slotId": 1}` using a slot ID from the list. Returns `201 Created` with the booking ID and status `ACTIVE`. |
| `POST /bookings/{id}/cancel` | `USER` who owns the booking | Use the booking ID in the path. Returns `204 No Content`. |
| `POST /admin/bookings/{id}/cancel` | `ADMIN` | Use the booking ID in the path for any booking. Returns `204 No Content`. |

### Example from PowerShell

In a second terminal, set `BOOKING_USER_PASSWORD` and `BOOKING_ADMIN_PASSWORD` to the same values used to start the app. The example creates a slot as admin, books it as user, and cancels it as the same user.

```powershell
function Get-BookingHeaders($username, $password) {
    $credentials = '{0}:{1}' -f $username, $password
    $token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($credentials))
    return @{ Authorization = "Basic $token" }
}

$baseUrl = 'http://localhost:8080'
$adminHeaders = Get-BookingHeaders 'admin' $env:BOOKING_ADMIN_PASSWORD
$userHeaders = Get-BookingHeaders 'user' $env:BOOKING_USER_PASSWORD

$slotBody = @{
    startTime = '2030-01-01T10:00:00Z'
    endTime = '2030-01-01T11:00:00Z'
} | ConvertTo-Json
$slot = Invoke-RestMethod -Method Post -Uri "$baseUrl/slots" `
    -Headers $adminHeaders -ContentType 'application/json' -Body $slotBody
Invoke-RestMethod -Method Get -Uri "$baseUrl/slots" -Headers $userHeaders

$bookingBody = @{ slotId = $slot.id } | ConvertTo-Json
$booking = Invoke-RestMethod -Method Post -Uri "$baseUrl/bookings" `
    -Headers $userHeaders -ContentType 'application/json' -Body $bookingBody
Invoke-RestMethod -Method Post -Uri "$baseUrl/bookings/$($booking.id)/cancel" -Headers $userHeaders
```

Missing or invalid credentials return `401 Unauthorized`. A role or booking ownership violation returns `403 Forbidden`. An unknown slot or booking returns `404 Not Found`.

## Locking and transaction boundaries

The project uses **pessimistic write locks in H2**. `SlotRepository.findByIdForUpdate` takes a database row lock using JPA's `PESSIMISTIC_WRITE`. The lock lasts until the transaction commits or rolls back.

- **Booking.** `BookingService.book` runs in one `@Transactional` method. It locks the slot before reading its status. If the slot is `AVAILABLE`, the method marks it `BOOKED` and inserts an `ACTIVE` booking. Both writes commit or roll back together.
- **Cancellation.** `BookingService.cancel` locks the booking row, checks who can cancel it, and locks its slot before updating either record. The booking becomes `CANCELLED` and the slot becomes `AVAILABLE` in the same transaction. Repeating a cancellation leaves the current slot state intact.

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
- Slots represent independent entries created by an admin. A slot's start time must precede its end time. The system allows separate slots to overlap.
- The service derives the booking's `userId` from the authenticated account. The H2 foreign key `bookings.slot_id` keeps each booking linked to its slot.
- The default local accounts use passwords from environment variables. Each account is enabled when its password is set.
