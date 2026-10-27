package com.drivemaster.drivemaster.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.service.TablasTiendaService;

/**
 * Las tablas paginadas del panel de la tienda (solo TIENDA, cada una con lo suyo). Son endpoints aparte: las listas
 * completas de siempre (/api/productos/mi-tienda, /api/pedidos-tienda/mios y /api/movimientos) siguen igual porque
 * también las usan los selectores y formularios.
 *
 * Todas aceptan page (desde 0), size (20 por defecto, máximo 100) y sort ("campo,asc" o "campo,desc", con lista
 * blanca en TablasTiendaService).
 */
@RestController
public class TablasTiendaController {

    private final TablasTiendaService tablas;
    private final AlcanceTienda alcanceTienda;

    public TablasTiendaController(TablasTiendaService tablas, AlcanceTienda alcanceTienda) {
        this.tablas = tablas;
        this.alcanceTienda = alcanceTienda;
    }

    /** Inventario. filtro: TODOS, POCO, AGOTADOS, PUBLICADOS u OCULTOS; q: nombre, código, categoría, marca o referencia. */
    @GetMapping("/api/productos/mi-tienda/tabla")
    public TablasTiendaDTO.Inventario inventario(
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication auth) {
        return tablas.inventario(alcanceTienda.tiendaId(auth), filtro, q,
                TablasTiendaService.peticionInventario(page, size, sort));
    }

    /** Pedidos. pestana: ATENDER, PREPARACION, LISTOS, EN_CAMINO, NUEVOS, ENTREGADOS, CANCELADOS o TODOS; desde/hasta: fecha de pago. */
    @GetMapping("/api/pedidos-tienda/mios/tabla")
    public TablasTiendaDTO.Pedidos pedidos(
            @RequestParam(required = false) String pestana,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication auth) {
        return tablas.pedidos(alcanceTienda.tiendaId(auth), pestana, q, RangoFechas.de(desde, hasta),
                TablasTiendaService.peticionPedidos(page, size, sort));
    }

    /**
     * Movimientos. tipo: ENTRADA, SALIDA o AJUSTE; q: nombre o código del producto; desde/hasta (AAAA-MM-DD).
     * fechaInicio/fechaFin son los nombres de /api/movimientos: se aceptan como alias hasta después de la feria (README).
     */
    @GetMapping("/api/movimientos/tabla")
    public TablasTiendaDTO.Movimientos movimientos(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(required = false) String fechaFin,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication auth) {
        RangoFechas rango = RangoFechas.de(desde != null ? desde : fechaInicio, hasta != null ? hasta : fechaFin);
        return tablas.movimientos(alcanceTienda.tiendaId(auth), tipo, q, rango,
                TablasTiendaService.peticionMovimientos(page, size, sort));
    }
}
