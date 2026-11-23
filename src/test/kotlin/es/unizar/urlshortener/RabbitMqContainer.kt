package es.unizar.urlshortener

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.rabbitmq.RabbitMQContainer
import java.net.ServerSocket

// esta clase contiene la configuracion del rabbitmq que se va a usar para los tests
@TestConfiguration(proxyBeanMethods = false)
class RabbitMqContainer {
    @Bean
    @ServiceConnection
    fun rabbitmq(): RabbitMQContainer {
        val puerto = ServerSocket(0).use { it.localPort } // pide al SO un puerto libre y lo almacena en la variable puerto,
        // sera el puerto que va a utilizar Rambbitmq, para que al parar y arrancar no cambie el puerto
        return RabbitMQContainer("rabbitmq:3.13-management-alpine").apply {
            portBindings = listOf("$puerto:5672")
        } // lo estandar es que rabbitmq utilice el puerto 5672, por tanto, se asocia el puerto libre, que se ha obtenido, con
        // este puerto de Rabbitmq para redirigir los paquetes
    }
}
