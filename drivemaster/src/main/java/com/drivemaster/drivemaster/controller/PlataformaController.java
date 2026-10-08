package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.plataforma.DashboardPlataformaDTO;
import com.drivemaster.drivemaster.service.PlataformaDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel de la plataforma. El acceso por rol lo define SecurityConfig (/api/plataforma/**: solo SUPERADMIN). */
@RestController
@RequestMapping("/api/plataforma")
public class PlataformaController {

    private final PlataformaDashboardService dashboardService;

    public PlataformaController(PlataformaDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardPlataformaDTO> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }
}
