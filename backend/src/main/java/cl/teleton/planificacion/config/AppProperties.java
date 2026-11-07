package cl.teleton.planificacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bot")
public record AppProperties(String modo, String carpetaEntrada, String automateExe, String proceso) {}
