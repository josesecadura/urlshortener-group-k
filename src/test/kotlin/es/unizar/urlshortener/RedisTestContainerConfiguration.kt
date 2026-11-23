package es.unizar.urlshortener

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class RedisTestContainerConfiguration {
    @Bean
    @ServiceConnection(name = "redis")
    fun redis(): RedisTestContainer = RedisTestContainer().withExposedPorts(6379)
}

class RedisTestContainer :
    GenericContainer<RedisTestContainer>(
        DockerImageName.parse("redis:7-alpine"),
    )
