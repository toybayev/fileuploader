package kz.lab.fileuploaderservice.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {
    @Bean
    public ApplicationRunner migrateDatabase() {
        return args -> {
            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:postgresql://localhost:5433/filedb", "postgres", "postgres")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
        };
    }
}
