package cl.teleton.planificacion;

import cl.teleton.planificacion.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class PlanificacionApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlanificacionApplication.class, args);
    }
}
