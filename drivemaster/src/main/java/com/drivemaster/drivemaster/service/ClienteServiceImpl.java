package com.drivemaster.drivemaster.service;


import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Cliente;
import com.drivemaster.drivemaster.repository.ClienteRepository;

import java.util.List;

@Service
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteServiceImpl(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Cliente crearCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    @Override
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Override
    public Cliente obtenerPorId(String id) {
        return clienteRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Cliente no encontrado"));
    }
    
    @Override
public Cliente actualizarCliente(String id, Cliente cliente) {
    cliente.setId(id);
    return clienteRepository.save(cliente);
}

@Override
public void eliminarCliente(String id) {
    clienteRepository.deleteById(id);
}
}

