package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Despacho;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.Informes;
import com.drivemaster.drivemaster.dto.tienda.InformesTiendaDTO.ProductoVendido;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.Reponer;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.SinMovimiento;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda.VentasSemana;
import com.drivemaster.drivemaster.util.PlantillaCorreo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.drivemaster.drivemaster.util.PlantillaCorreo.e;

/**
 * Resumen semanal de la TIENDA por correo: las cifras las calcula el backend (ventas contra la semana anterior, lo más
 * vendido, qué reponer, dinero quieto) y la IA solo redacta el titular. Si la IA falla, el titular se arma sin ella.
 */
@Service
@Slf4j
public class ResumenSemanalTienda {

    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final int MAX_TITULAR = 110;
    private static final String PIE = "Recibes este correo porque activaste el resumen semanal en \"Mi tienda\". Puedes desactivarlo desde ahí.";

    private final TiendaRepository tiendas;
    private final UsuarioRepository usuarios;
    private final ContextoAsistenteTienda contexto;
    private final AsistenteTiendaIA ia;
    private final EmailsService email;
    private final String frontendUrl;

    public ResumenSemanalTienda(TiendaRepository tiendas, UsuarioRepository usuarios, ContextoAsistenteTienda contexto,
                                AsistenteTiendaIA ia, EmailsService email, @Value("${frontend.url:}") String frontendUrl) {
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.contexto = contexto;
        this.ia = ia;
        this.email = email;
        this.frontendUrl = frontendUrl;
    }

    /** Lunes de la última semana completa (lunes a domingo) antes de hoy. */
    public static LocalDate lunesSemanaPasada(LocalDate hoy) {
        return hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    /** Job del lunes: una vez por semana a cada tienda operativa que lo activó. Una que falle no frena a las demás. */
    public int enviarPendientes() {
        LocalDate lunes = lunesSemanaPasada(LocalDate.now(ContextoAsistenteTienda.ZONA));
        int enviados = 0;
        for (Tienda t : tiendas.findAll()) {
            if (!Boolean.TRUE.equals(t.getResumenSemanal()) || !operativa(t) || lunes.equals(t.getResumenSemanalEnviado())) continue;
            try {
                if (enviar(t, lunes)) {
                    t.setResumenSemanalEnviado(lunes);
                    tiendas.save(t);
                    enviados++;
                }
            } catch (Exception ex) {
                log.error("No se pudo enviar el resumen semanal de la tienda {}: {}", t.getId(), ex.getMessage());
            }
        }
        return enviados;
    }

    /** Aprobada y activa, como pide TiendaService.obtenerOperativaPorUsuario. */
    static boolean operativa(Tienda t) {
        return EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()) && Boolean.TRUE.equals(t.getActivo());
    }

    /** Envía el resumen de la semana que empieza en {@code lunes} al dueño. false si no tiene correo. */
    public boolean enviar(Tienda tienda, LocalDate lunes) {
        Usuario dueno = tienda.getUsuarioId() == null ? null : usuarios.findById(tienda.getUsuarioId()).orElse(null);
        if (dueno == null || dueno.getCorreo() == null || dueno.getCorreo().isBlank()) return false;

        VentasSemana s = contexto.semana(tienda.getId(), lunes);
        Map<String, Object> c = contexto.contexto(tienda);
        Despacho despacho = ((Informes) c.get("informes")).despacho();
        @SuppressWarnings("unchecked") List<Reponer> reponer = (List<Reponer>) c.get("reponer");
        @SuppressWarnings("unchecked") List<SinMovimiento> quietos = (List<SinMovimiento>) c.get("sinMovimiento90d");

        String titular = titular(tienda, s, reponer.size(), despacho.atrasados());
        String html = PlantillaCorreo.documento("Resumen semanal", titular,
                cuerpo(tienda, s, despacho, reponer, quietos), PIE);
        email.enviarEmail(dueno.getCorreo(), "Tu semana en DriveMaster: " + rango(s), html);
        return true;
    }

    // ─── Titular (lo único que escribe la IA) ────────────────────────────────

    private String titular(Tienda tienda, VentasSemana s, int porReponer, long atrasados) {
        String pedido = "Escribe SOLO el titular del correo semanal de mi tienda: una frase de máximo 90 caracteres, sin comillas, "
                + "sin emojis y sin cifras inventadas. Datos de la semana del " + rango(s) + ": ventas " + cop(s.ventas())
                + " (semana anterior " + cop(s.ventasAnterior()) + "), " + s.pedidos() + " pedidos (antes " + s.pedidosAnterior()
                + "), " + porReponer + " productos por reponer, " + atrasados + " pedidos atrasados.";
        return titularValido(ia.pedir(tienda, pedido), s, porReponer);
    }

    /** El texto de la IA solo se usa si es una frase corta de una línea; si no, el titular se arma con las cifras. */
    static String titularValido(String deIa, VentasSemana s, int porReponer) {
        if (deIa != null) {
            String t = deIa.strip().replaceAll("^[\"'«“]+|[\"'»”]+$", "");
            if (!t.isBlank() && !t.contains("\n") && t.length() <= MAX_TITULAR) return t;
        }
        String ventas = s.ventas() > s.ventasAnterior() ? "Vendiste más que la semana pasada"
                : s.ventas() < s.ventasAnterior() ? "Vendiste menos que la semana pasada"
                : "Tu semana en DriveMaster";
        if (porReponer == 0) return ventas;
        return ventas + (s.ventas() > s.ventasAnterior() ? ", pero " : " y ")
                + porReponer + (porReponer == 1 ? " producto está por agotarse" : " productos están por agotarse");
    }

    // ─── Cuerpo ──────────────────────────────────────────────────────────────

    String cuerpo(Tienda tienda, VentasSemana s, Despacho despacho, List<Reponer> reponer, List<SinMovimiento> quietos) {
        StringBuilder b = new StringBuilder();
        b.append(PlantillaCorreo.parrafo("Hola. Este es el resumen de <strong>" + e(tienda.getNombre()) + "</strong> de la semana del "
                + e(rango(s)) + ". Las cifras salen de tus pedidos e inventario; la IA solo redacta el titular."));

        b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;\"><tr>")
                .append(cifra("Ventas", cop(s.ventas()), variacionPct(s.ventas(), s.ventasAnterior()) + " vs. semana anterior"))
                .append(cifra("Pedidos", String.valueOf(s.pedidos()), conSigno(s.pedidos() - s.pedidosAnterior()) + " pedidos"))
                .append(cifra("Por despachar", String.valueOf(despacho.pendientes()),
                        despacho.atrasados() == 0 ? "ninguno atrasado"
                                : despacho.atrasados() + (despacho.atrasados() == 1 ? " atrasado" : " atrasados")))
                .append("</tr></table>");

        b.append(subtitulo("Lo más vendido"));
        if (s.masVendidos().isEmpty()) {
            b.append(PlantillaCorreo.parrafo("No hubo ventas esta semana. Revisa que tus productos estén publicados y con stock."));
        } else {
            b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px;border-collapse:collapse;\">")
                    .append("<tr>").append(th("Producto", "left")).append(th("Unidades", "right")).append(th("Ventas", "right")).append("</tr>");
            for (ProductoVendido p : s.masVendidos()) {
                b.append("<tr>").append(td(e(p.nombre()), "left")).append(td(String.valueOf(p.unidades()), "right"))
                        .append(td(e(cop(p.ventas())), "right")).append("</tr>");
            }
            b.append("</table>");
        }

        if (!reponer.isEmpty()) {
            b.append(subtitulo("Para reponer"));
            StringBuilder lista = new StringBuilder("<ul style=\"margin:0 0 20px;padding-left:18px;\">");
            for (Reponer r : reponer.subList(0, Math.min(3, reponer.size()))) {
                lista.append("<li style=\"margin:0 0 6px;\"><strong>").append(e(r.producto())).append("</strong>: quedan ")
                        .append(r.stock()).append(", mínimo ").append(r.minimo())
                        .append(r.sugerido() > 0 ? ". Sugerimos pedir " + r.sugerido() + (r.sugerido() == 1 ? " unidad" : " unidades") : "")
                        .append(".</li>");
            }
            if (reponer.size() > 3) lista.append("<li>Y ").append(reponer.size() - 3).append(" más en tus informes.</li>");
            b.append(lista).append("</ul>");
        }

        SinMovimiento q = quietos.stream().filter(x -> x.capitalInmovilizado() != null).findFirst().orElse(null);
        if (q != null) {
            b.append(subtitulo("Dinero quieto"));
            String desde = q.ultimaVenta() == null ? "no registra ventas"
                    : "lleva " + ChronoUnit.DAYS.between(q.ultimaVenta(), LocalDate.now(ContextoAsistenteTienda.ZONA)) + " días sin ventas";
            b.append(PlantillaCorreo.recuadro("<strong>" + e(q.producto()) + "</strong> " + desde + ": " + q.stock()
                    + (q.stock() == 1 ? " unidad, " : " unidades, ") + "<strong>" + e(cop(q.capitalInmovilizado()))
                    + "</strong> a precio de compra. Podrías ponerlo en promoción o hablar con el proveedor."));
        }

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            b.append("<p style=\"margin:8px 0 0;\">").append(PlantillaCorreo.boton("Ver mis informes", frontendUrl)).append("</p>");
        }
        return b.toString();
    }

    // ─── Formato ─────────────────────────────────────────────────────────────

    static String rango(VentasSemana s) {
        DateTimeFormatter dia = DateTimeFormatter.ofPattern("d", CO);
        DateTimeFormatter diaMes = DateTimeFormatter.ofPattern("d 'de' MMMM", CO);
        return (s.desde().getMonth() == s.hasta().getMonth() ? s.desde().format(dia) : s.desde().format(diaMes))
                + " al " + s.hasta().format(diaMes);
    }

    static String variacionPct(double actual, double anterior) {
        if (anterior <= 0) return actual > 0 ? "sin ventas la semana anterior" : "igual";
        long pct = Math.round((actual - anterior) / anterior * 100);
        return conSigno(pct) + " %";
    }

    private static String conSigno(long n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    private static String cop(double n) {
        NumberFormat f = NumberFormat.getCurrencyInstance(CO);
        f.setMaximumFractionDigits(0);
        return f.format(n);
    }

    private static String cifra(String etiqueta, String valor, String detalle) {
        return "<td width=\"33%\" valign=\"top\" style=\"padding:12px;border:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0;font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:1px;\">" + e(etiqueta) + "</p>"
                + "<p style=\"margin:4px 0;font-size:18px;font-weight:700;color:#17191C;\">" + e(valor) + "</p>"
                + "<p style=\"margin:0;font-size:12px;color:#6b7280;\">" + e(detalle) + "</p></td>";
    }

    private static String subtitulo(String texto) {
        return "<h2 style=\"margin:20px 0 8px;font-size:15px;color:#17191C;\">" + e(texto) + "</h2>";
    }

    private static String th(String texto, String alinear) {
        return "<th align=\"" + alinear + "\" style=\"padding:8px;border-bottom:2px solid #e5e7eb;font-size:12px;color:#6b7280;\">" + e(texto) + "</th>";
    }

    private static String td(String html, String alinear) {
        return "<td align=\"" + alinear + "\" style=\"padding:8px;border-bottom:1px solid #e5e7eb;font-size:13px;\">" + html + "</td>";
    }
}
