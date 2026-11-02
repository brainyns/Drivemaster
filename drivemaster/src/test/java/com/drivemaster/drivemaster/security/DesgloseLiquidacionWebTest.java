package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.DesgloseLiquidacionController;
import com.drivemaster.drivemaster.service.DesgloseLiquidacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El desglose con la seguridad real: cada ruta solo para su rol y siempre con el negocio de la sesión. */
@WebMvcTest(controllers = DesgloseLiquidacionController.class)
@Import(SecurityConfig.class)
class DesgloseLiquidacionWebTest {

    private static final String PEDIDO = "/api/pedidos-tienda/ped-1/liquidacion";
    private static final String ORDEN = "/api/talleres/mi-taller/ordenes/o1/liquidacion";

    @Autowired private MockMvc mvc;

    @MockBean private DesgloseLiquidacionService desglose;
    @MockBean private com.drivemaster.drivemaster.service.ResumenServicioService resumen;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void laTiendaConsultaSuPedidoConElCorreoDeLaSesion() throws Exception {
        mvc.perform(get(PEDIDO).with(user("t@demo.com").roles("TIENDA"))).andExpect(status().isOk());

        verify(desglose).dePedido("t@demo.com", "ped-1");
    }

    @Test
    void elTallerConsultaSuOrdenConElCorreoDeLaSesion() throws Exception {
        mvc.perform(get(ORDEN).with(user("t@demo.com").roles("TALLER"))).andExpect(status().isOk());

        verify(desglose).deOrden("t@demo.com", "o1");
    }

    @Test
    void ningunOtroRolVeElDesgloseDeUnPedido() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE", "TALLER"}) {
            mvc.perform(get(PEDIDO).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get(PEDIDO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(desglose);
    }

    @Test
    void ningunOtroRolVeElDesgloseDeUnaOrden() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE", "TIENDA"}) {
            mvc.perform(get(ORDEN).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get(ORDEN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(desglose);
    }
}
