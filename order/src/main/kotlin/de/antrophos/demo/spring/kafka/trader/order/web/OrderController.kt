package de.antrophos.demo.spring.kafka.trader.order.web

import de.antrophos.demo.spring.kafka.trader.order.dto.OrderResponse
import de.antrophos.demo.spring.kafka.trader.order.dto.PlaceOrderRequest
import de.antrophos.demo.spring.kafka.trader.order.service.OrderCommandService
import de.antrophos.demo.spring.kafka.trader.order.service.OrderQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/orders")
class OrderController(
    private val commandService: OrderCommandService,
    private val queryService: OrderQueryService
) {

    @PostMapping
    fun placeOrder(@Valid @RequestBody request: PlaceOrderRequest): ResponseEntity<OrderResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(commandService.place(request))

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: UUID): OrderResponse =
        queryService.findById(id)

    @GetMapping
    fun listOrders(
        @RequestParam(required = false) traderId: String?,
        @RequestParam(required = false) status: String?
    ): List<OrderResponse> =
        queryService.findAll(traderId, status)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelOrder(@PathVariable id: UUID) =
        commandService.cancel(id)
}
