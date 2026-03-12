package de.antrophos.demo.spring.kafka.trader.order.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class FieldError(val field: String, val message: String)
data class ErrorResponse(val errors: List<FieldError>)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map {
            FieldError(it.field, it.defaultMessage ?: "invalid")
        }
        return ResponseEntity.badRequest().body(ErrorResponse(errors))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val error = FieldError("side", "Invalid value. Accepted values: BUY, SELL")
        return ResponseEntity.badRequest().body(ErrorResponse(listOf(error)))
    }

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleNotFound(ex: OrderNotFoundException): ResponseEntity<Void> =
        ResponseEntity.notFound().build()

    @ExceptionHandler(OrderNotCancellableException::class)
    fun handleNotCancellable(ex: OrderNotCancellableException): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.CONFLICT).build()
}
