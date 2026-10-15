package es.unizar.urlshortener
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

// fichero de configuracion de shedlock (para asegurar 1 ejecucion de las scheduled tasks)

@Configuration
class SchedulerConfiguration {
    @Bean
    fun lockProvider(jdbcTemplate: JdbcTemplate): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(jdbcTemplate)
                .withTableName("shedlock")
                .usingDbTime()
                .build(),
        )
}
