package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Cliente;

public interface ClienteService {

    Cliente crearCliente(Cliente cliente);

    List<Cliente> listarTodos();

    Cliente obtenerPorId(String id);

    Cliente actualizarCliente(String id, Cliente cliente);

    void eliminarCliente(String id);
}