package com.drivemaster.drivemaster.service;

import java.util.List;

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
        return proveedorRepository.save(proveedor);
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
        proveedor.setId(null); // siempre es uno nuevo: no se pisa un registro ajeno eligiendo su id
        proveedor.setTiendaId(exigir(tiendaId));
        return proveedorRepository.save(proveedor);
    }

    @Override
    public Proveedor actualizarEnTienda(String id, Proveedor datos, String tiendaId) {
        Proveedor actual = obtenerDeTienda(id, tiendaId);
        actual.setNombre(datos.getNombre());
        actual.setNit(datos.getNit());
        actual.setTelefono(datos.getTelefono());
        actual.setCorreo(datos.getCorreo());
        return proveedorRepository.save(actual); // el dueño no cambia aunque el cuerpo traiga otro tiendaId
    }

    @Override
    public void eliminarDeTienda(String id, String tiendaId) {
        proveedorRepository.delete(obtenerDeTienda(id, tiendaId));
    }

    private static String exigir(String tiendaId) {
        if (tiendaId == null || tiendaId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta la tienda dueña");
        }
        return tiendaId;
    }
}
