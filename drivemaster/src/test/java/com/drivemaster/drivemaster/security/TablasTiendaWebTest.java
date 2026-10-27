package com.drivemaster.drivemaster.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.drivemaster.drivemaster.controller.TablasTiendaController;
import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.tienda.TablasTiendaDTO;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.service.ProductoService;
import com.drivemaster.drivemaster.service.TablasTiendaService;
import com.drivemaster.drivemaster.service.TiendaService;

/**
 * Las tablas paginadas de la tienda con la seguridad real: solo la TIENDA entra, siempre con SU tienda, y un orden o
 * una fecha mal pedidos responden 400 antes de consultar.
 */
@WebMvcTest(controllers = TablasTiendaController.class)
@Import({SecurityConfig.class, AlcanceTienda.class})
class TablasTiendaWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private TablasTiendaService tablas;
    @MockBean private TiendaService tiendaService;
    @MockBean private ProductoService productoService; // lo necesita AlcanceTienda
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        when(tiendaService.obtenerOperativaPorUsuario("a@demo.com")).thenReturn(Tienda.builder().id("A").nombre("Tienda A").build());
        when(tablas.inventario(any(), any(), any(), any())).thenReturn(new TablasTiendaDTO.Inventario(
                new Pagina<>(List.of(), 45, 1, 20), Map.of("TODOS", 45L), new TablasTiendaDTO.ResumenInventario(45, 40, 3, 0, 0)));
        when(tablas.pedidos(any(), any(), any(), any(), any())).thenReturn(new TablasTiendaDTO.Pedidos(Pagina.vacia(20), Map.of()));
        when(tablas.movimientos(any(), any(), any(), any(), any())).thenReturn(new TablasTiendaDTO.Movimientos(Pagina.vacia(20),
                new TablasTiendaDTO.ResumenMovimientos(0, 0, 0, 0)));
    }

    @Test
    void laTiendaVeSusTresTablasConElFormatoComun() throws Exception {
        mvc.perform(get("/api/productos/mi-tienda/tabla").param("page", "1").with(user("a@demo.com").roles("TIENDA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos.total").value(45))
                .andExpect(jsonPath("$.productos.totalPaginas").value(3))
                .andExpect(jsonPath("$.productos.pagina").value(1));
        mvc.perform(get("/api/pedidos-tienda/mios/tabla").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        mvc.perform(get("/api/movimientos/tabla").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());

        verify(tablas).inventario(eq("A"), isNull(), isNull(), any());
    }

    @Test
    void otrosRolesNoEntran() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "VENDEDOR", "CLIENTE", "TALLER"}) {
            mvc.perform(get("/api/productos/mi-tienda/tabla").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get("/api/pedidos-tienda/mios/tabla").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get("/api/movimientos/tabla").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/productos/mi-tienda/tabla")).andExpect(status().isUnauthorized());
    }

    @Test
    void unOrdenNoPermitidoOUnaFechaInvertidaResponden400SinConsultar() throws Exception {
        mvc.perform(get("/api/productos/mi-tienda/tabla").param("sort", "precioCompra,desc").with(user("a@demo.com").roles("TIENDA")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pedidos-tienda/mios/tabla").param("desde", "2026-10-08").param("hasta", "2026-10-01")
                .with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isBadRequest());
        verify(tablas, never()).inventario(any(), any(), any(), any());
        verify(tablas, never()).pedidos(any(), any(), any(), any(), any());
    }

    @Test
    void movimientosAceptaLosAliasViejosDeFecha() throws Exception {
        mvc.perform(get("/api/movimientos/tabla").param("fechaInicio", "2026-10-01").param("fechaFin", "2026-10-07")
                .with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        verify(tablas).movimientos(eq("A"), isNull(), isNull(), eq(RangoFechas.de("2026-10-01", "2026-10-07")), any());
    }
}
