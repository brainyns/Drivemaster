package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Recordatorios de mantenimiento con dos servidores (Render y el HomeLab) sobre la misma base:
 * el envío se reclama con un findAndModify antes de mandar el correo, así que sale una sola vez.
 */
class MantenimientoServiceTest {

    private VehiculoRepository vehiculos;
    private UsuarioRepository usuarios;
    private EmailsService email;
    private MongoTemplate mongo;
    private MantenimientoService servicio;

    @BeforeEach
    void setUp() {
        vehiculos = mock(VehiculoRepository.class);
        usuarios = mock(UsuarioRepository.class);
        email = mock(EmailsService.class);
        mongo = mock(MongoTemplate.class);
        servicio = nuevoServicio(mongo);
        when(usuarios.findById("u1")).thenReturn(Optional.of(Usuario.builder().id("u1").nombre("Ana").correo("ana@demo.com").build()));
    }

    private MantenimientoService nuevoServicio(MongoTemplate mongoTemplate) {
        MantenimientoService s = new MantenimientoService(vehiculos, usuarios, email, mongoTemplate);
        ReflectionTestUtils.setField(s, "diasEntreRecordatorios", 30);
        ReflectionTestUtils.setField(s, "intervaloPorDefecto", 5000);
        return s;
    }

    /** Vencido: 12.000 km con el último mantenimiento a los 5.000 y un intervalo de 5.000. */
    private static Vehiculo vencido(LocalDateTime ultimoRecordatorio) {
        return Vehiculo.builder().id("v1").usuarioId("u1").marca("Mazda").modelo("3")
                .kilometraje(12_000).kmUltimoMantenimiento(5_000).intervaloMantenimientoKm(5_000)
                .recordatoriosActivos(true).ultimoRecordatorioEnviado(ultimoRecordatorio).build();
    }

    private void reclamoGana() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Vehiculo.class)))
                .thenReturn(vencido(LocalDateTime.now()));
    }

    @Test
    void reclamaAntesDeEnviarYNoGuardaElDocumentoEntero() {
        when(vehiculos.findAll()).thenReturn(List.of(vencido(null)));
        reclamoGana();

        assertEquals(1, servicio.enviarRecordatoriosPendientes());

        var orden = inOrder(mongo, email);
        orden.verify(mongo).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Vehiculo.class));
        orden.verify(email).enviarEmail(eq("ana@demo.com"), anyString(), anyString());
        // Un save() pisaría cambios del cliente (kilometraje, opt-out) hechos mientras corre el barrido
        verify(vehiculos, never()).save(any());
    }

    @Test
    void elReclamoExigeQueNadieHayaAvisadoEnLaVentanaYQueSigaActivo() {
        when(vehiculos.findAll()).thenReturn(List.of(vencido(null)));
        reclamoGana();

        servicio.enviarRecordatoriosPendientes();

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(q.capture(), u.capture(), any(FindAndModifyOptions.class), eq(Vehiculo.class));
        String filtro = q.getValue().getQueryObject().toString();
        assertTrue(filtro.contains("v1"), filtro);
        assertTrue(filtro.contains("ultimoRecordatorioEnviado") && filtro.contains("$lt"), filtro);
        assertTrue(filtro.contains("recordatoriosActivos") && filtro.contains("$ne"), filtro);
        assertTrue(u.getValue().getUpdateObject().toString().contains("ultimoRecordatorioEnviado"));
    }

    @Test
    void siOtroServidorYaReclamoNoSeEnvia() {
        when(vehiculos.findAll()).thenReturn(List.of(vencido(null)));
        // findAndModify sin coincidencia: el otro servidor ya marcó el envío

        assertEquals(0, servicio.enviarRecordatoriosPendientes());

        verifyNoInteractions(email);
    }

    @Test
    void dosServidoresALaVezEnvianUnSoloCorreo() {
        // Ambos leyeron el vehículo sin aviso; la base (simulada) deja reclamar una sola vez, como findAndModify
        when(vehiculos.findAll()).thenReturn(List.of(vencido(null)));
        AtomicBoolean reclamado = new AtomicBoolean(false);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Vehiculo.class)))
                .thenAnswer(inv -> reclamado.compareAndSet(false, true) ? vencido(LocalDateTime.now()) : null);

        int render = servicio.enviarRecordatoriosPendientes();
        int homelab = nuevoServicio(mongo).enviarRecordatoriosPendientes();

        assertEquals(1, render + homelab);
        verify(email, times(1)).enviarEmail(eq("ana@demo.com"), anyString(), anyString());
    }

    @Test
    void siElCorreoFallaSeLiberaLaMarcaParaReintentar() {
        when(vehiculos.findAll()).thenReturn(List.of(vencido(null)));
        reclamoGana();
        doThrow(new RuntimeException("Brevo caído")).when(email).enviarEmail(anyString(), anyString(), anyString());

        assertEquals(0, servicio.enviarRecordatoriosPendientes());

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(q.capture(), u.capture(), eq(Vehiculo.class));
        // Solo si la marca sigue siendo la nuestra; sin aviso previo, se quita el campo
        assertTrue(q.getValue().getQueryObject().toString().contains("ultimoRecordatorioEnviado"));
        assertTrue(u.getValue().getUpdateObject().toString().contains("$unset"));
    }

    @Test
    void siElCorreoFallaConAvisoPrevioSeRestauraLaFechaAnterior() {
        LocalDateTime hace40Dias = LocalDateTime.now().minusDays(40);
        when(vehiculos.findAll()).thenReturn(List.of(vencido(hace40Dias)));
        reclamoGana();
        doThrow(new RuntimeException("Brevo caído")).when(email).enviarEmail(anyString(), anyString(), anyString());

        servicio.enviarRecordatoriosPendientes();

        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), u.capture(), eq(Vehiculo.class));
        assertTrue(u.getValue().getUpdateObject().toString().contains("$set"));
    }

    @Test
    void conAvisoRecienteNoIntentaReclamar() {
        when(vehiculos.findAll()).thenReturn(List.of(vencido(LocalDateTime.now().minusDays(3))));

        assertEquals(0, servicio.enviarRecordatoriosPendientes());

        verifyNoInteractions(mongo, email);
    }

    @Test
    void sinVencerNoIntentaReclamar() {
        Vehiculo alDia = vencido(null);
        alDia.setKilometraje(7_000);
        when(vehiculos.findAll()).thenReturn(List.of(alDia));

        assertEquals(0, servicio.enviarRecordatoriosPendientes());

        verifyNoInteractions(mongo, email);
    }
}
