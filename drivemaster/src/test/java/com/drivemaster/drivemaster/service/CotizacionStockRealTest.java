package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Caso reportado: cotización con 2 filtros de aceite en stock, pago aprobado.
 * A diferencia de PagoMarketplaceServiceTest, aquí el inventario NO es un mock:
 * se usa MovimientoInventarioServiceImpl real sobre un stock en memoria, para
 * comprobar el stock final y la SALIDA registrada en movimientos_inventario.
 */
class CotizacionStockRealTest {

    private final Map<String, Producto> productos = new HashMap<>();
    private final List<MovimientoInventario> movimientos = new ArrayList<>();

    private PagoRepository pagoRepository;
    private PagoMarketplaceService service;
    private OrdenServicio orden;
    private Cotizacion cotizacion;

    private static Producto producto(String id, int stock, String tipo) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Filtro de aceite " + id);
        p.setStockActual(stock);
        p.setTiendaId("s1");
        p.setTipo(tipo);
        return p;
    }

    /** ProductoService en memoria con la misma semántica atómica que ProductoServiceImpl. */
    private ProductoService productoServiceEnMemoria() {
        ProductoService ps = mock(ProductoService.class);
        when(ps.obtenerPorId(anyString())).thenAnswer(i -> productos.get((String) i.getArgument(0)));
        when(ps.descontarStock(anyString(), anyInt())).thenAnswer(i -> {
            Producto p = productos.get((String) i.getArgument(0));
            int cantidad = i.getArgument(1);
            if (p.getStockActual() < cantidad) throw new StockInsuficienteException(p.getNombre(), p.getStockActual());
            p.setStockActual(p.getStockActual() - cantidad);
            return p;
        });
        when(ps.incrementarStock(anyString(), anyInt())).thenAnswer(i -> {
            Producto p = productos.get((String) i.getArgument(0));
            p.setStockActual(p.getStockActual() + (int) i.getArgument(1));
            return p;
        });
        return ps;
    }

    @BeforeEach
    void setUp() {
        productos.put("filtro", producto("filtro", 5, "STOCK"));
        productos.put("encargo", producto("encargo", 0, "ENCARGO"));

        ProductoService productoService = productoServiceEnMemoria();
        MovimientoInventarioRepository movimientoRepository = mock(MovimientoInventarioRepository.class);
        when(movimientoRepository.save(any())).thenAnswer(i -> {
            movimientos.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(movimientoRepository.existsByProductoIdAndTipoAndReferencia(anyString(), anyString(), anyString()))
                .thenAnswer(i -> movimientos.stream().anyMatch(m -> m.getProductoId().equals(i.getArgument(0))
                        && m.getTipo().equals(i.getArgument(1)) && i.getArgument(2).equals(m.getReferencia())));
        MovimientoInventarioService movimientoService = new MovimientoInventarioServiceImpl(movimientoRepository, productoService);

        pagoRepository = mock(PagoRepository.class);
        when(pagoRepository.reclamar(anyLong(), anyString())).thenReturn(1);
        OrdenServicioRepository ordenRepository = mock(OrdenServicioRepository.class);
        CotizacionRepository cotizacionRepository = mock(CotizacionRepository.class);
        PedidoTiendaRepository pedidoRepository = mock(PedidoTiendaRepository.class);
        when(pedidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TallerRepository tallerRepository = mock(TallerRepository.class);
        when(tallerRepository.findById("t1")).thenReturn(Optional.of(Taller.builder().id("t1").nombre("AutoMax").build()));
        TransicionOrdenService transiciones = mock(TransicionOrdenService.class);
        when(transiciones.transicionar(any(), anyString(), any(), anyString(), any(), any())).thenAnswer(i -> {
            OrdenServicio o = i.getArgument(0);
            o.setEstado(i.getArgument(1));
            return o;
        });

        service = new PagoMarketplaceService(mock(PagoService.class), pagoRepository, ordenRepository, cotizacionRepository,
                pedidoRepository, mock(RepartoPagoRepository.class), mock(ReembolsoRepository.class), tallerRepository,
                productoService, movimientoService, movimientoRepository, mock(UsuarioService.class), transiciones,
                mock(CotizacionService.class), mock(AvisosSolicitudServicio.class), mock(ParametrosMarketplace.class));

        orden = OrdenServicio.builder().id("o1").flujo(EstadoOrden.FLUJO_MARKETPLACE).estado(EstadoOrden.APROBADA)
                .tallerId("t1").usuarioId("u1").cotizacionId("c1").build();
        when(ordenRepository.findById("o1")).thenAnswer(i -> Optional.of(orden));
        cotizacion = Cotizacion.builder().id("c1").ordenId("o1").version(1).estado(Cotizacion.APROBADA).ivaPct(19.0)
                .items(new ArrayList<>(List.of(
                        item("filtro", 2, "DISPONIBLE"),
                        item("encargo", 1, "ENCARGO"))))
                .totalAPagar(100_000.0).vigenciaHasta(LocalDateTime.now().plusDays(1)).build();
        when(cotizacionRepository.findById("c1")).thenReturn(Optional.of(cotizacion));
    }

    private static CotizacionItem item(String productoId, int cantidad, String disponibilidad) {
        return CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).productoId(productoId)
                .productoNombre("Filtro de aceite " + productoId).tiendaId("s1").tiendaNombre("Tienda Norte")
                .cantidad(cantidad).subtotal(20_000.0 * cantidad).disponibilidad(disponibilidad).build();
    }

    private PagoEntity pago() {
        PagoEntity p = new PagoEntity();
        p.setId(10L);
        p.setConcepto(PagoMarketplaceService.COTIZACION);
        p.setOrdenServicioId("o1");
        p.setCotizacionId("c1");
        p.setUsuarioId("u1");
        p.setReferencia("DRV-REF");
        p.setMonto(100_000.0);
        p.setEstado("PENDIENTE");
        return p;
    }

    private List<MovimientoInventario> salidas(String productoId) {
        return movimientos.stream().filter(m -> "SALIDA".equals(m.getTipo()) && productoId.equals(m.getProductoId())).toList();
    }

    @Test
    void pagoAprobadoBajaElStockYRegistraLaSalida() {
        service.confirmar(pago(), "txn-1");

        assertEquals(3, productos.get("filtro").getStockActual());
        List<MovimientoInventario> salidas = salidas("filtro");
        assertEquals(1, salidas.size(), "Debe quedar una SALIDA en movimientos_inventario");
        assertEquals(2, salidas.get(0).getCantidad());
        assertEquals(5, salidas.get(0).getStockAnterior());
        assertEquals(3, salidas.get(0).getStockNuevo());
        assertEquals("s1", salidas.get(0).getTiendaId());
        assertEquals(PagoMarketplaceService.referenciaInventario(pago()), salidas.get(0).getReferencia());
        // Lo de encargo no se descuenta ni genera salida
        assertEquals(0, productos.get("encargo").getStockActual());
        assertTrue(salidas("encargo").isEmpty());
        assertEquals(EstadoOrden.REPUESTOS_PEDIDOS, orden.getEstado());
    }

    @Test
    void elDescuentoNoSeRepiteSiElPagoSeReprocesa() {
        service.confirmar(pago(), "txn-1");
        // Segundo aviso del mismo pago (webhook + redirección + verificar)
        when(pagoRepository.reclamar(anyLong(), anyString())).thenReturn(0);
        service.confirmar(pago(), "txn-1");
        assertEquals(3, productos.get("filtro").getStockActual());
        assertEquals(1, salidas("filtro").size());
    }

    @Test
    void aunqueSeReclameDosVecesLaSalidaNoSeDuplica() {
        // Defensa adicional: si el mismo pago llegara a aplicarse otra vez, la SALIDA
        // ya registrada con su referencia evita un segundo descuento.
        service.confirmar(pago(), "txn-1");
        orden.setEstado(EstadoOrden.APROBADA);
        service.confirmar(pago(), "txn-1");
        assertEquals(3, productos.get("filtro").getStockActual());
        assertEquals(1, salidas("filtro").size());
    }

    @Test
    void sinStockSeRevierteConEntradaRegistrada() {
        productos.get("filtro").setStockActual(1);

        service.confirmar(pago(), "txn-1");

        assertEquals(1, productos.get("filtro").getStockActual());
        assertTrue(salidas("filtro").isEmpty());
        assertEquals(EstadoOrden.DIAGNOSTICADA, orden.getEstado());
    }
}
