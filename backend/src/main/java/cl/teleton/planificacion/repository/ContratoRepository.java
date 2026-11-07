package cl.teleton.planificacion.repository;

import cl.teleton.planificacion.model.ContratoRow;
import cl.teleton.planificacion.model.FilaBuk;
import cl.teleton.planificacion.config.DatabaseDialect;
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
    private final DatabaseDialect dialect;

    public ContratoRepository(JdbcTemplate jdbc, DatabaseDialect dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

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
        String sql;
        if (dialect.isPostgres()) {
            sql = "INSERT INTO contratos (persona_id,it,periodo,instituto,ciudad,sub_area,cod_esp,tipo_contrato,"
                + "tipo_trabajador,fecha_termino,horas_semanales,cargo,reg_sic,archivo_origen,vigente) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (persona_id,it,periodo) DO UPDATE SET "
                + "instituto=EXCLUDED.instituto, ciudad=EXCLUDED.ciudad, sub_area=EXCLUDED.sub_area, "
                + "cod_esp=EXCLUDED.cod_esp, tipo_contrato=EXCLUDED.tipo_contrato, "
                + "tipo_trabajador=EXCLUDED.tipo_trabajador, fecha_termino=EXCLUDED.fecha_termino, "
                + "horas_semanales=EXCLUDED.horas_semanales, cargo=EXCLUDED.cargo, reg_sic=EXCLUDED.reg_sic, "
                + "archivo_origen=EXCLUDED.archivo_origen, vigente=EXCLUDED.vigente";
        } else {
            sql = "MERGE INTO contratos (persona_id,it,periodo,instituto,ciudad,sub_area,cod_esp,tipo_contrato,"
                + "tipo_trabajador,fecha_termino,horas_semanales,cargo,reg_sic,archivo_origen,vigente) "
                + "KEY (persona_id,it,periodo) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        }
        jdbc.batchUpdate(sql, lote, tipos);
    }

    public void marcarNoVigente(int personaId, int it, LocalDate desde) {
        jdbc.update("UPDATE contratos SET vigente = FALSE WHERE persona_id = ? AND it = ? AND periodo >= ?",
                personaId, it, Date.valueOf(desde));
    }
}