package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.CompraCatalogoRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resumen en texto de las compras del cliente para el chat ("¿por dónde va mi pedido?").
 *
 * Lo arma el backend con la sesión del usuario y viaja en el cuerpo del webhook: el agente de n8n no
 * tiene ninguna herramienta que reciba un correo o un id, así que no puede consultar pedidos de otra
 * persona aunque se lo pidan. Solo lleva la ciudad del envío, nunca la dirección ni el teléfono.
 *
 * Los textos que escriben las tiendas (nombre, nota de despacho, producto...) llegan al prompt del modelo:
 * si alguno parece una instrucción, ese pedido sale sin detalle y el chat remite a Mis compras.
 */
@Service
public class PedidosChatService {

    public static final String SIN_SESION =
            "El cliente no ha iniciado sesión: no se pueden consultar sus pedidos.";
    static final String SIN_COMPRAS = "El cliente inició sesión y no tiene compras registradas.";

    static final String SIN_DETALLE = "no se puede dar el detalle por el chat; el cliente debe revisarlo en Mis compras.";
    // shortcut: lista de palabras, no atrapa todo intento de manipular al modelo; si hay abuso real, quitar la nota de despacho del resumen.
    private static final Pattern SOSPECHOSO = Pattern.compile(
            "(?i)ignor|instrucci|prompt|\\bsystem\\b|asistente|olvida|\\bdi que\\b|\\bdile\\b|\\{\\{|[<>]|https?://");
    private static final int MAX_TEXTO = 100;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale CO = Locale.forLanguageTag("es-CO");

    private final UsuarioService usuarioService;
    private final CompraCatalogoRepository compraRepository;
    private final PedidoTiendaRepository pedidoRepository;

    public PedidosChatService(UsuarioService usuarioService,
                              CompraCatalogoRepository compraRepository,
                              PedidoTiendaRepository pedidoRepository) {
        this.usuarioService = usuarioService;
        this.compraRepository = compraRepository;
        this.pedidoRepository = pedidoRepository;
    }

    /** usuarioId separa la memoria del chat por persona; texto es lo que lee el agente. */
    public record Contexto(String usuarioId, String texto) {}

    public Contexto contexto(String correo) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);
        List<CompraCatalogo> compras = compraRepository.findTop5ByClienteIdOrderByFechaCreacionDesc(usuario.getId());
        Map<String, List<PedidoTienda>> pedidos = compras.stream().collect(Collectors.toMap(
                CompraCatalogo::getId, c -> pedidoRepository.findByCompraIdOrderByFechaCreacionAsc(c.getId())));
        return new Contexto(usuario.getId(), describir(compras, pedidos));
    }

    static String describir(List<CompraCatalogo> compras, Map<String, List<PedidoTienda>> pedidosPorCompra) {
        if (compras.isEmpty()) return SIN_COMPRAS;
        StringBuilder sb = new StringBuilder("Sus últimas compras, de la más reciente a la más antigua:");
        for (CompraCatalogo c : compras) {
            sb.append("\n- Compra del ").append(fecha(c.getFechaCreacion())).append(" por ").append(pesos(c.getTotal()));
            if (sospechoso(c.getMotivoNoAplicada())
                    || (c.getLineas() != null && c.getLineas().stream().anyMatch(l -> sospechoso(l.getProductoNombre())))) {
                sb.append(": ").append(SIN_DETALLE);
                continue;
            }
            sb.append(": ").append(productos(c)).append('.');
            if (CompraCatalogo.PENDIENTE_PAGO.equals(c.getEstado())) {
                sb.append(" Estado: esperando el pago; aún no se ha cobrado.");
            } else if (CompraCatalogo.NO_APLICADA.equals(c.getEstado())) {
                sb.append(" Estado: el pago se recibió pero la compra no se pudo completar")
                        .append(c.getMotivoNoAplicada() != null ? " (" + limpio(c.getMotivoNoAplicada()) + ")" : "")
                        .append("; el dinero se devuelve completo.");
            } else {
                for (PedidoTienda p : pedidosPorCompra.getOrDefault(c.getId(), List.of())) {
                    if (sospechoso(p.getTiendaNombre(), p.getNotaDespacho(), p.getTiendaDireccion(),
                            p.getTiendaCiudad(), p.getTiendaHorario(), p.getEnvioCiudad())) {
                        sb.append("\n  * Pedido: ").append(SIN_DETALLE);
                        continue;
                    }
                    sb.append("\n  * Pedido a ").append(limpio(p.getTiendaNombre())).append(", ")
                            .append(PedidoTienda.RETIRO.equals(p.getEntrega()) ? "para retirar en la tienda"
                                    : "con envío" + (p.getEnvioCiudad() != null ? " a " + limpio(p.getEnvioCiudad()) : ""))
                            .append(": ").append(estado(p));
                }
            }
        }
        return sb.toString();
    }

    private static String estado(PedidoTienda p) {
        if (Boolean.TRUE.equals(p.getProblemaReportado()) && p.getResolucionProblema() == null) {
            return "el cliente reportó un problema y DriveMaster lo está revisando.";
        }
        String e = p.getEstado() == null ? "" : p.getEstado();
        return switch (e) {
            case PedidoTienda.PENDIENTE -> PedidoTienda.RETIRO.equals(p.getEntrega())
                    ? "pagado; la tienda lo está alistando y avisará cuando esté listo para retirar."
                    : "pagado; la tienda aún no lo despacha" + hasta(p.getPlazoDespachoHasta()) + ".";
            case PedidoTienda.EN_PREPARACION -> "la tienda está consiguiendo el repuesto (es por encargo)"
                    + (p.getDiasEntrega() != null ? "; tarda hasta " + p.getDiasEntrega() + " días" : "") + ".";
            case PedidoTienda.DESPACHADO -> "despachado el " + fecha(p.getFechaDespacho())
                    + (p.getNotaDespacho() != null && !p.getNotaDespacho().isBlank() ? " (nota de la tienda: " + limpio(p.getNotaDespacho()) + ")" : "")
                    + "; el cliente debe confirmar en Mis compras cuando lo reciba.";
            case PedidoTienda.NO_DESPACHADO -> "la tienda no pudo despacharlo.";
            case PedidoTienda.LISTO_PARA_RETIRAR -> "listo para retirar en " + limpio(p.getTiendaDireccion())
                    + (p.getTiendaCiudad() != null ? ", " + limpio(p.getTiendaCiudad()) : "")
                    + (p.getTiendaHorario() != null ? " (horario: " + limpio(p.getTiendaHorario()) + ")" : "")
                    + " con el código de retiro que llegó al correo.";
            case PedidoTienda.RETIRADO -> "retirado el " + fecha(p.getFechaRetirado()) + ".";
            case PedidoTienda.ENTREGADO, PedidoTienda.RECIBIDO -> "entregado"
                    + (p.getFechaRecibidoCliente() != null ? " el " + fecha(p.getFechaRecibidoCliente()) : "") + ".";
            case PedidoTienda.NO_ENTREGADO -> "no se entregó; DriveMaster lo resolvió con reembolso.";
            default -> "estado por confirmar.";
        };
    }

    private static String productos(CompraCatalogo c) {
        if (c.getLineas() == null || c.getLineas().isEmpty()) return "sin detalle de productos";
        return c.getLineas().stream()
                .map(l -> limpio(l.getProductoNombre()) + (l.getCantidad() > 1 ? " x" + l.getCantidad() : ""))
                .collect(Collectors.joining(", "));
    }

    private static boolean sospechoso(String... textos) {
        for (String t : textos) if (t != null && SOSPECHOSO.matcher(t).find()) return true;
        return false;
    }

    // Una sola línea y corta: un texto de tienda no puede abrir párrafos nuevos en el prompt.
    private static String limpio(String t) {
        if (t == null) return null;
        String s = t.replaceAll("\\s+", " ").trim();
        return s.length() > MAX_TEXTO ? s.substring(0, MAX_TEXTO) + "…" : s;
    }

    private static String hasta(LocalDateTime plazo) {
        return plazo == null ? "" : " (debe despacharlo a más tardar el " + fecha(plazo) + ")";
    }

    private static String fecha(LocalDateTime f) {
        return f == null ? "fecha sin registrar" : f.format(FECHA);
    }

    private static String pesos(Double monto) {
        return "$" + String.format(CO, "%,.0f", monto == null ? 0 : monto);
    }
}
