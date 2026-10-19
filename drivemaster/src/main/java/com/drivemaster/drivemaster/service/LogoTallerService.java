package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.util.ImagenSubida;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Logo o foto del taller. El dueño solo cambia el de su taller (se busca por la cuenta de la sesión, nunca por un id
 * que venga en el pedido); el administrador lo cambia por id. Se guarda con el mismo mecanismo que la foto del
 * vehículo (CloudinaryService) y solo se escribe el campo logoUrl.
 */
@Service
public class LogoTallerService {

    private final TallerService tallerService;
    private final CloudinaryService cloudinary;
    private final MongoTemplate mongo;

    public LogoTallerService(TallerService tallerService, CloudinaryService cloudinary, MongoTemplate mongo) {
        this.tallerService = tallerService;
        this.cloudinary = cloudinary;
        this.mongo = mongo;
    }

    /** El taller de la sesión sube su logo. */
    public String subirMio(String correoTaller, MultipartFile archivo) {
        ImagenSubida.validar(archivo);
        return guardar(tallerService.obtenerPorUsuario(correoTaller), archivo);
    }

    /** El administrador cambia el logo de cualquier taller. */
    public String subirPorAdmin(String tallerId, MultipartFile archivo) {
        ImagenSubida.validar(archivo);
        return guardar(tallerService.obtenerEntidad(tallerId), archivo);
    }

    private String guardar(Taller taller, MultipartFile archivo) {
        String url;
        try {
            url = cloudinary.subirImagen(archivo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo subir la imagen. Inténtalo de nuevo.");
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(taller.getId())), new Update().set("logoUrl", url), Taller.class);
        return url;
    }
}
