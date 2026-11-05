package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.repository.MovimientoPiezaTallerRepository;
import com.drivemaster.drivemaster.repository.PiezaTallerRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Inventario de talleres en memoria para pruebas, con la semántica de Mongo que importa: leer da una copia, guardar
 * comprueba la version e insertar un id repetido falla.
 */
final class InventarioEnMemoria {

    final Map<String, PiezaTaller> piezas = new LinkedHashMap<>();
    final Map<String, MovimientoPiezaTaller> movimientos = new LinkedHashMap<>();
    final PiezaTallerRepository piezaRepository = mock(PiezaTallerRepository.class);
    final MovimientoPiezaTallerRepository movimientoRepository = mock(MovimientoPiezaTallerRepository.class);
    final StockTallerService stock = new StockTallerService(piezaRepository, movimientoRepository);
    // Corre una vez antes de la próxima escritura: simula a otro proceso que escribe justo entre leer y guardar
    Runnable antesDeGuardar;

    InventarioEnMemoria() {
        when(piezaRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(copia(piezas.get(i.<String>getArgument(0)))));
        when(piezaRepository.save(any())).thenAnswer(i -> guardar(i.getArgument(0)));
        when(piezaRepository.findByTallerIdOrderByNombreAsc(anyString())).thenAnswer(i -> piezas.values().stream()
                .filter(p -> i.<String>getArgument(0).equals(p.getTallerId())).map(InventarioEnMemoria::copia).toList());
        when(piezaRepository.findByTallerIdAndStockGreaterThan(anyString(), anyInt())).thenAnswer(i -> piezas.values().stream()
                .filter(p -> i.<String>getArgument(0).equals(p.getTallerId()) && p.getStock() > i.<Integer>getArgument(1))
                .map(InventarioEnMemoria::copia).toList());

        when(movimientoRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(movimientos.get(i.<String>getArgument(0))));
        when(movimientoRepository.insert(any(MovimientoPiezaTaller.class))).thenAnswer(i -> {
            MovimientoPiezaTaller m = i.getArgument(0);
            if (movimientos.containsKey(m.getId())) throw new DuplicateKeyException(m.getId());
            movimientos.put(m.getId(), m);
            return m;
        });
        when(movimientoRepository.save(any(MovimientoPiezaTaller.class))).thenAnswer(i -> {
            MovimientoPiezaTaller m = i.getArgument(0);
            movimientos.put(m.getId(), m);
            return m;
        });
        doAnswer(i -> movimientos.remove(i.<String>getArgument(0))).when(movimientoRepository).deleteById(anyString());
        when(movimientoRepository.findByReferenciaAndTipo(anyString(), anyString())).thenAnswer(i -> movimientos.values().stream()
                .filter(m -> i.<String>getArgument(0).equals(m.getReferencia()) && i.<String>getArgument(1).equals(m.getTipo()))
                .collect(Collectors.toCollection(ArrayList::new)));
        when(movimientoRepository.findByTallerIdOrderByFechaDesc(anyString())).thenAnswer(i -> movimientos.values().stream()
                .filter(m -> i.<String>getArgument(0).equals(m.getTallerId()))
                .sorted(java.util.Comparator.comparing(MovimientoPiezaTaller::getFecha, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    private PiezaTaller guardar(PiezaTaller p) {
        if (antesDeGuardar != null) {
            Runnable una = antesDeGuardar;
            antesDeGuardar = null;
            una.run();
        }
        PiezaTaller actual = piezas.get(p.getId());
        if (actual == null && p.getVersion() != null) throw new OptimisticLockingFailureException(p.getId());
        if (actual != null && !actual.getVersion().equals(p.getVersion())) throw new OptimisticLockingFailureException(p.getId());
        p.setVersion(p.getVersion() == null ? 0L : p.getVersion() + 1);
        piezas.put(p.getId(), copia(p));
        return p;
    }

    static PiezaTaller copia(PiezaTaller p) {
        if (p == null) return null;
        List<PiezaTaller.Lote> lotes = p.getLotes().stream().map(l -> PiezaTaller.Lote.builder().id(l.getId())
                .compraId(l.getCompraId()).pedidoId(l.getPedidoId()).productoId(l.getProductoId()).tiendaId(l.getTiendaId())
                .tiendaNombre(l.getTiendaNombre()).cantidad(l.getCantidad()).disponible(l.getDisponible())
                .costoUnitario(l.getCostoUnitario()).precioPublico(l.getPrecioPublico()).descuentoPct(l.getDescuentoPct())
                .precioPagado(l.getPrecioPagado()).ivaEnCosto(l.getIvaEnCosto()).fechaEntrada(l.getFechaEntrada()).build())
                .collect(Collectors.toCollection(ArrayList::new));
        return PiezaTaller.builder().id(p.getId()).version(p.getVersion()).tallerId(p.getTallerId()).clave(p.getClave())
                .nombre(p.getNombre()).marca(p.getMarca()).referencia(p.getReferencia()).categoria(p.getCategoria())
                .imagenUrl(p.getImagenUrl()).modelosCompatibles(p.getModelosCompatibles())
                .compatibilidadUniversal(p.getCompatibilidadUniversal()).precioVenta(p.getPrecioVenta()).stock(p.getStock())
                .stockMinimo(p.getStockMinimo()).avisoStockBajo(p.getAvisoStockBajo())
                .lotes(lotes).fechaCreacion(p.getFechaCreacion()).build();
    }

    /** Disponible de cada lote de una pieza, por id de lote. */
    Map<String, Integer> disponiblePorLote(String piezaId) {
        Map<String, Integer> r = new LinkedHashMap<>();
        piezas.get(piezaId).getLotes().forEach(l -> r.put(l.getId(), l.getDisponible()));
        return r;
    }
}
