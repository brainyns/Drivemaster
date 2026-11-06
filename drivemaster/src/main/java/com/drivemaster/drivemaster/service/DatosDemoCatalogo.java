package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.util.NitColombia;

import java.util.List;
import java.util.Map;

/**
 * Los datos fijos de la demostración de la feria: tiendas, productos, clientes y talleres. Todo es inventado salvo los
 * correos, que usan el alias "+" de Gmail para que lleguen a la bandeja del dueño. Ver DatosDemoService.
 */
final class DatosDemoCatalogo {

    private DatosDemoCatalogo() {
    }

    /** Prefijo de todos los _id de Mongo de la demo: lo que lo tiene es demo y lo que no, no se toca nunca. */
    static final String PREFIJO = "demo-";
    /** Prefijo de las referencias de pago (Postgres) y de las liquidaciones simuladas. */
    static final String REF_PAGO = "DEMO-";
    static final String REF_LIQUIDACION = "SIM-DEMO-";
    /** La misma contraseña para todas las cuentas demo, guardada con BCrypt como las demás. */
    static final String PASSWORD = "Feria2026*";

    // ─── Tiendas ─────────────────────────────────────────────────────────────

    record TiendaDemo(String clave, String nombre, String correo, String nitNumero, String ciudad, String barrio,
                      String direccion, double latitud, double longitud, String horario, Double costoEnvio, String telefono,
                      String banco, String bancoCodigo, String cuenta, double factorPrecio) {
        String id() {
            return PREFIJO + "tienda-" + clave;
        }

        String usuarioId() {
            return PREFIJO + "u-tienda-" + clave;
        }

        String nit() {
            return nitNumero + "-" + NitColombia.digitoVerificacion(nitNumero);
        }
    }

    static final String BOCAGRANDE = "bocagrande";
    static final String EL_BOSQUE = "elbosque";
    static final String MANGA = "manga";
    static final String CRESPO = "crespo";
    static final String PRADO = "prado";

    static final List<TiendaDemo> TIENDAS = List.of(
            new TiendaDemo(BOCAGRANDE, "Frenos y Suspensión Bocagrande", "brainyblancoh+frenos.bocagrande@gmail.com",
                    "901457231", "Cartagena", "Bocagrande", "Av. San Martín # 7-45", 10.4006, -75.5528,
                    "Lunes a sábado 8:00 a. m. - 6:00 p. m.", 8000.0, "3014567821", "Bancolombia", "1007", "65812345670", 1.05),
            new TiendaDemo(EL_BOSQUE, "Repuestos El Bosque", "brainyblancoh+repuestos.elbosque@gmail.com",
                    "901457248", "Cartagena", "El Bosque", "Transversal 54 # 21A-30", 10.3953, -75.5166,
                    "Lunes a sábado 7:30 a. m. - 6:30 p. m.", 7000.0, "3156789012", "Davivienda", "1051", "457812349011", 0.97),
            new TiendaDemo(MANGA, "Autopartes Manga", "brainyblancoh+autopartes.manga@gmail.com",
                    "901457256", "Cartagena", "Manga", "Calle Real de Manga # 24-15", 10.4144, -75.5386,
                    "Lunes a viernes 8:00 a. m. - 5:30 p. m., sábados 8:00 a. m. - 1:00 p. m.", 6000.0, "3002345678",
                    "Banco de Bogotá", "1001", "009876543", 1.00),
            new TiendaDemo(CRESPO, "Lubricentro Crespo", "brainyblancoh+lubricentro.crespo@gmail.com",
                    "901457263", "Cartagena", "Crespo", "Carrera 6 # 70-22", 10.4467, -75.5167,
                    "Lunes a sábado 7:00 a. m. - 6:00 p. m.", 6000.0, "3205678934", "Bancolombia", "1007", "65898761230", 0.99),
            new TiendaDemo(PRADO, "Autopartes del Prado", "brainyblancoh+autopartes.delprado@gmail.com",
                    "901457270", "Barranquilla", "El Prado", "Carrera 54 # 64-120", 10.9970, -74.8020,
                    "Lunes a sábado 8:00 a. m. - 6:00 p. m.", 9000.0, "3017894561", "BBVA", "1013", "001302458812", 1.02));

    static TiendaDemo tienda(String clave) {
        return TIENDAS.stream().filter(t -> t.clave().equals(clave)).findFirst().orElseThrow();
    }

    // ─── Productos (plantillas: cada tienda vende las suyas con su propio precio) ─────

    record Compatible(String marca, String modelo, int desde, int hasta) {
    }

    /** Precio de venta sin IVA (el sistema le suma el 19 %) y de compra, en pesos, antes del factor de cada tienda. */
    record Plantilla(String clave, String nombre, String categoria, String marca, String referencia, double precioCompra,
                     double precioVenta, int stockMinimo, List<Compatible> compatibles) {
    }

    private static List<Compatible> c(Compatible... v) {
        return List.of(v);
    }

    private static final Compatible SPARK = new Compatible("Chevrolet", "Spark", 2010, 2020);
    private static final Compatible BEAT = new Compatible("Chevrolet", "Beat", 2018, 2023);
    private static final Compatible AVEO = new Compatible("Chevrolet", "Aveo", 2008, 2017);
    private static final Compatible ONIX = new Compatible("Chevrolet", "Onix", 2020, 2024);
    private static final Compatible LOGAN = new Compatible("Renault", "Logan", 2010, 2022);
    private static final Compatible SANDERO = new Compatible("Renault", "Sandero", 2012, 2022);
    private static final Compatible MAZDA3 = new Compatible("Mazda", "3", 2014, 2020);
    private static final Compatible PICANTO = new Compatible("Kia", "Picanto", 2012, 2022);
    private static final Compatible RIO = new Compatible("Kia", "Rio", 2015, 2022);
    private static final Compatible COROLLA = new Compatible("Toyota", "Corolla", 2014, 2021);
    private static final Compatible MARCH = new Compatible("Nissan", "March", 2012, 2020);

    static final String FRENOS = "Frenos";
    static final String SUSPENSION = "Suspension y Direccion";
    static final String ELECTRICO = "Sistema Electrico";
    static final String FILTROS = "Filtros";
    static final String LUBRICANTES = "Lubricantes y Fluidos";
    static final String BATERIAS = "Baterias y Carga";

    /** El producto estrella: se vende en todas las tiendas. */
    static final String ESTRELLA = "pastillas-bosch";
    /** Vende mucho en octubre y nada en noviembre. */
    static final String SE_DEJA_DE_VENDER = "aceite-20w50";

    static final List<Plantilla> PLANTILLAS = List.of(
            new Plantilla(ESTRELLA, "Pastillas de freno delanteras", FRENOS, "Bosch", "BP1234", 62000, 89000, 5, c(SPARK, BEAT, AVEO)),
            new Plantilla("pastillas-brembo", "Pastillas de freno delanteras cerámicas", FRENOS, "Brembo", "P30056", 120000, 168000, 3, c(MAZDA3)),
            new Plantilla("discos-brembo", "Disco de freno delantero (par)", FRENOS, "Brembo", "09.A913", 150000, 215000, 2, c(LOGAN, SANDERO)),
            new Plantilla("bandas-bosch", "Bandas de freno traseras", FRENOS, "Bosch", "BS0821", 45000, 68000, 4, c(LOGAN, SANDERO, MARCH)),
            new Plantilla("liquido-dot4", "Líquido de frenos DOT 4 500 ml", FRENOS, "Bosch", "DOT4-500", 14000, 22000, 6, List.of()),
            new Plantilla("amortiguador-monroe", "Amortiguador delantero", SUSPENSION, "Monroe", "72345", 135000, 195000, 2, c(SPARK, BEAT)),
            new Plantilla("amortiguador-kyb", "Amortiguador trasero", SUSPENSION, "KYB", "343456", 120000, 175000, 2, c(PICANTO, RIO)),
            new Plantilla("terminal-moog", "Terminal de dirección", SUSPENSION, "Moog", "ES3456", 38000, 59000, 4, c(LOGAN, SANDERO)),
            new Plantilla("rotula-moog", "Rótula de suspensión", SUSPENSION, "Moog", "K90345", 55000, 82000, 3, c(MAZDA3, COROLLA)),
            new Plantilla("sensor-oxigeno", "Sensor de oxígeno", ELECTRICO, "Bosch", "0258006", 140000, 205000, 2, c(SPARK, AVEO, ONIX)),
            new Plantilla("sensor-map", "Sensor MAP", ELECTRICO, "Delphi", "PS10", 95000, 140000, 2, c(LOGAN, SANDERO)),
            new Plantilla("bujias-iridio", "Bujías de iridio (juego x4)", ELECTRICO, "NGK", "ILZKR7", 110000, 165000, 3, c(MAZDA3, COROLLA, RIO)),
            new Plantilla("bujias-cobre", "Bujías de cobre (juego x4)", ELECTRICO, "NGK", "BKR6E", 26000, 42000, 5, c(SPARK, MARCH, LOGAN)),
            new Plantilla("bobina-bosch", "Bobina de encendido", ELECTRICO, "Bosch", "0221504", 150000, 220000, 2, c(PICANTO, RIO)),
            new Plantilla("filtro-aceite", "Filtro de aceite", FILTROS, "Mann", "W610/3", 12000, 19500, 8, c(SPARK, PICANTO, MARCH, ONIX)),
            new Plantilla("filtro-aire", "Filtro de aire", FILTROS, "Bosch", "F026400", 22000, 35000, 6, c(LOGAN, SANDERO)),
            new Plantilla("filtro-cabina", "Filtro de cabina", FILTROS, "Mann", "CU1919", 28000, 45000, 4, c(MAZDA3, COROLLA)),
            new Plantilla("filtro-combustible", "Filtro de combustible", FILTROS, "Bosch", "0450905", 25000, 39000, 4, c(SPARK, AVEO)),
            new Plantilla(SE_DEJA_DE_VENDER, "Aceite 20W-50 mineral 1/4 de galón", LUBRICANTES, "Mobil", "SUPER-1000", 22000, 33000, 10, List.of()),
            new Plantilla("aceite-10w30", "Aceite 10W-30 semisintético 1/4 de galón", LUBRICANTES, "Castrol", "GTX-10W30", 30000, 45000, 8, List.of()),
            new Plantilla("aceite-5w30", "Aceite 5W-30 sintético 1/4 de galón", LUBRICANTES, "Mobil 1", "M1-5W30", 48000, 69000, 6, List.of()),
            new Plantilla("refrigerante", "Refrigerante 50/50 galón", LUBRICANTES, "Prestone", "AF2100", 38000, 56000, 4, List.of()),
            new Plantilla("bateria-45", "Batería 12V 45Ah", BATERIAS, "MAC", "MAC-45", 280000, 390000, 2, c(SPARK, PICANTO, MARCH)),
            new Plantilla("bateria-60", "Batería 12V 60Ah", BATERIAS, "Willard", "W-60", 360000, 499000, 2, c(LOGAN, MAZDA3, COROLLA)),
            new Plantilla("alternador", "Alternador remanufacturado", BATERIAS, "Bosch", "AL-0986", 420000, 590000, 1, c(LOGAN, SANDERO)));

    static Plantilla plantilla(String clave) {
        return PLANTILLAS.stream().filter(p -> p.clave().equals(clave)).findFirst().orElseThrow();
    }

    /** Qué vende cada tienda (de 18 a 21 productos; varios se repiten entre tiendas con precios distintos). */
    static final Map<String, List<String>> SURTIDO = Map.of(
            BOCAGRANDE, List.of(ESTRELLA, "pastillas-brembo", "discos-brembo", "bandas-bosch", "liquido-dot4", "amortiguador-monroe",
                    "amortiguador-kyb", "terminal-moog", "rotula-moog", "sensor-oxigeno", "bujias-iridio", "bobina-bosch",
                    "filtro-aceite", "filtro-aire", "filtro-cabina", "aceite-10w30", "aceite-5w30", "bateria-45", "bateria-60", "refrigerante"),
            EL_BOSQUE, List.of(ESTRELLA, "discos-brembo", "bandas-bosch", "liquido-dot4", "amortiguador-monroe", "terminal-moog",
                    "sensor-oxigeno", "sensor-map", "bujias-cobre", "filtro-aceite", "filtro-aire", "filtro-combustible",
                    SE_DEJA_DE_VENDER, "aceite-10w30", "aceite-5w30", "refrigerante", "bateria-45", "bateria-60", "alternador"),
            MANGA, List.of(ESTRELLA, "pastillas-brembo", "bandas-bosch", "liquido-dot4", "amortiguador-kyb", "rotula-moog",
                    "sensor-map", "bujias-iridio", "bujias-cobre", "filtro-aceite", "filtro-aire", "filtro-cabina",
                    "filtro-combustible", SE_DEJA_DE_VENDER, "aceite-10w30", "refrigerante", "bateria-45", "alternador"),
            CRESPO, List.of(ESTRELLA, "liquido-dot4", "amortiguador-monroe", "terminal-moog", "sensor-oxigeno", "bujias-iridio",
                    "bujias-cobre", "bobina-bosch", "filtro-aceite", "filtro-aire", "filtro-cabina", "filtro-combustible",
                    SE_DEJA_DE_VENDER, "aceite-10w30", "aceite-5w30", "refrigerante", "bateria-45", "bateria-60"),
            PRADO, List.of(ESTRELLA, "pastillas-brembo", "discos-brembo", "amortiguador-monroe", "amortiguador-kyb", "rotula-moog",
                    "sensor-oxigeno", "sensor-map", "bujias-iridio", "bobina-bosch", "filtro-aceite", "filtro-aire",
                    SE_DEJA_DE_VENDER, "aceite-10w30", "aceite-5w30", "bateria-45", "bateria-60", "alternador"));

    /** Productos de Autopartes Manga que no se venden nunca: "lo que no se mueve" y su capital inmovilizado. */
    static final List<String> MANGA_QUIETOS = List.of("pastillas-brembo", "amortiguador-kyb", "alternador");

    static String productoId(String tienda, String plantilla) {
        return PREFIJO + "p-" + tienda + "-" + plantilla;
    }

    // ─── Clientes ────────────────────────────────────────────────────────────

    record Vehiculo(String marca, String modelo, int anio, String motor, String placa, int kilometraje) {
    }

    record ClienteDemo(String clave, String nombre, String ciudad, String telefono, String direccion, String barrio,
                       List<Vehiculo> vehiculos) {
        String id() {
            return PREFIJO + "u-" + clave;
        }

        String correo() {
            return "brainy796+" + clave + "@gmail.com";
        }
    }

    private static Vehiculo v(String marca, String modelo, int anio, String motor, String placa, int km) {
        return new Vehiculo(marca, modelo, anio, motor, placa, km);
    }

    /** El cliente frecuente de la historia. */
    static final String FRECUENTE = "carlos.mendoza";

    static final List<ClienteDemo> CLIENTES = List.of(
            new ClienteDemo(FRECUENTE, "Carlos Mendoza", "Cartagena", "3004561230", "Calle 31 # 22-14", "Pie de la Popa",
                    List.of(v("Chevrolet", "Spark GT", 2018, "1.2L", "KSM418", 61200), v("Renault", "Logan", 2016, "1.6L", "IZT552", 98400))),
            new ClienteDemo("laura.perez", "Laura Pérez", "Cartagena", "3012345671", "Carrera 3 # 8-120", "Bocagrande",
                    List.of(v("Renault", "Logan", 2016, "1.6L", "HMQ271", 87300))),
            new ClienteDemo("andres.gomez", "Andrés Gómez", "Cartagena", "3105678234", "Calle 70 # 6-45", "Crespo",
                    List.of(v("Mazda", "3", 2018, "2.0L", "JXP903", 54100))),
            new ClienteDemo("valentina.ruiz", "Valentina Ruiz", "Cartagena", "3157893412", "Transversal 54 # 30-11", "El Bosque",
                    List.of(v("Kia", "Picanto", 2019, "1.25L", "KTR276", 43800))),
            new ClienteDemo("jorge.castillo", "Jorge Castillo", "Cartagena", "3016784523", "Calle Real # 25-30", "Manga",
                    List.of(v("Chevrolet", "Beat", 2020, "1.2L", "LMN628", 38900))),
            new ClienteDemo("maria.rodriguez", "María Rodríguez", "Cartagena", "3023456789", "Carrera 17 # 29-60", "Pie de la Popa",
                    List.of(v("Toyota", "Corolla", 2017, "1.8L", "GHT445", 102300))),
            new ClienteDemo("diego.herrera", "Diego Herrera", "Cartagena", "3118945672", "Calle 21 # 50-14", "El Bosque",
                    List.of(v("Renault", "Sandero", 2019, "1.6L", "JKS781", 51200))),
            new ClienteDemo("camila.torres", "Camila Torres", "Cartagena", "3045671289", "Avenida del Lago # 28-40", "Manga",
                    List.of(v("Nissan", "March", 2018, "1.6L", "HYV390", 64500))),
            new ClienteDemo("sebastian.vargas", "Sebastián Vargas", "Cartagena", "3176543210", "Calle 5 # 2-77", "Bocagrande",
                    List.of(v("Mazda", "3", 2016, "2.0L", "FRD218", 89700))),
            new ClienteDemo("daniela.morales", "Daniela Morales", "Cartagena", "3009876123", "Carrera 2 # 68-30", "Crespo",
                    List.of(v("Kia", "Rio", 2020, "1.4L", "LPQ536", 32600))),
            new ClienteDemo("luis.martinez", "Luis Martínez", "Cartagena", "3128765430", "Calle 30 # 54-12", "El Bosque",
                    List.of(v("Chevrolet", "Aveo", 2014, "1.6L", "DXN662", 134800))),
            new ClienteDemo("natalia.jimenez", "Natalia Jiménez", "Cartagena", "3054328761", "Carrera 21 # 26-50", "Manga",
                    List.of(v("Chevrolet", "Onix", 2021, "1.0L Turbo", "MAR914", 28400))),
            new ClienteDemo("felipe.ramirez", "Felipe Ramírez", "Cartagena", "3167823456", "Calle 8 # 1-35", "Castillogrande",
                    List.of(v("Toyota", "Corolla", 2019, "1.8L", "KBT307", 47900))),
            new ClienteDemo("paola.suarez", "Paola Suárez", "Cartagena", "3015432987", "Calle 66 # 4-18", "Crespo",
                    List.of(v("Renault", "Logan", 2018, "1.6L", "JEM458", 71200))),
            new ClienteDemo("ricardo.ortiz", "Ricardo Ortiz", "Cartagena", "3138762345", "Calle 29 # 18-90", "Pie de la Popa",
                    List.of(v("Chevrolet", "Spark", 2015, "1.0L", "FQZ219", 118600))),
            new ClienteDemo("juliana.rios", "Juliana Ríos", "Cartagena", "3046789123", "Transversal 45 # 22-31", "El Bosque",
                    List.of(v("Kia", "Picanto", 2017, "1.25L", "HUL743", 66300))),
            new ClienteDemo("mauricio.pineda", "Mauricio Pineda", "Cartagena", "3189876542", "Carrera 24 # 23-45", "Manga",
                    List.of(v("Nissan", "March", 2016, "1.6L", "GZA581", 92400))),
            new ClienteDemo("alejandra.cardenas", "Alejandra Cárdenas", "Cartagena", "3026547891", "Carrera 1 # 10-55", "Bocagrande",
                    List.of(v("Mazda", "3", 2020, "2.0L", "LTW620", 26800))),
            new ClienteDemo("oscar.navarro", "Óscar Navarro", "Barranquilla", "3008765432", "Calle 72 # 54-30", "El Prado",
                    List.of(v("Chevrolet", "Spark GT", 2017, "1.2L", "UFK372", 72900))),
            new ClienteDemo("adriana.lopez", "Adriana López", "Barranquilla", "3116549872", "Carrera 50 # 79-12", "Alto Prado",
                    List.of(v("Toyota", "Corolla", 2018, "1.8L", "WGP815", 58300))),
            new ClienteDemo("hernan.diaz", "Hernán Díaz", "Barranquilla", "3152348796", "Calle 84 # 45-60", "Riomar",
                    List.of(v("Renault", "Sandero", 2017, "1.6L", "TVN264", 81400))),
            new ClienteDemo("sofia.mejia", "Sofía Mejía", "Barranquilla", "3048792345", "Carrera 43 # 70-25", "El Prado",
                    List.of(v("Kia", "Rio", 2019, "1.4L", "UHB548", 40100))));

    static ClienteDemo cliente(String clave) {
        return CLIENTES.stream().filter(c -> c.clave().equals(clave)).findFirst().orElseThrow();
    }

    static String vehiculoId(ClienteDemo c, int i) {
        return PREFIJO + "v-" + c.clave() + "-" + (i + 1);
    }

    // ─── Talleres ────────────────────────────────────────────────────────────

    record TallerDemo(String clave, String nombre, String correo, String nitNumero, String ciudad, String barrio, String direccion,
                      double latitud, double longitud, String telefono, double precioManoObra, double tarifaHora,
                      List<String> especialidades, String horario, String mecanico, String banco, String bancoCodigo, String cuenta) {
        String id() {
            return PREFIJO + "taller-" + clave;
        }

        String usuarioId() {
            return PREFIJO + "u-taller-" + clave;
        }

        String nit() {
            return nitNumero + "-" + NitColombia.digitoVerificacion(nitNumero);
        }
    }

    static final String LA_MATUNA = "lamatuna";
    static final String PIE_DE_LA_POPA = "piedelapopa";

    static final List<TallerDemo> TALLERES = List.of(
            new TallerDemo(LA_MATUNA, "Taller La Matuna", "brainyblancoh+taller.lamatuna@gmail.com", "901457287",
                    "Cartagena", "La Matuna", "Avenida Venezuela # 10-23", 10.4228, -75.5466, "3014561278", 80000, 60000,
                    List.of("Frenos", "Suspensión", "Alineación y balanceo"), "Lunes a sábado 7:30 a. m. - 5:30 p. m.",
                    "Wilson Barrios", "Bancolombia", "1007", "65845612390"),
            new TallerDemo(PIE_DE_LA_POPA, "Taller Pie de la Popa", "brainyblancoh+taller.piedelapopa@gmail.com", "901457294",
                    "Cartagena", "Pie de la Popa", "Calle 30 # 19-45", 10.4199, -75.5361, "3156782340", 70000, 55000,
                    List.of("Motor", "Eléctrico", "Inyección"), "Lunes a viernes 8:00 a. m. - 6:00 p. m., sábados 8:00 a. m. - 12:00 m.",
                    "Yesid Contreras", "Davivienda", "1051", "457890123456"));

    static TallerDemo taller(String clave) {
        return TALLERES.stream().filter(t -> t.clave().equals(clave)).findFirst().orElseThrow();
    }

    /** SUPERADMIN demo: solo se crea si la base no tiene ninguno (una base vacía). */
    static final String SUPERADMIN_CORREO = "brainyblancoh+admin.demo@gmail.com";
    static final String SUPERADMIN_ID = PREFIJO + "u-superadmin";
}
