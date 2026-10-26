package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La bandeja del taller trae placas y clientes con una consulta por colección, no una por orden. */
class BandejaSinConsultasPorOrdenTest {

    @Test
    void placasYClientesEnUnaSolaConsultaCadaUno() {
        OrdenServicioRepository ordenes = mock(OrdenServicioRepository.class);
        TallerService tallerService = mock(TallerService.class);
        VehiculoService vehiculos = mock(VehiculoService.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        SolicitudServicioServiceImpl service = new SolicitudServicioServiceImpl(ordenes, mock(TallerRepository.class),
                vehiculos, usuarios, tallerService, mock(CloudinaryService.class), mock(TransicionOrdenService.class),
                mock(AsignacionTallerService.class), mock(ParametrosMarketplace.class), mock(AvisosSolicitudServicio.class),
                mock(ProductoService.class), mock(BuscadorRepuestosService.class), mock(LiquidacionService.class),
                mock(CalificacionTallerService.class), mock(HorarioTallerService.class));

        when(tallerService.obtenerPorUsuario("t@demo.com")).thenReturn(Taller.builder().id("t1").build());
        OrdenServicio a = OrdenServicio.builder().id("a").flujo(EstadoOrden.FLUJO_MARKETPLACE).vehiculoId("v1").usuarioId("u1").build();
        OrdenServicio b = OrdenServicio.builder().id("b").flujo(EstadoOrden.FLUJO_MARKETPLACE).vehiculoId("v2").usuarioId("u1").build();
        OrdenServicio c = OrdenServicio.builder().id("c").flujo(EstadoOrden.FLUJO_MARKETPLACE).vehiculoId("borrado").usuarioId("u2").build();
        when(ordenes.findByFlujoAndTallerIdOrderByFechaCreacionDesc(EstadoOrden.FLUJO_MARKETPLACE, "t1")).thenReturn(List.of(a, b, c));
        when(vehiculos.obtenerEntidades(Set.of("v1", "v2", "borrado"))).thenReturn(List.of(
                Vehiculo.builder().id("v1").placa("ABC123").build(), Vehiculo.builder().id("v2").placa("XYZ789").build()));
        Usuario ana = new Usuario();
        ana.setId("u1");
        ana.setNombre("Ana");
        when(usuarios.obtenerPorIds(Set.of("u1", "u2"))).thenReturn(List.of(ana));

        List<SolicitudServicioDTO> bandeja = service.bandejaTaller("t@demo.com");

        assertEquals(List.of("ABC123", "XYZ789"), bandeja.subList(0, 2).stream().map(SolicitudServicioDTO::getVehiculoPlaca).toList());
        assertNull(bandeja.get(2).getVehiculoPlaca(), "vehículo eliminado: sin placa, sin error");
        assertEquals("Ana", bandeja.get(0).getClienteNombre());
        assertNull(bandeja.get(2).getClienteNombre(), "cliente eliminado");
        verify(vehiculos, times(1)).obtenerEntidades(any());
        verify(usuarios, times(1)).obtenerPorIds(any());
        verify(vehiculos, never()).obtenerEntidad(anyString());
        verify(usuarios, never()).obtenerPorId(anyString());
    }
}
