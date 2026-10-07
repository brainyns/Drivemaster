package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de demostración para el módulo "Servicios y rol Taller":
 * un cliente demo con 4 vehículos, 3 talleres (con su usuario de login)
 * y repuestos compatibles con stock para el vehículo principal de la demo
 * (Chevrolet Spark GT 2018).
 */
// Solo en desarrollo: crea usuarios con contraseñas conocidas. Se activa con
// DEMO_SEED_ENABLED=true; en producción la variable no existe y no se ejecuta.
@ConditionalOnProperty(name = "drivemaster.demo-seed.enabled", havingValue = "true")
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class ServicioTallerSeeder implements CommandLineRunner {

    private static final String CLIENTE_DEMO_CORREO = "cliente.demo@drivemaster.com";
    private static final String CLIENTE_DEMO_PASSWORD = "Demo1234*";
    private static final String TALLER_PASSWORD = "Taller1234*";

    private final UsuarioRepository usuarioRepository;
    private final VehiculoRepository vehiculoRepository;
    private final TallerRepository tallerRepository;
    private final ProductoRepository productoRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Usuario clienteDemo = crearClienteDemo();
        crearVehiculosDemo(clienteDemo);
        crearTalleresDemo();
        crearProductosDemo();
        log.info("✅ Datos de demo del módulo de servicios (talleres) verificados/creados");
    }

    private Usuario crearClienteDemo() {
        return usuarioRepository.findByCorreo(CLIENTE_DEMO_CORREO).orElseGet(() -> {
            Usuario usuario = Usuario.builder()
                    .nombre("Cliente Demo")
                    .correo(CLIENTE_DEMO_CORREO)
                    .password(passwordEncoder.encode(CLIENTE_DEMO_PASSWORD))
                    .proveedor("LOCAL")
                    .rol("CLIENTE")
                    .permisos(List.of("ROLE_CLIENTE", "PRODUCTOS_READ"))
                    .activo(true)
                    .datosCompletos(true)
                    .ciudad("Bogotá D.C.")
                    .fechaCreacion(Instant.now())
                    .intentosFallidos(0)
                    .bloqueado(false)
                    .build();
            usuario = usuarioRepository.save(usuario);
            log.info("➕ Usuario cliente demo creado: {}", CLIENTE_DEMO_CORREO);
            return usuario;
        });
    }

    private void crearVehiculosDemo(Usuario clienteDemo) {
        if (!vehiculoRepository.findByUsuarioId(clienteDemo.getId()).isEmpty()) {
            return;
        }

        List<Vehiculo> vehiculos = List.of(
                Vehiculo.builder().usuarioId(clienteDemo.getId())
                        .marca("Chevrolet").modelo("Spark GT").anio(2018)
                        .motor("1.2L").combustible("Gasolina")
                        .placa("DEM001").kilometraje(45000).build(),
                Vehiculo.builder().usuarioId(clienteDemo.getId())
                        .marca("Mazda").modelo("3").anio(2019)
                        .motor("2.0L").combustible("Gasolina")
                        .placa("DEM002").kilometraje(32000).build(),
                Vehiculo.builder().usuarioId(clienteDemo.getId())
                        .marca("Renault").modelo("Logan").anio(2020)
                        .motor("1.6L").combustible("Gasolina")
                        .placa("DEM003").kilometraje(20000).build(),
                Vehiculo.builder().usuarioId(clienteDemo.getId())
                        .marca("Chevrolet").modelo("Onix").anio(2021)
                        .motor("1.0L Turbo").combustible("Gasolina")
                        .placa("DEM004").kilometraje(15000).build()
        );

        vehiculoRepository.saveAll(vehiculos);
        log.info("➕ {} vehículos de demo creados para el cliente demo", vehiculos.size());
    }

    private void crearTalleresDemo() {
        crearTallerConUsuario("AutoMax", "automax@drivemaster.com",
                "Bogotá D.C.", "Cra 15 #45-20", 4.6684, -74.0553,
                List.of("Frenos", "Suspensión"), 4.8, 80000.0);

        crearTallerConUsuario("Express Motor", "expressmotor@drivemaster.com",
                "Bogotá D.C.", "Av. Boyacá #60-10", 4.6789, -74.0870,
                List.of("Frenos", "Motor"), 4.5, 65000.0);

        crearTallerConUsuario("Taller Central", "tallercentral@drivemaster.com",
                "Bogotá D.C.", "Calle 100 #19-40", 4.6910, -74.0450,
                List.of("Frenos", "Eléctrico"), 4.7, 70000.0);
    }

    private void crearTallerConUsuario(String nombre, String correo, String ciudad, String direccion,
                                        double lat, double lng, List<String> especialidades,
                                        double rating, double precioManoObra) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario u = Usuario.builder()
                    .nombre(nombre)
                    .correo(correo)
                    .password(passwordEncoder.encode(TALLER_PASSWORD))
                    .proveedor("LOCAL")
                    .rol("TALLER")
                    .permisos(List.of("TALLER_ORDENES_READ", "TALLER_ORDENES_WRITE"))
                    .activo(true)
                    .datosCompletos(true)
                    .ciudad(ciudad)
                    .fechaCreacion(Instant.now())
                    .intentosFallidos(0)
                    .bloqueado(false)
                    .build();
            u = usuarioRepository.save(u);
            log.info("➕ Usuario de taller creado: {}", correo);
            return u;
        });

        if (tallerRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            return;
        }

        Taller taller = Taller.builder()
                .usuarioId(usuario.getId())
                .nombre(nombre)
                .ciudad(ciudad)
                .direccion(direccion)
                .latitud(lat)
                .longitud(lng)
                .especialidades(new ArrayList<>(especialidades))
                .rating(rating)
                .precioManoObra(precioManoObra)
                .activo(true)
                .build();
        tallerRepository.save(taller);
        log.info("➕ Taller de demo creado: {}", nombre);
    }

    private void crearProductosDemo() {
        // Compatibilidad principal de la demo: Chevrolet Spark GT 2015-2020
        Compatibilidad compatSparkGT = new Compatibilidad("Chevrolet", "Spark GT", 2015, 2020);

        crearProductoSiNoExiste("PST-BOSCH-01", "Pastillas de freno Bosch", "Frenos", "Bosch",
                85000.0, 120000.0, 20, compatSparkGT);
        crearProductoSiNoExiste("PST-BREMBO-01", "Pastillas de freno Brembo", "Frenos", "Brembo",
                110000.0, 160000.0, 15, compatSparkGT);
        crearProductoSiNoExiste("PST-GEN-01", "Pastillas de freno Genéricas", "Frenos", "Genérica",
                45000.0, 70000.0, 30, compatSparkGT);
    }

    private void crearProductoSiNoExiste(String codigo, String nombre, String categoria, String marca,
                                          double precioCompra, double precioVenta, int stock,
                                          Compatibilidad compatibilidad) {
        if (productoRepository.existsByCodigo(codigo)) {
            return;
        }

        Producto producto = new Producto();
        producto.setCodigo(codigo);
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        producto.setMarca(marca);
        producto.setModelosCompatibles(new ArrayList<>(List.of(compatibilidad)));
        producto.setPrecioCompra(precioCompra);
        producto.setPrecioVenta(precioVenta);
        producto.setStockActual(stock);
        producto.setStockMinimo(3);
        producto.setTipo("STOCK");
        producto.setActivo(true);

        productoRepository.save(producto);
        log.info("➕ Producto de demo creado: {}", nombre);
    }
}