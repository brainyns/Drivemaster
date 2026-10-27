package com.drivemaster.drivemaster.dto.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Formato común de las tablas paginadas: page/size, sort con lista blanca y desde/hasta en hora de Colombia. */
class PeticionTablaTest {

    private static final PeticionTabla.OrdenPermitido ORDEN = PeticionTabla.OrdenPermitido.de(
            Sort.by(Sort.Direction.DESC, "fechaCreacion"), "fecha", "fechaCreacion", "nombre", "nombre", "total", "total");

    // ─── Paginación ─────────────────────────────────────────────────────────

    @Test
    void sinParametrosEsLaPrimeraPaginaDe20ConElOrdenPorDefecto() {
        PeticionTabla t = PeticionTabla.de(null, null, null, null, null, ORDEN);

        assertEquals(0, t.pagina());
        assertEquals(20, t.tamano());
        assertEquals(Sort.by(Sort.Direction.DESC, "fechaCreacion"), t.orden());
        assertNull(t.campoOrden());
    }

    @Test
    void elTamanoTieneTopeDe100YUnoInvalidoVuelveA20() {
        assertEquals(100, PeticionTabla.de(0, 500, null, null, null, ORDEN).tamano());
        assertEquals(20, PeticionTabla.de(0, 0, null, null, null, ORDEN).tamano());
        assertEquals(20, PeticionTabla.de(0, -3, null, null, null, ORDEN).tamano());
        assertEquals(0, PeticionTabla.de(-2, 10, null, null, null, ORDEN).pagina());
    }

    @Test
    void losAliasViejosPaginaYTamanoSiguenFuncionando_yGanaElNombreNuevo() {
        PeticionTabla viejo = PeticionTabla.de(null, null, 3, 50, null, ORDEN);
        assertEquals(3, viejo.pagina());
        assertEquals(50, viejo.tamano());

        PeticionTabla ambos = PeticionTabla.de(1, 10, 3, 50, null, ORDEN);
        assertEquals(1, ambos.pagina());
        assertEquals(10, ambos.tamano());
    }

    @Test
    void elTotalDePaginasYUnaPaginaMasAllaDeLaUltima() {
        assertEquals(1, Pagina.totalPaginas(0, 20));
        assertEquals(1, Pagina.totalPaginas(20, 20));
        assertEquals(2, Pagina.totalPaginas(21, 20));
        assertEquals(6, Pagina.totalPaginas(103, 20));

        PeticionTabla lejos = PeticionTabla.de(9, 20, null, null, null, ORDEN);
        assertEquals(5, lejos.paginaPara(103), "page 9 de 6 páginas devuelve la última (5, desde 0)");
        Pagina<String> p = lejos.pagina(List.of("a"), 103);
        assertEquals(5, p.pagina());
        assertEquals(6, p.totalPaginas());
        assertEquals(103, p.total());
    }

    @Test
    void paginarEnMemoriaDevuelveLaPaginaPedidaConElTotalDeTodos() {
        List<Integer> todos = IntStream.rangeClosed(1, 45).boxed().toList();

        Pagina<Integer> segunda = PeticionTabla.de(1, 20, null, null, null, ORDEN).paginar(todos);
        assertEquals(IntStream.rangeClosed(21, 40).boxed().toList(), segunda.items());
        assertEquals(45, segunda.total());
        assertEquals(3, segunda.totalPaginas());

        Pagina<Integer> ultima = PeticionTabla.de(2, 20, null, null, null, ORDEN).paginar(todos);
        assertEquals(List.of(41, 42, 43, 44, 45), ultima.items());

        Pagina<Integer> vacia = PeticionTabla.de(0, 20, null, null, null, ORDEN).paginar(List.of());
        assertTrue(vacia.items().isEmpty());
        assertEquals(1, vacia.totalPaginas());
    }

    // ─── Orden ──────────────────────────────────────────────────────────────

    @Test
    void elOrdenUsaElCampoGuardadoDeLaListaBlancaYDesempataPorId() {
        PeticionTabla t = PeticionTabla.de(0, 20, null, null, "fecha,asc", ORDEN);

        assertEquals("fecha", t.campoOrden());
        assertTrue(t.ascendente());
        assertEquals(Sort.by(Sort.Direction.ASC, "fechaCreacion").and(Sort.by(Sort.Direction.DESC, "_id")), t.orden());

        PeticionTabla desc = PeticionTabla.de(0, 20, null, null, " total , DESC ", ORDEN);
        assertFalse(desc.ascendente());
        assertEquals(Sort.Direction.DESC, desc.orden().getOrderFor("total").getDirection());
        // Sin dirección, ascendente
        assertTrue(PeticionTabla.de(0, 20, null, null, "nombre", ORDEN).ascendente());
    }

    @Test
    void unCampoQueNoEstaEnLaListaBlancaResponde400() {
        for (String sort : new String[]{"password,asc", "correo", "fechaCreacion,desc", "$where,asc"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> PeticionTabla.de(0, 20, null, null, sort, ORDEN), sort);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertTrue(e.getReason().startsWith("No se puede ordenar por"), e.getReason());
        }
    }

    @Test
    void unaDireccionInventadaResponde400() {
        for (String sort : new String[]{"fecha,arriba", "fecha,asc,desc"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> PeticionTabla.de(0, 20, null, null, sort, ORDEN), sort);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    void enJpaElDesempateEsPorId() {
        PeticionTabla t = PeticionTabla.de(0, 20, null, null, "fecha,desc", ORDEN.conDesempate("id"));
        assertEquals(Sort.Direction.DESC, t.orden().getOrderFor("id").getDirection());
        assertNull(t.orden().getOrderFor("_id"));
    }

    // ─── Rango de fechas ────────────────────────────────────────────────────

    @Test
    void hastaIncluyeTodoEseDiaEnHoraDeColombia() {
        RangoFechas r = RangoFechas.de("2026-10-01", "2026-10-07");

        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), r.desde());
        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0), r.antesDe());
        assertTrue(r.contiene(LocalDateTime.of(2026, 10, 1, 0, 0)));
        assertTrue(r.contiene(LocalDateTime.of(2026, 10, 7, 23, 59, 59)));
        assertFalse(r.contiene(LocalDateTime.of(2026, 10, 8, 0, 0)));
        assertFalse(r.contiene(LocalDateTime.of(2026, 9, 30, 23, 59)));
        assertFalse(r.contiene(null));
    }

    @Test
    void unSoloLimiteYSinRango() {
        RangoFechas soloDesde = RangoFechas.de("2026-10-20", null);
        assertTrue(soloDesde.contiene(LocalDateTime.of(2030, 1, 1, 0, 0)));
        assertFalse(soloDesde.contiene(LocalDateTime.of(2026, 10, 19, 23, 0)));

        RangoFechas sin = RangoFechas.de(" ", null);
        assertTrue(sin.vacio());
        assertTrue(sin.contiene(null));
        assertNull(sin.criterio("fecha"));
    }

    @Test
    void elCriterioDeMongoUsaElMismoRango() {
        String c = RangoFechas.de("2026-10-01", "2026-10-07").criterio("fechaCreacion").getCriteriaObject().toString();
        assertTrue(c.contains("fechaCreacion") && c.contains("$gte") && c.contains("$lt") && c.contains("2026-10-08T00:00"), c);
    }

    @Test
    void desdePosteriorAHastaOUnaFechaMalEscritaResponden400() {
        ResponseStatusException invertido = assertThrows(ResponseStatusException.class, () -> RangoFechas.de("2026-10-08", "2026-10-01"));
        assertEquals(HttpStatus.BAD_REQUEST, invertido.getStatusCode());
        assertEquals("La fecha «desde» no puede ser posterior a «hasta».", invertido.getReason());

        ResponseStatusException mal = assertThrows(ResponseStatusException.class, () -> RangoFechas.de("01/10/2026", null));
        assertEquals("La fecha «desde» va como AAAA-MM-DD.", mal.getReason());
    }

    @Test
    void aceptaUnaFechaConHora_comoLaMandabanLosAliasDeMovimientos() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), RangoFechas.de("2026-10-01T15:30:00", null).desde());
    }
}
