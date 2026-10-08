package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/**
 * El ADMIN solo aprueba o rechaza negocios: un negocio aprobado por él arranca con el plan de prueba. Asignar
 * cualquier otro plan es una decisión de la plataforma y es solo del SUPERADMIN.
 */
public final class PermisosAprobacion {

    public static final String PLAN_DE_PRUEBA = "PRUEBA";

    private PermisosAprobacion() {
    }

    public static boolean esSuperadmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
    }

    public static void validarPlan(Authentication auth, AprobacionRequest request) {
        if (request == null || request.getPlanCodigo() == null || request.getPlanCodigo().isBlank()) return;
        boolean superadmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPERADMIN".equals(a.getAuthority()));
        if (!superadmin && !PLAN_DE_PRUEBA.equalsIgnoreCase(request.getPlanCodigo().trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el SUPERADMIN puede asignar un plan distinto al de prueba");
        }
    }
}
