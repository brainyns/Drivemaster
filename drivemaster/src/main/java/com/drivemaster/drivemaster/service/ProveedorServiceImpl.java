package com.drivemaster.drivemaster.service;



import org.springframework.stereotype.Service;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.repository.ProveedorRepository;
import java.util.List;

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

}
