package com.web.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

@org.junit.jupiter.api.Disabled("Requires full Mongo/Redis/Kafka services to be running")
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@org.junit.jupiter.api.Disabled("Requires full Mongo/Redis/Kafka services to be running")
	@Test
	void contextLoads() {
	}

}
