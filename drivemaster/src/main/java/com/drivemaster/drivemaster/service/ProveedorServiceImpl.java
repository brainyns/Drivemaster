package com.drivemaster.drivemaster.service;

import java.util.List;

import java.util.Map;
import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.util.ValidacionProveedor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.repository.ProveedorRepository;

@Service
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public ProveedorServiceImpl(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @Override
    public Proveedor crear(Proveedor proveedor) {
        return proveedorRepository.save(ValidacionProveedor.validar(proveedor));
    }

    @Override
    public List<Proveedor> listarTodos() {
        return proveedorRepository.findAll();
    }

    @Override
    public Proveedor obtenerPorId(String id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
    }

    @Override
    public void eliminarProveedor(String id) {
        proveedorRepository.deleteById(id);
    }

    // ─── Por tienda: el dueño se filtra aquí, no en la pantalla ──────────────

    @Override
    public List<Proveedor> listarDeTienda(String tiendaId) {
        return proveedorRepository.findByTiendaId(exigir(tiendaId));
    }

    @Override
    public Proveedor obtenerDeTienda(String id, String tiendaId) {
        String dueno = exigir(tiendaId);
        return proveedorRepository.findById(id)
                .filter(p -> dueno.equals(p.getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    @Override
    public Proveedor crearEnTienda(Proveedor proveedor, String tiendaId) {
        String dueno = exigir(tiendaId);
        ValidacionProveedor.validar(proveedor);
        exigirNitLibre(dueno, proveedor.getNit(), null);
        proveedor.setId(null); // siempre es uno nuevo: no se pisa un registro ajeno eligiendo su id
        proveedor.setTiendaId(dueno);
        return proveedorRepository.save(proveedor);
    }

    @Override
    public Proveedor actualizarEnTienda(String id, Proveedor datos, String tiendaId) {
        Proveedor actual = obtenerDeTienda(id, tiendaId);
        ValidacionProveedor.validar(datos);
        exigirNitLibre(actual.getTiendaId(), datos.getNit(), actual.getId());
        actual.setNombre(datos.getNombre());
        actual.setNit(datos.getNit());
        actual.setTelefono(datos.getTelefono());
        actual.setCorreo(datos.getCorreo());
        actual.setEnviarPdfCompras(datos.getEnviarPdfCompras());
        actual.setContacto(datos.getContacto());
        actual.setDireccion(datos.getDireccion());
        actual.setCondicionPago(datos.getCondicionPago());
        actual.setDiasCredito(datos.getDiasCredito());
        return proveedorRepository.save(actual); // el dueño no cambia aunque el cuerpo traiga otro tiendaId
    }

    @Override
    public void eliminarDeTienda(String id, String tiendaId) {
        proveedorRepository.delete(obtenerDeTienda(id, tiendaId));
    }

    /**
     * Dos proveedores de la misma tienda no pueden tener el mismo NIT. Se comparan solo los dígitos del número base
     * (sin el dígito de verificación ni puntos o espacios): "900.123.456-7" y "900123456" son el mismo. El nombre
     * repetido no se bloquea (lo advierte el formulario): dos sucursales pueden llamarse igual.
     */
    private void exigirNitLibre(String tiendaId, String nit, String excluirId) {
        String base = nitBase(nit);
        if (base == null) return;
        boolean repetido = proveedorRepository.findByTiendaId(tiendaId).stream()
                .anyMatch(p -> !p.getId().equals(excluirId) && base.equals(nitBase(p.getNit())));
        if (repetido) {
            throw new DatosInvalidosException(Map.of("nit", "Ya tienes un proveedor con este NIT."));
        }
    }

    /** Dígitos del NIT antes del guion (el dígito de verificación va después); null si no hay NIT. */
    static String nitBase(String nit) {
        if (nit == null) return null;
        String numero = nit.contains("-") ? nit.substring(0, nit.indexOf('-')) : nit;
        String digitos = numero.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private static String exigir(String tiendaId) {
        if (tiendaId == null || tiendaId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta la tienda dueña");
        }
        return tiendaId;
    }
}
