package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.catalogo.FiltrosCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.PaginaCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.ProductoCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.TiendaCatalogoDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.util.CompatibilidadVehiculo;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Catálogo marketplace: los productos de TODAS las tiendas visibles, con su tienda, ciudad
 * y precio. Una tienda es visible si está aprobada, activa y tiene dirección, ciudad y
 * horario; las demás (incluidas las anteriores a la 4-A sin esos datos, marcadas "completar
 * datos") no muestran sus productos hasta completarlos. DriveMaster es la aplicación, no una
 * tienda: todos los productos son de una tienda y se compran con el carrito del catálogo.
 *
 * El mismo repuesto en varias tiendas se puede comparar (detalle → otrasTiendas). La
 * compatibilidad con el vehículo del cliente solo avisa y ordena: nunca oculta productos.
 * Es público (sin sesión): solo expone datos comerciales, nunca precios de compra,
 * proveedores ni datos administrativos de la tienda.
 */
@Service
public class CatalogoService {

    static final int MAX_COINCIDENCIAS = 2000;
    static final int TAMANIO_MAX = 60;
    static final int TAMANIO_DEFECTO = 24;
    static final int STOCK_VISIBLE_MAX = 20;
    private static final int MIN_TEXTO = 2;

    private final MongoTemplate mongoTemplate;
    private final TiendaRepository tiendaRepository;
    private final VehiculoService vehiculoService;

    public CatalogoService(MongoTemplate mongoTemplate, TiendaRepository tiendaRepository,
                           VehiculoService vehiculoService) {
        this.mongoTemplate = mongoTemplate;
        this.tiendaRepository = tiendaRepository;
        this.vehiculoService = vehiculoService;
    }

    // ─── Tiendas visibles ────────────────────────────────────────────────────

    /** Aprobada, activa y con dirección, ciudad y horario. */
    public static boolean tiendaVisible(Tienda t) {
        return t != null
                && EstadoAprobacion.estaAprobado(t.getEstadoAprobacion())
                && Boolean.TRUE.equals(t.getActivo())
                && t.tieneDatosCompletos();
    }

    public Map<String, Tienda> tiendasVisibles() {
        return tiendaRepository.findAll().stream().filter(CatalogoService::tiendaVisible)
                .collect(Collectors.toMap(Tienda::getId, t -> t, (a, b) -> a, LinkedHashMap::new));
    }

    // ─── Consultas públicas ──────────────────────────────────────────────────

    public PaginaCatalogoDTO buscar(String texto, String ciudad, String tiendaId, String categoria,
                                    String vehiculoId, String correo, String orden, Integer pagina, Integer tamanio) {
        Map<String, Tienda> tiendas = tiendasVisibles();
        if (ciudad != null && !ciudad.isBlank()) {
            String c = normalizar(ciudad);
            tiendas.values().removeIf(t -> !c.equals(normalizar(t.getCiudad())));
        }
        if (tiendaId != null && !tiendaId.isBlank()) {
            tiendas.keySet().removeIf(id -> !id.equals(tiendaId));
        }
        int tam = tamanio == null || tamanio < 1 ? TAMANIO_DEFECTO : Math.min(tamanio, TAMANIO_MAX);
        int pag = pagina == null || pagina < 0 ? 0 : pagina;
        if (tiendas.isEmpty()) return new PaginaCatalogoDTO(List.of(), 0, pag, tam);

        Vehiculo vehiculo = resolverVehiculo(correo, vehiculoId);
        List<Criteria> filtros = new ArrayList<>();
        filtros.add(Criteria.where("tiendaId").in(tiendas.keySet()));
        // Los productos creados desde el panel no guardan "activo": null cuenta como activo.
        filtros.add(Criteria.where("activo").ne(false));
        String t = texto != null ? texto.trim() : "";
        if (t.length() >= MIN_TEXTO) {
            Pattern p = Pattern.compile(Pattern.quote(t), Pattern.CASE_INSENSITIVE);
            filtros.add(new Criteria().orOperator(
                    Criteria.where("nombre").regex(p), Criteria.where("categoria").regex(p),
                    Criteria.where("marca").regex(p), Criteria.where("referencia").regex(p),
                    Criteria.where("codigo").regex(p)));
        }
        if (categoria != null && !categoria.isBlank()) {
            filtros.add(Criteria.where("categoria")
                    .regex(Pattern.compile("^" + Pattern.quote(categoria.trim()) + "$", Pattern.CASE_INSENSITIVE)));
        }
        Query query = Query.query(new Criteria().andOperator(filtros.toArray(new Criteria[0]))).limit(MAX_COINCIDENCIAS);

        List<ProductoCatalogoDTO> todos = mongoTemplate.find(query, Producto.class).stream()
                .map(p -> aDTO(p, tiendas.get(p.getTiendaId()), vehiculo))
                .sorted(comparador(orden, vehiculo != null))
                .toList();
        int desde = Math.min(pag * tam, todos.size());
        int hasta = Math.min(desde + tam, todos.size());
        return new PaginaCatalogoDTO(todos.subList(desde, hasta), todos.size(), pag, tam);
    }

    public ProductoCatalogoDTO detalle(String id, String vehiculoId, String correo) {
        Map<String, Tienda> tiendas = tiendasVisibles();
        Producto producto = mongoTemplate.findById(id, Producto.class);
        if (producto == null || Boolean.FALSE.equals(producto.getActivo()) || !tiendas.containsKey(producto.getTiendaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        }
        Vehiculo vehiculo = resolverVehiculo(correo, vehiculoId);
        ProductoCatalogoDTO dto = aDTO(producto, tiendas.get(producto.getTiendaId()), vehiculo);

        List<ProductoCatalogoDTO> otras = candidatosComparables(producto, tiendas).stream()
                .filter(o -> !o.getId().equals(producto.getId()))
                .filter(o -> claveComparable(o).equals(claveComparable(producto)))
                .map(o -> aDTO(o, tiendas.get(o.getTiendaId()), vehiculo))
                .sorted(Comparator.comparing(ProductoCatalogoDTO::precio, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return dto.conOtrasTiendas(otras);
    }

    public FiltrosCatalogoDTO filtros() {
        Map<String, Tienda> tiendas = tiendasVisibles();
        TreeSet<String> ciudades = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        tiendas.values().forEach(t -> ciudades.add(t.getCiudad().trim()));
        List<TiendaCatalogoDTO> lista = tiendas.values().stream().map(CatalogoService::aTiendaDTO)
                .sorted(Comparator.comparing(TiendaCatalogoDTO::nombre, String.CASE_INSENSITIVE_ORDER)).toList();
        List<String> categorias = tiendas.isEmpty() ? List.of() : mongoTemplate.findDistinct(
                        Query.query(new Criteria().andOperator(Criteria.where("tiendaId").in(tiendas.keySet()),
                                Criteria.where("activo").ne(false))), "categoria", Producto.class, String.class)
                .stream().filter(c -> c != null && !c.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new FiltrosCatalogoDTO(new ArrayList<>(ciudades), lista, categorias);
    }

    private List<Producto> candidatosComparables(Producto base, Map<String, Tienda> tiendas) {
        List<Criteria> filtros = new ArrayList<>();
        filtros.add(Criteria.where("tiendaId").in(tiendas.keySet()));
        filtros.add(Criteria.where("activo").ne(false));
        if (noVacio(base.getReferencia())) {
            filtros.add(Criteria.where("referencia").regex(
                    Pattern.compile("^" + Pattern.quote(base.getReferencia().trim()) + "$", Pattern.CASE_INSENSITIVE)));
        } else {
            filtros.add(Criteria.where("nombre").regex(
                    Pattern.compile("^" + Pattern.quote(base.getNombre().trim()) + "$", Pattern.CASE_INSENSITIVE)));
        }
        return mongoTemplate.find(Query.query(new Criteria().andOperator(filtros.toArray(new Criteria[0]))).limit(100),
                Producto.class);
    }

    /** Solo se usa el vehículo si es del cliente con sesión; sin sesión se ignora (no es un error). */
    private Vehiculo resolverVehiculo(String correo, String vehiculoId) {
        if (correo == null || vehiculoId == null || vehiculoId.isBlank()) return null;
        vehiculoService.obtenerDeUsuario(correo, vehiculoId); // 403/404 si no es suyo
        return vehiculoService.obtenerEntidad(vehiculoId);
    }

    // ─── Conversión y orden (sin acceso a datos: probadas en unitarias) ──────

    public static ProductoCatalogoDTO aDTO(Producto p, Tienda tienda, Vehiculo vehiculo) {
        boolean encargo = "ENCARGO".equals(p.getTipo());
        int stock = p.getStockActual() != null ? p.getStockActual() : 0;
        String compatibilidad = null;
        String aviso = null;
        if (vehiculo != null) {
            compatibilidad = CompatibilidadVehiculo.estado(p, vehiculo);
            if (CompatibilidadVehiculo.NO_COMPATIBLE.equals(compatibilidad)) {
                aviso = "No es compatible con " + CompatibilidadVehiculo.describir(vehiculo)
                        + ". Puedes comprarlo, pero no le servirá a ese vehículo.";
            } else if (CompatibilidadVehiculo.POR_CONFIRMAR.equals(compatibilidad)) {
                aviso = "La tienda no declaró compatibilidades: confirma que le sirve a "
                        + CompatibilidadVehiculo.describir(vehiculo) + " antes de comprar.";
            }
        }
        return new ProductoCatalogoDTO(p.getId(), p.getNombre(), p.getMarca(), p.getCategoria(), p.getReferencia(),
                p.getImagenUrl(), p.getPrecioVenta(), encargo ? "ENCARGO" : "STOCK", encargo || stock > 0,
                encargo ? null : Math.min(stock, STOCK_VISIBLE_MAX), encargo ? p.getDiasEntregaEncargo() : null,
                compatibilidad, aviso, aTiendaDTO(tienda), null);
    }

    public static TiendaCatalogoDTO aTiendaDTO(Tienda t) {
        return new TiendaCatalogoDTO(t.getId(), t.getNombre(), t.getCiudad(), t.getBarrio(), t.getDireccion(),
                t.getHorario(), t.getCostoEnvio() != null, t.getCostoEnvio());
    }

    /** Mismo repuesto = misma referencia; sin referencia, mismo nombre. Sin distinguir mayúsculas ni tildes. */
    static String claveComparable(Producto p) {
        return noVacio(p.getReferencia()) ? "ref:" + normalizar(p.getReferencia()) : "nom:" + normalizar(p.getNombre());
    }

    /**
     * Con vehículo y orden por defecto: compatibles primero, luego "por confirmar", luego los no
     * compatibles (al final, pero visibles); dentro de cada grupo, disponibles primero y más barato.
     */
    static Comparator<ProductoCatalogoDTO> comparador(String orden, boolean conVehiculo) {
        Comparator<ProductoCatalogoDTO> porPrecio = Comparator.comparing(ProductoCatalogoDTO::precio,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<ProductoCatalogoDTO> porNombre = Comparator.comparing(
                (ProductoCatalogoDTO d) -> normalizar(d.nombre())).thenComparing(porPrecio);
        String o = orden != null ? orden.trim().toLowerCase(Locale.ROOT) : "";
        switch (o) {
            case "precio_asc":
                return porPrecio.thenComparing(porNombre);
            case "precio_desc":
                return porPrecio.reversed().thenComparing(porNombre);
            case "nombre":
                return porNombre;
            default:
                Comparator<ProductoCatalogoDTO> disponibles = Comparator.comparing((ProductoCatalogoDTO d) -> d.disponible() ? 0 : 1);
                Comparator<ProductoCatalogoDTO> base = disponibles.thenComparing(porPrecio).thenComparing(porNombre);
                if (!conVehiculo) return base;
                return Comparator.comparing((ProductoCatalogoDTO d) -> rangoCompatibilidad(d.compatibilidad())).thenComparing(base);
        }
    }

    private static int rangoCompatibilidad(String c) {
        if (CompatibilidadVehiculo.COMPATIBLE.equals(c)) return 0;
        if (CompatibilidadVehiculo.POR_CONFIRMAR.equals(c)) return 1;
        return 2;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }

}
