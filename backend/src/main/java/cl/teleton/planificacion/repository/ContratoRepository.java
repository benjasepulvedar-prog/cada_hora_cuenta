package cl.teleton.planificacion.repository;

import cl.teleton.planificacion.model.ContratoRow;
import cl.teleton.planificacion.model.FilaBuk;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ContratoRepository {

    private final JdbcTemplate jdbc;

    public ContratoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<ContratoRow> MAPPER = (rs, i) -> {
        Date term = rs.getDate("fecha_termino");
        return new ContratoRow(rs.getInt("persona_id"), rs.getInt("it"), rs.getDate("periodo").toLocalDate(),
                rs.getString("instituto"), rs.getString("sub_area"), rs.getString("cod_esp"),
                rs.getString("tipo_contrato"), rs.getString("tipo_trabajador"),
                term == null ? null : term.toLocalDate(), rs.getDouble("horas_semanales"),
                rs.getString("cargo"), rs.getBoolean("vigente"));
    };

    public Map<String, ContratoRow> todos() {
        Map<String, ContratoRow> contratos = new HashMap<>();
        for (ContratoRow contrato : jdbc.query("SELECT * FROM contratos", MAPPER)) contratos.put(contrato.llave(), contrato);
        return contratos;
    }

    public List<ContratoRow> vigentes() {
        return jdbc.query("SELECT * FROM contratos WHERE vigente = TRUE ORDER BY persona_id, it, periodo", MAPPER);
    }

    public List<ContratoRow> vigentesDePersona(int personaId, Integer it) {
        if (it == null) {
            return jdbc.query("SELECT * FROM contratos WHERE vigente = TRUE AND persona_id = ? ORDER BY it, periodo",
                    MAPPER, personaId);
        }
        return jdbc.query("SELECT * FROM contratos WHERE vigente = TRUE AND persona_id = ? AND it = ? ORDER BY periodo",
                MAPPER, personaId, it);
    }

    public void upsert(List<FilaBuk> filas, String archivo) {
        List<Object[]> lote = new ArrayList<>();
        for (FilaBuk f : filas) {
            lote.add(new Object[]{f.personaId(), f.it(), Date.valueOf(f.periodo()), f.instituto(), f.ciudad(),
                    f.subArea(), f.codEsp(), f.tipoContrato(), f.tipoTrabajador(),
                    f.fechaTermino() == null ? null : Date.valueOf(f.fechaTermino()),
                    f.horas(), f.cargo(), f.regSic(), archivo, Boolean.TRUE});
        }
        int[] tipos = {Types.INTEGER, Types.INTEGER, Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.DATE, Types.DOUBLE, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN};
        jdbc.batchUpdate("MERGE INTO contratos (persona_id,it,periodo,instituto,ciudad,sub_area,cod_esp,tipo_contrato,"
                + "tipo_trabajador,fecha_termino,horas_semanales,cargo,reg_sic,archivo_origen,vigente) "
                + "KEY (persona_id,it,periodo) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", lote, tipos);
    }

    public void marcarNoVigente(int personaId, int it, LocalDate desde) {
        jdbc.update("UPDATE contratos SET vigente = FALSE WHERE persona_id = ? AND it = ? AND periodo >= ?",
                personaId, it, Date.valueOf(desde));
    }
}