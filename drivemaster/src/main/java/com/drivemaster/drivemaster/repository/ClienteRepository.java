package com.drivemaster.drivemaster.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.Cliente;

@Repository
public interface ClienteRepository extends MongoRepository<Cliente, String> {
}

