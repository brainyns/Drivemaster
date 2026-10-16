package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Despacho;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoVendido;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.Reponer;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.SinMovimiento;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.VentasSemana;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumenSemanalTiendaTest {

    private static VentasSemana semana(double ventas, double anterior) {
        LocalDate lunes = LocalDate.of(2026, 10, 5);
        return new VentasSemana(lunes, lunes.plusDays(6), ventas, 12, anterior, 10,
                List.of(new ProductoVendido("1", "Filtro <b>aceite</b>", 18, 486_000)));
    }

    @Test
    void laSemanaEsLaUltimaCompletaDeLunesADomingo() {
        assertEquals(LocalDate.of(2026, 10, 5), ResumenSemanalTienda.lunesSemanaPasada(LocalDate.of(2026, 10, 12)));  // lunes
        assertEquals(LocalDate.of(2026, 10, 5), ResumenSemanalTienda.lunesSemanaPasada(LocalDate.of(2026, 10, 16)));  // viernes
        assertEquals("5 al 11 de octubre", ResumenSemanalTienda.rango(semana(1, 1)));
    }

    @Test
    void elTitularDeLaIaSoloSeUsaSiEsUnaFraseCorta() {
        assertEquals("Buena semana: subieron las ventas", ResumenSemanalTienda.titularValido("\"Buena semana: subieron las ventas\"", semana(2, 1), 0));
        assertEquals("Vendiste más que la semana pasada, pero 3 productos están por agotarse",
                ResumenSemanalTienda.titularValido("línea 1\nlínea 2", semana(2, 1), 3));
        assertEquals("Vendiste menos que la semana pasada y 1 producto está por agotarse",
                ResumenSemanalTienda.titularValido(null, semana(1, 2), 1));
        assertEquals("Tu semana en DriveMaster", ResumenSemanalTienda.titularValido("x".repeat(200), semana(1, 1), 0));
    }

    @Test
    void variacionContraLaSemanaAnterior() {
        assertEquals("+12 %", ResumenSemanalTienda.variacionPct(112, 100));
        assertEquals("-50 %", ResumenSemanalTienda.variacionPct(50, 100));
        assertEquals("sin ventas la semana anterior", ResumenSemanalTienda.variacionPct(10, 0));
    }

    @Test
    void elCuerpoEscapaLosNombresYMuestraLasSecciones() {
        ResumenSemanalTienda r = new ResumenSemanalTienda(null, null, null, null, null, "https://ejemplo.com");
        Tienda t = new Tienda();
        t.setNombre("El <Pistón>");
        String html = r.cuerpo(t, semana(3_480_000, 3_100_000), new Despacho(0, null, null, 2, 1),
                List.of(new Reponer("Pastillas", "P1", 1, 5, 9, 7)),
                List.of(new SinMovimiento("Amortiguador", "A1", 6, 900_000.0, null)));
        assertFalse(html.contains("<Pistón>") || html.contains("<b>aceite"));
        assertTrue(html.contains("El &lt;Pist"));  // htmlEscape también convierte la tilde
        assertTrue(html.contains("Sugerimos pedir 7 unidades"));
        assertTrue(html.contains("Dinero quieto") && html.contains("1 atrasado") && html.contains("https://ejemplo.com"));
    }
}
