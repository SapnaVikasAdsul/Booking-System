package com.example.booking_system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking-context-tests")
class BookingSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
