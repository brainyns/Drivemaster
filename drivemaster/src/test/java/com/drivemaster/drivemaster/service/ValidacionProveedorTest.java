package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.repository.ProveedorRepository;
import com.drivemaster.drivemaster.util.ValidacionProveedor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El backend valida el proveedor (nombre obligatorio, correo con formato) y responde el error de cada campo. */
class ValidacionProveedorTest {

    private static Proveedor proveedor(String nombre, String nit, String telefono, String correo) {
        Proveedor p = new Proveedor();
        p.setNombre(nombre);
        p.setNit(nit);
        p.setTelefono(telefono);
        p.setCorreo(correo);
        return p;
    }

    @Test
    void unProveedorBienEscritoPasaLimpioYConLoVacioEnNull() {
        Proveedor p = ValidacionProveedor.validar(proveedor("  Autopartes   Sur ", " ", "+57 300 123 4567", " ventas@sur.com "));
        assertEquals("Autopartes Sur", p.getNombre());
        assertNull(p.getNit());
        assertEquals("ventas@sur.com", p.getCorreo());
    }

    @Test
    void elNombreEsObligatorioYDeLargoRazonable() {
        assertEquals("Escribe el nombre de la empresa.",
                assertThrows(DatosInvalidosException.class, () -> ValidacionProveedor.validar(proveedor("  ", null, null, null)))
                        .getCampos().get("nombre"));
        assertEquals("El nombre debe tener entre 2 y 120 caracteres.",
                assertThrows(DatosInvalidosException.class, () -> ValidacionProveedor.validar(proveedor("A", null, null, null)))
                        .getCampos().get("nombre"));
        assertThrows(DatosInvalidosException.class, () -> ValidacionProveedor.validar(proveedor("x".repeat(121), null, null, null)));
    }

    @Test
    void elCorreoEsOpcionalPeroSiVieneDebeTenerFormato() {
        ValidacionProveedor.validar(proveedor("Autopartes Sur", null, null, null));
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> ValidacionProveedor.validar(proveedor("Autopartes Sur", null, "abc", "ventas@")));
        assertEquals(List.of("correo", "telefono"), List.copyOf(e.getCampos().keySet()), "cada campo con su error");
        assertEquals("Revisa los datos marcados", e.getMessage());
    }

    @Test
    void elServicioNoGuardaUnProveedorInvalidoNiAlCrearNiAlEditar() {
        ProveedorRepository repo = mock(ProveedorRepository.class);
        ProveedorServiceImpl servicio = new ProveedorServiceImpl(repo);
        Proveedor existente = proveedor("Viejo", null, null, null);
        existente.setId("p1");
        existente.setTiendaId("A");
        when(repo.findById("p1")).thenReturn(Optional.of(existente));

        assertThrows(DatosInvalidosException.class, () -> servicio.crearEnTienda(proveedor("", null, null, null), "A"));
        assertThrows(DatosInvalidosException.class, () -> servicio.actualizarEnTienda("p1", proveedor("Nuevo", null, null, "no-es-correo"), "A"));
        verify(repo, never()).save(any());
        assertEquals("Viejo", existente.getNombre(), "el registro guardado no cambia");
    }

    @Test
    void lasCondicionesDePagoSonOpcionalesPeroElCreditoLlevaPlazo() {
        Proveedor contado = proveedor("Alfa", null, null, null);
        contado.setCondicionPago("CONTADO");
        contado.setDiasCredito(30);
        contado.setContacto("  Laura Gómez ");
        assertNull(ValidacionProveedor.validar(contado).getDiasCredito(), "de contado no hay plazo");
        assertEquals("Laura Gómez", contado.getContacto());

        Proveedor sinPlazo = proveedor("Alfa", null, null, null);
        sinPlazo.setCondicionPago("CREDITO");
        assertEquals("Escribe el plazo del crédito: entre 1 y 180 días.",
                assertThrows(DatosInvalidosException.class, () -> ValidacionProveedor.validar(sinPlazo)).getCampos().get("diasCredito"));

        Proveedor credito = proveedor("Alfa", null, null, null);
        credito.setCondicionPago("CREDITO");
        credito.setDiasCredito(30);
        assertEquals(30, ValidacionProveedor.validar(credito).getDiasCredito());

        Proveedor otra = proveedor("Alfa", null, null, null);
        otra.setCondicionPago("TRUEQUE");
        assertThrows(DatosInvalidosException.class, () -> ValidacionProveedor.validar(otra));
    }
}
