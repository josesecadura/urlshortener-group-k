package es.unizar.urlshortener

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class RabbitMqContainer {
    @Bean
    @ServiceConnection
    fun rabbitmq() = RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
}
