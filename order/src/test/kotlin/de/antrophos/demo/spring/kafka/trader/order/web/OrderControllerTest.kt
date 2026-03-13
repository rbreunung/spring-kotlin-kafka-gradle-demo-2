package de.antrophos.demo.spring.kafka.trader.order.web

import com.fasterxml.jackson.databind.ObjectMapper
import de.antrophos.demo.spring.kafka.trader.order.dto.OrderResponse
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotFoundException
import de.antrophos.demo.spring.kafka.trader.order.exception.OrderNotCancellableException
import de.antrophos.demo.spring.kafka.trader.order.service.OrderCommandService
import de.antrophos.demo.spring.kafka.trader.order.service.OrderQueryService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

/** Kotlin-safe wrapper: returns a non-null stand-in accepted by Mockito matchers. */
@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() as T

@WebMvcTest(controllers = [OrderController::class])
class OrderControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    @MockitoBean lateinit var commandService: OrderCommandService
    @MockitoBean lateinit var queryService: OrderQueryService

    private fun aResponse(id: UUID = UUID.randomUUID(), status: String = "PENDING") = OrderResponse(
        id = id,
        traderId = "T1",
        symbol = "AAPL",
        quantity = 10,
        side = "BUY",
        status = status,
        tradeId = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `POST orders returns 201 with order`() {
        val response = aResponse()
        `when`(commandService.place(anyNonNull())).thenReturn(response)

        mockMvc.perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"traderId":"T1","symbol":"AAPL","quantity":10,"side":"BUY"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.traderId").value("T1"))
    }

    @Test
    fun `POST orders with quantity 0 returns 400`() {
        mockMvc.perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"traderId":"T1","symbol":"AAPL","quantity":0,"side":"BUY"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("quantity"))
    }

    @Test
    fun `POST orders with blank traderId returns 400`() {
        mockMvc.perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"traderId":"","symbol":"AAPL","quantity":10,"side":"BUY"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("traderId"))
    }

    @Test
    fun `POST orders with invalid side returns 400`() {
        mockMvc.perform(
            post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"traderId":"T1","symbol":"AAPL","quantity":10,"side":"INVALID"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].field").value("side"))
    }

    @Test
    fun `GET orders by id returns 200`() {
        val id = UUID.randomUUID()
        `when`(queryService.findById(id)).thenReturn(aResponse(id = id))

        mockMvc.perform(get("/orders/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
    }

    @Test
    fun `GET orders by id returns 404 for unknown id`() {
        val id = UUID.randomUUID()
        `when`(queryService.findById(id)).thenThrow(OrderNotFoundException(id))

        mockMvc.perform(get("/orders/$id"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET orders returns list`() {
        `when`(queryService.findAll(null, null)).thenReturn(listOf(aResponse(), aResponse()))

        mockMvc.perform(get("/orders"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET orders with traderId param filters list`() {
        `when`(queryService.findAll("T1", null)).thenReturn(listOf(aResponse()))

        mockMvc.perform(get("/orders?traderId=T1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
        verify(queryService).findAll("T1", null)
    }

    @Test
    fun `GET orders with status param filters list`() {
        `when`(queryService.findAll(null, "PENDING")).thenReturn(listOf(aResponse()))

        mockMvc.perform(get("/orders?status=PENDING"))
            .andExpect(status().isOk)
        verify(queryService).findAll(null, "PENDING")
    }

    @Test
    fun `DELETE orders by id returns 204`() {
        val id = UUID.randomUUID()
        doNothing().`when`(commandService).cancel(id)

        mockMvc.perform(delete("/orders/$id"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE orders by id returns 404 for unknown id`() {
        val id = UUID.randomUUID()
        doThrow(OrderNotFoundException(id)).`when`(commandService).cancel(id)

        mockMvc.perform(delete("/orders/$id"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE orders by id returns 409 for non-PENDING order`() {
        val id = UUID.randomUUID()
        doThrow(OrderNotCancellableException(id, "EXECUTED")).`when`(commandService).cancel(id)

        mockMvc.perform(delete("/orders/$id"))
            .andExpect(status().isConflict)
    }
}
