package com.drivemaster.drivemaster.service;

import java.io.IOException;
import java.util.List;

import com.drivemaster.drivemaster.dto.ProductoDTO;
import com.drivemaster.drivemaster.model.Producto;
import org.springframework.web.multipart.MultipartFile;

public interface ProductoService {

    Producto crearProducto(Producto producto);

    Producto actualizarProducto(String id, Producto producto);

    Producto obtenerPorId(String id);

    Producto obtenerPorCodigo(String codigo);

    List<Producto> listarTodos();

    /** Catálogo de compra directa: solo productos de la tienda de DriveMaster. */
    List<Producto> listarCatalogoPublico();

    /**
     * Búsqueda para el agente del chat (n8n): mismo alcance que el catálogo público,
     * ordenada por cuántos términos coinciden. Máximo 10 resultados.
     */
    List<ProductoDTO> buscarParaAgente(List<String> terminos);

    List<Producto> listarPorTienda(String tiendaId);

    /**
     * Descuenta stock de forma atómica: solo se aplica si hay existencias suficientes
     * en el mismo instante de la escritura. Lanza StockInsuficienteException si no.
     */
    Producto descontarStock(String productoId, int cantidad);

    /** Suma stock de forma atómica. */
    Producto incrementarStock(String productoId, int cantidad);

    void eliminarProducto(String id);

    Producto actualizarStock(String productoId, Integer nuevoStock);

    List<Producto> obtenerPorCategoria(String categoria);

    List<Producto> obtenerPorMarca(String marca);

    List<Producto> buscarPorNombre(String nombre);

    List<Producto> obtenerPorModeloCompatible(String modelo);

    List<Producto> obtenerConStockBajo();

    String subirImagen(String id, MultipartFile archivo) throws IOException;

}