package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.SuscripcionService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * La migración de arranque solo aprueba los talleres antiguos (sin estado) que tienen una cuenta TALLER válida.
 * Los que no la tienen quedan PENDIENTE e inactivos: un taller sin estado contaría como aprobado y recibiría
 * solicitudes sin dueño que pueda atenderlas.
 */
class MarketplaceMigrationTest {

    private TallerRepository talleres;
    private UsuarioRepository usuarios;
    private SuscripcionService suscripciones;
    private MarketplaceMigration migracion;

    @BeforeEach
    void setUp() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        talleres = mock(TallerRepository.class);
        usuarios = mock(UsuarioRepository.class);
        suscripciones = mock(SuscripcionService.class);
        when(suscripciones.vigente(anyString(), anyString())).thenReturn(Optional.empty());
        when(talleres.save(any(Taller.class))).thenAnswer(i -> i.getArgument(0));
        migracion = new MarketplaceMigration(mongo, talleres, suscripciones, usuarios);
    }

    private static Taller antiguo(String id, String usuarioId) {
        return Taller.builder().id(id).nombre("Taller " + id).usuarioId(usuarioId).activo(true).build();
    }

    private void cuenta(String id, String rol) {
        when(usuarios.findById(id)).thenReturn(Optional.of(Usuario.builder().id(id).rol(rol).build()));
    }

    @Test
    void conCuentaTallerValidaSeApruebaYRecibePlanDePrueba() {
        Taller conCuenta = antiguo("t1", "u1");
        cuenta("u1", "TALLER");
        when(talleres.findAll()).thenReturn(List.of(conCuenta));

        migracion.run();

        assertEquals(EstadoAprobacion.APROBADO, conCuenta.getEstadoAprobacion());
        assertTrue(conCuenta.getActivo());
        verify(suscripciones).activar(eq(SuscripcionService.ACTOR_TALLER), eq("t1"), anyString(), anyString());
    }

    @Test
    void sinCuentaValidaQuedaPendienteEInactivoSinPlan() {
        Taller sinUsuarioId = antiguo("t2", null);
        Taller vacio = antiguo("t3", " ");
        Taller usuarioInexistente = antiguo("t4", "fantasma");
        Taller cuentaDeOtroRol = antiguo("t5", "u-cliente");
        when(usuarios.findById("fantasma")).thenReturn(Optional.empty());
        cuenta("u-cliente", "CLIENTE");
        when(talleres.findAll()).thenReturn(List.of(sinUsuarioId, vacio, usuarioInexistente, cuentaDeOtroRol));

        migracion.run();

        for (Taller t : List.of(sinUsuarioId, vacio, usuarioInexistente, cuentaDeOtroRol)) {
            assertEquals(EstadoAprobacion.PENDIENTE, t.getEstadoAprobacion(), t.getId());
            assertFalse(t.getActivo(), t.getId());
            assertFalse(EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()), t.getId());
        }
        verify(suscripciones, never()).activar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void losTalleresQueYaTienenEstadoNoSeTocan() {
        Taller rechazado = antiguo("t6", null);
        rechazado.setEstadoAprobacion(EstadoAprobacion.RECHAZADO);
        Taller aprobado = antiguo("t7", "u7");
        aprobado.setEstadoAprobacion(EstadoAprobacion.APROBADO);
        when(talleres.findAll()).thenReturn(List.of(rechazado, aprobado));

        migracion.run();

        verify(talleres, never()).save(any());
        verify(usuarios, never()).findById(anyString());
        assertEquals(EstadoAprobacion.RECHAZADO, rechazado.getEstadoAprobacion());
    }
}
