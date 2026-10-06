package com.drivemaster.drivemaster.security;

import java.util.List;

/**
 * Permisos asignados a cada rol al crear o cambiar de rol a un usuario.
 * Única fuente de verdad: antes estaba duplicada en AuthController y UsuarioController.
 */
public final class RolPermisos {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String VENDEDOR = "VENDEDOR";
    public static final String CLIENTE = "CLIENTE";
    public static final String TALLER = "TALLER";
    public static final String TIENDA = "TIENDA";
    // En v1 el mecánico es solo un nombre dentro del taller; el rol queda listo
    // para cuando tenga cuenta propia.
    public static final String MECANICO = "MECANICO";

    private RolPermisos() {
    }

    public static List<String> de(String rol) {
        if (rol == null) return List.of();
        return switch (rol) {
            case SUPERADMIN -> List.of(
                    "USUARIOS_READ", "USUARIOS_WRITE", "USUARIOS_DELETE",
                    "PRODUCTOS_READ", "PRODUCTOS_WRITE", "PRODUCTOS_DELETE",
                    "CLIENTES_READ", "CLIENTES_WRITE", "CLIENTES_DELETE",
                    "VENTAS_READ", "VENTAS_WRITE", "VENTAS_DELETE",
                    "COMPRAS_READ", "COMPRAS_WRITE", "COMPRAS_DELETE",
                    "PROVEEDORES_READ", "PROVEEDORES_WRITE", "PROVEEDORES_DELETE",
                    "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE",
                    "AJUSTES_READ", "AJUSTES_WRITE"
            );
            case ADMIN -> List.of(
                    "USUARIOS_READ", "USUARIOS_WRITE",
                    "PRODUCTOS_READ", "PRODUCTOS_WRITE",
                    "CLIENTES_READ", "CLIENTES_WRITE",
                    "VENTAS_READ", "VENTAS_WRITE",
                    "COMPRAS_READ", "COMPRAS_WRITE",
                    "PROVEEDORES_READ", "PROVEEDORES_WRITE",
                    "MOVIMIENTOS_READ", "MOVIMIENTOS_WRITE",
                    "AJUSTES_READ", "AJUSTES_WRITE"
            );
            case VENDEDOR -> List.of(
                    "PRODUCTOS_READ",
                    "CLIENTES_READ", "CLIENTES_WRITE",
                    "VENTAS_READ", "VENTAS_WRITE"
            );
            case CLIENTE -> List.of("ROLE_CLIENTE", "PRODUCTOS_READ");
            case TALLER -> List.of("TALLER_ORDENES_READ", "TALLER_ORDENES_WRITE");
            case TIENDA -> List.of(
                    "TIENDA_PRODUCTOS_READ", "TIENDA_PRODUCTOS_WRITE",
                    "TIENDA_PEDIDOS_READ", "TIENDA_PEDIDOS_WRITE"
            );
            case MECANICO -> List.of("MECANICO_ORDENES_READ", "MECANICO_ORDENES_WRITE");
            default -> List.of();
        };
    }
}
