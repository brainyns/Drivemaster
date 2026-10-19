package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Tienda;
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
 * Logo de la tienda, con el mismo mecanismo que el del taller (LogoTallerService): la dueña solo cambia el de su
 * tienda (se busca por la cuenta de la sesión, nunca por un id del pedido) y el administrador lo cambia por id.
 * ImagenSubida valida el archivo, CloudinaryService lo guarda y solo se escribe el campo logoUrl.
 */
@Service
public class LogoTiendaService {

    private final TiendaService tiendaService;
    private final CloudinaryService cloudinary;
    private final MongoTemplate mongo;

    public LogoTiendaService(TiendaService tiendaService, CloudinaryService cloudinary, MongoTemplate mongo) {
        this.tiendaService = tiendaService;
        this.cloudinary = cloudinary;
        this.mongo = mongo;
    }

    /** La tienda de la sesión sube su logo. */
    public String subirMio(String correoTienda, MultipartFile archivo) {
        ImagenSubida.validar(archivo);
        return guardar(tiendaService.obtenerPorUsuario(correoTienda), archivo);
    }

    /** El administrador cambia el logo de cualquier tienda. */
    public String subirPorAdmin(String tiendaId, MultipartFile archivo) {
        ImagenSubida.validar(archivo);
        return guardar(tiendaService.obtenerEntidad(tiendaId), archivo);
    }

    private String guardar(Tienda tienda, MultipartFile archivo) {
        String url;
        try {
            url = cloudinary.subirImagen(archivo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo subir la imagen. Inténtalo de nuevo.");
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(tienda.getId())), new Update().set("logoUrl", url), Tienda.class);
        return url;
    }
}
