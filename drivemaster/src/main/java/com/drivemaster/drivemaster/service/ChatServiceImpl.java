package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AiIntentResponse;
import com.drivemaster.drivemaster.dto.ChatRequest;
import com.drivemaster.drivemaster.dto.ChatResponse;
import com.drivemaster.drivemaster.dto.ProductoDTO;
import com.drivemaster.drivemaster.model.Producto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final GeminiService geminiService;
    private final ProductoService productoService;

    @Override
    public ChatResponse procesarMensaje(ChatRequest request) {
        try {
            String mensajeUsuario = request.getMensaje();
            log.info("Procesando mensaje: {}", mensajeUsuario);

            AiIntentResponse intentResponse = geminiService.interpretIntent(mensajeUsuario);
            log.info("Intención detectada: {}", intentResponse.getIntent());

            String respuestaNatural;
            List<ProductoDTO> productosRelacionados = new ArrayList<>();

            switch (intentResponse.getIntent()) {
                case "buscar_producto":
                    respuestaNatural = "Buscando productos que coincidan con tu consulta...";
                    productosRelacionados = buscarProductos(intentResponse);
                    break;
                case "consultar_precio":
                    respuestaNatural = "Consultando precios de los productos solicitados...";
                    productosRelacionados = buscarProductos(intentResponse);
                    break;
                case "verificar_stock":
                    respuestaNatural = "Verificando disponibilidad en nuestro inventario...";
                    productosRelacionados = buscarProductos(intentResponse);
                    break;
                case "recomendar":
                    respuestaNatural = "Basado en tus necesidades, te recomiendo estos productos...";
                    productosRelacionados = buscarProductos(intentResponse);
                    break;
                default:
                    respuestaNatural = "Entiendo que tienes una consulta sobre nuestros productos. Déjame ayudarte...";
                    productosRelacionados = obtenerProductosPopulares();
                    break;
            }

            if (!productosRelacionados.isEmpty()) {
                respuestaNatural = generarRespuestaConProductos(respuestaNatural, productosRelacionados, intentResponse);
            } else {
                respuestaNatural = "No encontré productos que coincidan exactamente con tu búsqueda. ¿Podrías proporcionar más detalles?";
            }

            return new ChatResponse(respuestaNatural, productosRelacionados);

        } catch (Exception e) {
            log.error("Error procesando mensaje de chat: {}", e.getMessage(), e);
            return new ChatResponse("Lo siento, occurred un error al procesar tu mensaje. Por favor, intenta de nuevo.", new ArrayList<>());
        }
    }

    private List<ProductoDTO> buscarProductos(AiIntentResponse intentResponse) {
        if (intentResponse.getCategoria() != null && !intentResponse.getCategoria().isEmpty()) {
            return productoService.obtenerPorCategoria(intentResponse.getCategoria())
                    .stream()
                    .map(this::convertirADTO)
                    .collect(Collectors.toList());
        } else if (intentResponse.getMarca() != null && !intentResponse.getMarca().isEmpty()) {
            return productoService.obtenerPorMarca(intentResponse.getMarca())
                    .stream()
                    .map(this::convertirADTO)
                    .collect(Collectors.toList());
        } else if (intentResponse.getNombreProducto() != null && !intentResponse.getNombreProducto().isEmpty()) {
            return productoService.buscarPorNombre(intentResponse.getNombreProducto())
                    .stream()
                    .map(this::convertirADTO)
                    .collect(Collectors.toList());
        } else if (intentResponse.getModelo() != null && !intentResponse.getModelo().isEmpty()) {
            return productoService.obtenerPorModeloCompatible(intentResponse.getModelo())
                    .stream()
                    .map(this::convertirADTO)
                    .collect(Collectors.toList());
        } else {
            return obtenerProductosPopulares();
        }
    }

    private List<ProductoDTO> obtenerProductosPopulares() {
        return productoService.listarTodos()
                .stream()
                .limit(5)
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    private String generarRespuestaConProductos(String respuestaBase, List<ProductoDTO> productos, AiIntentResponse intentResponse) {
        StringBuilder respuesta = new StringBuilder(respuestaBase);
        respuesta.append("\n\n");

        if (productos.size() == 1) {
            ProductoDTO producto = productos.get(0);
            respuesta.append("Encontré el siguiente producto:").append("\n");
            respuesta.append("- ").append(producto.getNombre()).append(" (").append(producto.getMarca()).append(")").append("\n");
            respuesta.append("  Categoría: ").append(producto.getCategoria()).append("\n");
            respuesta.append("  Precio: $").append(String.format("%.2f", producto.getPrecioVenta())).append("\n");
            respuesta.append("  Stock disponible: ").append(producto.getStockActual()).append(" unidades");
        } else {
            respuesta.append("Encontré ").append(productos.size()).append(" productos relacionados:").append("\n");
            for (int i = 0; i < Math.min(productos.size(), 5); i++) {
                ProductoDTO producto = productos.get(i);
                respuesta.append("- ").append(producto.getNombre()).append(" (").append(producto.getMarca()).append(")").append("\n");
                respuesta.append("  Precio: $").append(String.format("%.2f", producto.getPrecioVenta())).append(", Stock: ").append(producto.getStockActual()).append("\n");
            }

            if (productos.size() > 5) {
                respuesta.append("... y ").append(productos.size() - 5).append(" productos más.");
            }
        }

        return respuesta.toString();
    }

    private ProductoDTO convertirADTO(Producto producto) {
        ProductoDTO dto = new ProductoDTO();
        dto.setId(producto.getId());
        dto.setCodigo(producto.getCodigo());
        dto.setNombre(producto.getNombre());
        dto.setCategoria(producto.getCategoria());
        dto.setMarca(producto.getMarca());
        dto.setPrecioVenta(producto.getPrecioVenta());
        dto.setStockActual(producto.getStockActual());
        return dto;
    }
}