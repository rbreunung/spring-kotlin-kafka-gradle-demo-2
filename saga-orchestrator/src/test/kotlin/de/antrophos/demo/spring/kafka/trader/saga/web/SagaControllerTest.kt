package de.antrophos.demo.spring.kafka.trader.saga.web

import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStateEntity
import de.antrophos.demo.spring.kafka.trader.saga.domain.SagaStep
import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Optional
import java.util.UUID

@WebMvcTest(controllers = [SagaController::class])
class SagaControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var repository: SagaStateRepository

    private fun anEntity(
        orderId: UUID = UUID.randomUUID(),
        step: SagaStep = SagaStep.RISK_REQUESTED,
        tradeId: UUID? = null
    ) = SagaStateEntity(
        orderId = orderId,
        step = step.name,
        tradeId = tradeId,
        updatedAt = Instant.now(),
        orderJson = """{"id":"$orderId","traderId":"T1","symbol":"AAPL","quantity":100,"side":"BUY"}"""
    )

    @Test
    fun `GET sagas returns 200 with list of saga states`() {
        val entities = listOf(anEntity(), anEntity())
        `when`(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(entities)

        mockMvc.perform(get("/sagas"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET sagas by known orderId returns 200 with saga state`() {
        val orderId = UUID.randomUUID()
        val entity = anEntity(orderId = orderId, step = SagaStep.RISK_APPROVED)
        `when`(repository.findById(orderId)).thenReturn(Optional.of(entity))

        mockMvc.perform(get("/sagas/$orderId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.step").value(SagaStep.RISK_APPROVED.name))
    }

    @Test
    fun `GET sagas by unknown orderId returns 404`() {
        val unknownId = UUID.randomUUID()
        `when`(repository.findById(unknownId)).thenReturn(Optional.empty())

        mockMvc.perform(get("/sagas/$unknownId"))
            .andExpect(status().isNotFound)
    }
}
