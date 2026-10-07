package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.catalogo.CompraCotizadaDTO;
import com.drivemaster.drivemaster.dto.catalogo.CompraDTO;
import com.drivemaster.drivemaster.dto.catalogo.CompraRequest;
import com.drivemaster.drivemaster.dto.catalogo.FiltrosCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.PaginaCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.ProductoCatalogoDTO;
import com.drivemaster.drivemaster.service.CatalogoService;
import com.drivemaster.drivemaster.service.CompraCatalogoService;
import com.drivemaster.drivemaster.service.RecepcionEnvioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Catálogo marketplace (consulta pública) y compra directa (CLIENTE).
 *
 * Públicos (GET): catálogo, detalle y filtros. Si trae sesión de cliente y un vehiculoId, se
 * avisa la compatibilidad (nunca se oculta nada). La compra exige sesión de CLIENTE.
 */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final CatalogoService catalogo;
    private final CompraCatalogoService compras;
    private final RecepcionEnvioService recepcion;

    public CatalogoController(CatalogoService catalogo, CompraCatalogoService compras, RecepcionEnvioService recepcion) {
        this.catalogo = catalogo;
        this.compras = compras;
        this.recepcion = recepcion;
    }

    @GetMapping("/productos")
    public ResponseEntity<PaginaCatalogoDTO> productos(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String ciudad,
                                                       @RequestParam(required = false) String tiendaId,
                                                       @RequestParam(required = false) String categoria,
                                                       @RequestParam(required = false) String vehiculoId,
                                                       @RequestParam(required = false) String orden,
                                                       @RequestParam(required = false) Integer pagina,
                                                       @RequestParam(required = false) Integer tamanio,
                                                       Authentication auth) {
        return ResponseEntity.ok(catalogo.buscar(q, ciudad, tiendaId, categoria, vehiculoId, correo(auth), orden, pagina, tamanio));
    }

    @GetMapping("/productos/{id}")
    public ResponseEntity<ProductoCatalogoDTO> detalle(@PathVariable String id,
                                                       @RequestParam(required = false) String vehiculoId,
                                                       Authentication auth) {
        return ResponseEntity.ok(catalogo.detalle(id, vehiculoId, correo(auth)));
    }

    @GetMapping("/filtros")
    public ResponseEntity<FiltrosCatalogoDTO> filtros() {
        return ResponseEntity.ok(catalogo.filtros());
    }

    // ─── Compra directa (CLIENTE) ────────────────────────────────────────────

    /** Desglose del carrito por tienda y total. No cobra ni reserva nada. */
    @PostMapping("/compras/cotizar")
    public ResponseEntity<CompraCotizadaDTO> cotizar(@RequestBody CompraRequest request, Authentication auth) {
        return ResponseEntity.ok(compras.cotizar(auth.getName(), request));
    }

    /** Revisa el stock de todas las tiendas, guarda la compra y devuelve el link de pago de Wompi. */
    @PostMapping("/compras")
    public ResponseEntity<Map<String, Object>> pagar(@RequestBody CompraRequest request, Authentication auth) {
        return ResponseEntity.ok(compras.iniciarPago(auth.getName(), request));
    }

    @GetMapping("/compras/mias")
    public ResponseEntity<List<CompraDTO>> misCompras(Authentication auth) {
        return ResponseEntity.ok(compras.misCompras(auth.getName()));
    }

    /** Código de retiro nuevo (reemplaza al anterior). Se muestra una sola vez. */
    @PostMapping("/pedidos/{pedidoId}/codigo-retiro")
    public ResponseEntity<Map<String, Object>> nuevoCodigo(@PathVariable String pedidoId, Authentication auth) {
        return ResponseEntity.ok(compras.nuevoCodigoRetiro(auth.getName(), pedidoId));
    }

    /** "Ya lo recibí": el cliente confirma la recepción de un envío. */
    @PostMapping("/pedidos/{pedidoId}/recibido")
    public ResponseEntity<CompraDTO.PedidoCompraDTO> recibido(@PathVariable String pedidoId, Authentication auth) {
        return ResponseEntity.ok(CompraDTO.PedidoCompraDTO.de(recepcion.confirmarRecepcion(auth.getName(), pedidoId)));
    }

    /** "Reportar un problema": pausa la autoconfirmación y la liquidación y alerta al ADMIN. */
    @PostMapping("/pedidos/{pedidoId}/problema")
    public ResponseEntity<CompraDTO.PedidoCompraDTO> problema(@PathVariable String pedidoId,
                                                              @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(CompraDTO.PedidoCompraDTO.de(
                recepcion.reportarProblema(auth.getName(), pedidoId, body != null ? body.get("detalle") : null)));
    }

    /** Correo de la sesión, o null si la petición es anónima. */
    private static String correo(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()) ? auth.getName() : null;
    }
}
