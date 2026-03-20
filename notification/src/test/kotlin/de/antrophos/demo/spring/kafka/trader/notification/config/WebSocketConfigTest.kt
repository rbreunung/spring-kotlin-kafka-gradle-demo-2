package de.antrophos.demo.spring.kafka.trader.notification.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConfigTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `SockJS info endpoint is reachable`() {
        val response = restTemplate.getForEntity("http://localhost:$port/ws/info", String::class.java)
        assertTrue(
            response.statusCode == HttpStatus.OK,
            "Expected SockJS /ws/info to return 200 but got ${response.statusCode}. " +
                "Check that WebSocketConfig registers the endpoint with withSockJS()."
        )
    }
}
