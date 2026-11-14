package es.unizar.urlshortener.clicks.adapters.messageBroker

import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

@Profile("broker")
@Configuration
class BrokerConfiguration {
    private var colaName: String = "clicks.ingest.queue"
    private var topicExchangeName: String = "clicks.ingest"

    @Bean
    fun queue(): Queue {
        return Queue(colaName, true) // durable=true para que si se se reinicia, se mantenga la cola
    }

    @Bean
    fun exchange(): TopicExchange {
        // cuando quiera añadir un evento al broker lo voy a hacer a traves del exhange
        return TopicExchange(topicExchangeName, true, false)
    }

    @Bean
    fun binding(
        queue: Queue,
        exchange: TopicExchange,
    ): Binding {
        // al ser @Bean, spring busca los parametros entre los creados con @bean,
        // por eso va a asociar la cola y el exchange creados previamente
        // relaciona el TopicExchange (por donde se van a mandar los mensajes) con la cola desde la que se van a consumir
        // se pone en routing key # para que todos los mensajes que llegan al exchange se redirijan a la cola (sin comprobar routing key)

        return BindingBuilder.bind(queue).to(exchange).with("#")
    }
    // conversor para que los mensajes que se envian al broker se envien y reciban como json, sino da error
    @Bean
    fun jsonMessageConverter(mapper: JsonMapper): MessageConverter =
        JacksonJsonMessageConverter(mapper).apply {
            typePrecedence = JacksonJavaTypeMapper.TypePrecedence.INFERRED
        }


}
