package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.Notificacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Cada paso del flujo nuevo avisa en la campanita a quien le toca (taller, tienda, cliente o ADMIN),
 * sin códigos de retiro ni enlaces con token, y un fallo del aviso no rompe el paso.
 */
class AvisosCampanitaTest {

    private EmailsService correos;
    private UsuarioRepository usuarios;
    private TallerRepository talleres;
    private TiendaRepository tiendas;
    private NotificacionService campanita;
    private AvisosSolicitudServicio avisos;

    @BeforeEach
    void setUp() {
        correos = mock(EmailsService.class);
        usuarios = mock(UsuarioRepository.class);
        talleres = mock(TallerRepository.class);
        tiendas = mock(TiendaRepository.class);
        campanita = mock(NotificacionService.class);
        avisos = new AvisosSolicitudServicio(correos, usuarios, talleres, tiendas, campanita, "http://localhost:5173");
        when(talleres.findById("t1")).thenReturn(Optional.of(
                Taller.builder().id("t1").nombre("Taller Norte").usuarioId("u-taller").build()));
    }

    private static OrdenServicio orden() {
        OrdenServicio o = new OrdenServicio();
        o.setId("o1");
        o.setUsuarioId("u-cliente");
        o.setTallerId("t1");
        o.setTallerNombre("Taller Norte");
        o.setVehiculoDescripcion("Mazda 3 2018");
        o.setCiudad("Cartagena");
        return o;
    }

    private static PedidoTienda pedido(String id, String tiendaId) {
        return PedidoTienda.builder().id(id).tiendaId(tiendaId).tiendaNombre("Autopartes " + tiendaId)
                .tallerNombre("Taller Norte").clienteId("u-cliente").compraId("c1").ordenId("o1")
                .entrega(PedidoTienda.RETIRO).items(new ArrayList<>()).total(78880.0).build();
    }

    private void tienda(String id, String usuarioId) {
        when(tiendas.findById(id)).thenReturn(Optional.of(Tienda.builder().id(id).nombre("Autopartes " + id).usuarioId(usuarioId).build()));
    }

    /** Todos los textos de los avisos enviados (título, mensaje, enlace y referencia). */
    private List<String> textosAvisados() {
        ArgumentCaptor<String> textos = ArgumentCaptor.forClass(String.class);
        verify(campanita, atLeastOnce()).avisar(anyString(), anyString(), textos.capture(), textos.capture(),
                any(), any());
        return textos.getAllValues();
    }

    @Test
    void solicitudAsignadaAvisaAlTallerYAlCliente() {
        avisos.tallerAsignado(orden(), talleres.findById("t1").orElseThrow());

        verify(campanita).avisar(eq("u-taller"), eq(Notificacion.SOLICITUD_NUEVA), anyString(),
                contains("Mazda 3 2018"), eq("taller-solicitudes"), eq("o1"));
        verify(campanita).avisar(eq("u-cliente"), eq(Notificacion.SOLICITUD_ASIGNADA), anyString(),
                contains("Taller Norte"), eq("mis-servicios"), eq("o1"));
    }

    @Test
    void cotizacionEmitidaAvisaAlClienteSinElToken() {
        Cotizacion c = Cotizacion.builder().id("c1").ordenId("o1").tallerId("t1").tallerNombre("Taller Norte").version(1).build();

        avisos.cotizacionEmitida(orden(), c, "tok-secreto-123");

        verify(campanita).avisar(eq("u-cliente"), eq(Notificacion.COTIZACION_EMITIDA), anyString(), anyString(),
                eq("mis-servicios"), eq("o1"));
        textosAvisados().forEach(t -> assertFalse(t != null && t.contains("tok-secreto-123"), t));
    }

    @Test
    void respuestaYVencimientoDeLaCotizacionAvisanAlTaller() {
        Cotizacion c = Cotizacion.builder().id("c1").ordenId("o1").tallerId("t1").version(2)
                .vehiculoDescripcion("Mazda 3 2018").build();

        avisos.cotizacionRespondida(c, "aprobada", null);
        avisos.cotizacionVencida(c);

        verify(campanita).avisar(eq("u-taller"), eq(Notificacion.COTIZACION_RESPONDIDA), eq("Cotización aprobada"),
                anyString(), eq("taller-cotizaciones"), eq("o1"));
        verify(campanita).avisar(eq("u-taller"), eq(Notificacion.COTIZACION_VENCIDA), anyString(),
                anyString(), eq("taller-cotizaciones"), eq("o1"));
    }

    @Test
    void cotizacionPagadaAvisaAlTallerYACadaTiendaConSuPedido() {
        tienda("s1", "u-tienda1");
        tienda("s2", "u-tienda2");

        avisos.cotizacionPagada(orden(), List.of(pedido("p1", "s1"), pedido("p2", "s2")));

        verify(campanita).avisar(eq("u-taller"), eq(Notificacion.PAGO_APROBADO), anyString(), anyString(),
                eq("taller-trabajos"), eq("o1"));
        verify(campanita).avisar(eq("u-tienda1"), eq(Notificacion.PEDIDO_NUEVO), anyString(), contains("78880 COP"),
                eq("pedidos-tienda"), eq("p1"));
        verify(campanita).avisar(eq("u-tienda2"), eq(Notificacion.PEDIDO_NUEVO), anyString(), anyString(),
                eq("pedidos-tienda"), eq("p2"));
    }

    @Test
    void compraDelCatalogoAvisaSinMostrarLosCodigosDeRetiro() {
        tienda("s1", "u-tienda1");
        CompraCatalogo compra = new CompraCatalogo();
        compra.setId("c1");
        compra.setClienteId("u-cliente");
        PagoEntity pago = new PagoEntity();
        pago.setReferencia("DRV-1");

        avisos.compraPagada(compra, List.of(pedido("p1", "s1")), Map.of("p1", "481516"), pago);
        avisos.codigoRetiroNuevo(pedido("p1", "s1"), "234216");

        verify(campanita).avisar(eq("u-cliente"), eq(Notificacion.PAGO_APROBADO), anyString(), anyString(),
                eq("mis-compras"), eq("c1"));
        verify(campanita).avisar(eq("u-tienda1"), eq(Notificacion.PEDIDO_NUEVO), contains("retiro"), anyString(),
                eq("pedidos-tienda"), eq("p1"));
        textosAvisados().forEach(t -> {
            assertFalse(t != null && t.contains("481516"), t);
            assertFalse(t != null && t.contains("234216"), t);
        });
    }

    @Test
    void lasAlertasLleganSoloAAdministradoresActivos() {
        when(usuarios.findByRol("ADMIN")).thenReturn(List.of(
                Usuario.builder().id("a1").correo("a1@demo.com").activo(true).build(),
                Usuario.builder().id("a2").correo("a2@demo.com").activo(false).build()));
        when(usuarios.findByRol("SUPERADMIN")).thenReturn(List.of(Usuario.builder().id("s1").correo("s1@demo.com").build()));

        avisos.alertaAdmin("Liquidación fallida", "Detalle");

        verify(campanita).avisar(eq("a1"), eq(Notificacion.ALERTA_ADMIN), eq("Liquidación fallida"), eq("Detalle"), isNull(), isNull());
        verify(campanita).avisar(eq("s1"), eq(Notificacion.ALERTA_ADMIN), anyString(), anyString(), isNull(), isNull());
        verify(campanita, never()).avisar(eq("a2"), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void unProblemaDeEnvioLlevaAlAdminASuPantallaYAvisaALaTienda() {
        when(usuarios.findByRol("ADMIN")).thenReturn(List.of(Usuario.builder().id("a1").correo("a1@demo.com").build()));
        tienda("s1", "u-tienda1");
        PedidoTienda p = pedido("p1", "s1");
        p.setEntrega(PedidoTienda.ENVIO);

        avisos.problemaEnvio(p, "Llegó roto");

        verify(campanita).avisar(eq("a1"), eq(Notificacion.ALERTA_ADMIN), eq("Problema con un envío"), anyString(),
                eq("problemas-envios"), isNull());
        verify(campanita).avisar(eq("u-tienda1"), eq(Notificacion.PROBLEMA_ENVIO), anyString(), anyString(),
                eq("pedidos-tienda"), eq("p1"));
    }

    @Test
    void siLaCampanitaFallaElCorreoSaleYElFlujoSigue() throws Exception {
        doThrow(new RuntimeException("Mongo caído")).when(campanita).avisar(any(), any(), any(), any(), any(), any());
        when(usuarios.findByRol("ADMIN")).thenReturn(List.of(Usuario.builder().id("a1").correo("a1@demo.com").build()));
        tienda("s1", "u-tienda1");

        assertDoesNotThrow(() -> avisos.alertaAdmin("Reembolso pendiente", "Detalle"));
        assertDoesNotThrow(() -> avisos.cotizacionPagada(orden(), List.of(pedido("p1", "s1"))));
        assertDoesNotThrow(() -> avisos.tallerAsignado(orden(), talleres.findById("t1").orElseThrow()));

        verify(correos).enviarEmail(eq("a1@demo.com"), contains("Reembolso pendiente"), anyString());
    }

    @Test
    void siElCorreoFallaElAvisoIgualSale() throws Exception {
        doThrow(new RuntimeException("SMTP caído")).when(correos).enviarEmail(anyString(), anyString(), anyString());
        when(usuarios.findByRol("ADMIN")).thenReturn(List.of(Usuario.builder().id("a1").correo("a1@demo.com").build()));

        avisos.alertaAdmin("Liquidación fallida", "Detalle");

        verify(campanita).avisar(eq("a1"), eq(Notificacion.ALERTA_ADMIN), anyString(), anyString(), any(), any());
    }
}
