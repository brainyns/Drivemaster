package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.service.ProductoService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Hasta dónde llega el usuario sobre el inventario: el personal interno ve todo y
 * un usuario TIENDA solo su propia tienda (y solo si ya fue aprobada).
 */
@Component
public class AlcanceTienda {

    private final TiendaService tiendaService;
    private final ProductoService productoService;

    public AlcanceTienda(TiendaService tiendaService, ProductoService productoService) {
        this.tiendaService = tiendaService;
        this.productoService = productoService;
    }

    public boolean esTienda(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + RolPermisos.TIENDA).equals(a.getAuthority()));
    }

    /** Id de la tienda del usuario TIENDA; null para el personal interno (sin restricción). */
    public String tiendaId(Authentication auth) {
        return esTienda(auth) ? tiendaService.obtenerOperativaPorUsuario(auth.getName()).getId() : null;
    }

    /** Carga el producto y verifica que el usuario pueda gestionarlo (403 si es de otra tienda). */
    public Producto productoGestionable(Authentication auth, String productoId) {
        Producto producto = productoService.obtenerPorId(productoId);
        String tiendaId = tiendaId(auth);
        if (tiendaId != null && !tiendaId.equals(producto.getTiendaId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este producto no pertenece a tu tienda");
        }
        return producto;
    }
}
