package de.antrophos.demo.spring.kafka.trader.saga.web

import de.antrophos.demo.spring.kafka.trader.saga.repository.SagaStateRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/sagas")
class SagaController(private val repository: SagaStateRepository) {

    @GetMapping
    fun listSagas(): List<SagaStateResponse> =
        repository.findAllByOrderByUpdatedAtDesc().map { SagaStateResponse.from(it) }

    @GetMapping("/{orderId}")
    fun getSaga(@PathVariable orderId: UUID): ResponseEntity<SagaStateResponse> {
        val entity = repository.findById(orderId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SagaStateResponse.from(entity))
    }
}
