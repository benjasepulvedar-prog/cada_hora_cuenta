package cl.teleton.planificacion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDialect {

    private final boolean postgres;

    public DatabaseDialect(@Value("${app.database-type:h2}") String databaseType) {
        this.postgres = "postgresql".equalsIgnoreCase(databaseType) || "postgres".equalsIgnoreCase(databaseType);
    }

    public boolean isPostgres() { return postgres; }

    public String month(String dateColumn) {
        return postgres ? "EXTRACT(MONTH FROM " + dateColumn + ")" : "MONTH(" + dateColumn + ")";
    }
}