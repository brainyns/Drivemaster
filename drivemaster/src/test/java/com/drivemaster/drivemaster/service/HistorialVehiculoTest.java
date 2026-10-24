package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.OrdenServicioDTO;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.Diagnostico;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Historial del vehículo: las órdenes terminadas de los dos flujos, de la más reciente a la más antigua. */
class HistorialVehiculoTest {

    private OrdenServicioRepository ordenes;
    private CotizacionRepository cotizaciones;
    private VehiculoService vehiculos;
    private OrdenServicioServiceImpl servicio;

    @BeforeEach
    void setUp() {
        ordenes = mock(OrdenServicioRepository.class);
        cotizaciones = mock(CotizacionRepository.class);
        vehiculos = mock(VehiculoService.class);
        servicio = new OrdenServicioServiceImpl(ordenes, vehiculos, mock(TallerService.class), mock(ProductoService.class),
                mock(UsuarioService.class), mock(VentaService.class), mock(MantenimientoService.class), cotizaciones);
    }

    @Test
    void incluyeLasEntregadasDelMarketplaceConSusRepuestosYTotal() {
        OrdenServicio v1 = OrdenServicio.builder().id("v1").vehiculoId("car").estado("FINALIZADA")
                .tallerNombre("Taller Viejo").productoNombre("Filtro de aceite").total(90_000.0)
                .fechaFinalizacion(LocalDateTime.of(2026, 3, 1, 10, 0)).build();
        Diagnostico diagnostico = new Diagnostico();
        diagnostico.setFallas(List.of("Pastillas gastadas", "Disco rayado"));
        OrdenServicio mk = OrdenServicio.builder().id("mk").vehiculoId("car").flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .estado(EstadoOrden.ENTREGADA).tallerNombre("Taller Nuevo").cotizacionId("c1").diagnostico(diagnostico)
                .fechaEntrega(LocalDateTime.of(2026, 10, 20, 17, 0)).build();
        // Un ENTREGADA del flujo anterior o un FINALIZADA del marketplace no son estados finales de su flujo
        OrdenServicio rara = OrdenServicio.builder().id("x").vehiculoId("car").flujo(EstadoOrden.FLUJO_MARKETPLACE)
                .estado("FINALIZADA").build();
        when(ordenes.findByVehiculoIdAndEstadoIn(eq("car"), any())).thenReturn(List.of(v1, mk, rara));

        CotizacionItem repuesto = new CotizacionItem();
        repuesto.setTipo(CotizacionItem.REPUESTO);
        repuesto.setCantidad(2);
        repuesto.setProductoNombre("Pastillas Brembo");
        CotizacionItem manoObra = new CotizacionItem();
        manoObra.setTipo(CotizacionItem.MANO_OBRA);
        manoObra.setDescripcion("Cambio de pastillas");
        Cotizacion c1 = Cotizacion.builder().id("c1").total(350_000.0).subtotalRepuestos(200_000.0)
                .subtotalManoObra(150_000.0).items(List.of(repuesto, manoObra)).build();
        when(cotizaciones.findAllById(anyList())).thenReturn(List.of(c1));

        List<OrdenServicioDTO> historial = servicio.historialPorVehiculo("cliente@demo.com", "car");

        assertEquals(List.of("mk", "v1"), historial.stream().map(OrdenServicioDTO::getId).toList(), "la más reciente primero");
        OrdenServicioDTO nuevo = historial.get(0);
        assertEquals("Taller Nuevo", nuevo.getTallerNombre());
        assertEquals("Pastillas gastadas, Disco rayado", nuevo.getProductoNombre());
        assertEquals(LocalDateTime.of(2026, 10, 20, 17, 0), nuevo.getFechaFinalizacion());
        assertEquals(350_000.0, nuevo.getTotal());
        assertEquals(List.of("2 × Pastillas Brembo"), nuevo.getRepuestos(), "solo los repuestos, no la mano de obra");
        assertEquals(EstadoOrden.FLUJO_MARKETPLACE, nuevo.getFlujo());

        OrdenServicioDTO viejo = historial.get(1);
        assertEquals("Filtro de aceite", viejo.getProductoNombre());
        assertEquals(90_000.0, viejo.getTotal());
        assertNull(viejo.getRepuestos());
    }

    @Test
    void validaQueElVehiculoSeaDelUsuario() {
        when(vehiculos.obtenerDeUsuario("otro@demo.com", "car"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Este vehículo no le pertenece"));
        assertThrows(ResponseStatusException.class, () -> servicio.historialPorVehiculo("otro@demo.com", "car"));
        verify(ordenes, never()).findByVehiculoIdAndEstadoIn(any(), any());
    }
}
