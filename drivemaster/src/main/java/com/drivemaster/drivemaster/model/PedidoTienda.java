package com.drivemaster.drivemaster.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repuestos que una tienda debe llevar al taller, creado al confirmarse el pago de
 * una cotización (uno por tienda). Va aparte de "ventas" para no mezclar el
 * marketplace con los reportes de la tienda propia.
 *
 * No lleva datos del cliente: la tienda solo necesita saber qué y a qué taller.
 */
@Document(collection = "pedidos_tienda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PedidoTienda {

    public static final String PENDIENTE = "PENDIENTE";
    // Solo encargos: la tienda ya está consiguiendo el repuesto
    public static final String EN_PREPARACION = "EN_PREPARACION";
    public static final String DESPACHADO = "DESPACHADO";
    public static final String NO_DESPACHADO = "NO_DESPACHADO";
    public static final String RECIBIDO = "RECIBIDO";
    // Compra directa con retiro en la tienda (4-A): pagado = PENDIENTE; luego estos dos
    public static final String LISTO_PARA_RETIRAR = "LISTO_PARA_RETIRAR";
    public static final String RETIRADO = "RETIRADO";
    // Compra directa con envío: tras DESPACHADO el cliente confirma (o se autoconfirma) y queda ENTREGADO.
    // Si reportó un problema y el ADMIN decide reembolsar, queda NO_ENTREGADO.
    public static final String ENTREGADO = "ENTREGADO";
    public static final String NO_ENTREGADO = "NO_ENTREGADO";

    // De dónde viene el pedido: del taller (cotización) o de una compra directa del catálogo. null = TALLER.
    public static final String ORIGEN_TALLER = "TALLER";
    public static final String ORIGEN_CATALOGO = "CATALOGO";
    // Entrega de una compra del catálogo
    public static final String ENVIO = "ENVIO";
    public static final String RETIRO = "RETIRO";

    // Motivo de "No puedo despachar"
    public static final String MOTIVO_SIN_REPUESTO = "SIN_REPUESTO";
    public static final String MOTIVO_OTRO = "OTRO";

    @Id
    private String id;

    private String ordenId;
    private String cotizacionId;
    private Long pagoId;

    private String tiendaId;
    private String tiendaNombre;

    // Destino de la entrega
    private String tallerId;
    private String tallerNombre;
    private String tallerDireccion;
    private String tallerCiudad;
    private String tallerTelefono;
    private String vehiculoDescripcion;

    private List<CotizacionItem> items;
    private Double subtotal;
    private Double iva;
    private Double total;
    // Días máximos de entrega de los ítems por encargo (null si todo estaba en stock)
    private Integer diasEntrega;

    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaDespacho;
    private String notaDespacho;
    private LocalDateTime fechaNoDespacho;
    // SIN_REPUESTO (no tengo el repuesto) u OTRO (con detalle obligatorio)
    private String tipoMotivoNoDespacho;
    private String motivoNoDespacho;
    private LocalDateTime fechaRecibido;

    // Despacho: plazo máximo (días hábiles desde el pago) y alerta al ADMIN si se pasa
    private LocalDateTime fechaPreparacion;
    private LocalDateTime plazoDespachoHasta;
    private Boolean alertaAtrasoEnviada;

    // ─── Compra directa del catálogo (origen CATALOGO) ───
    private String origen;
    private String compraId;
    private String clienteId;
    private String clienteNombre;
    // ENVIO o RETIRO
    private String entrega;
    // Solo ENVIO: a dónde y a quién (la tienda no ve estos datos en pedidos de retiro)
    private String envioReceptor;
    private String envioTelefono;
    private String envioDireccion;
    private String envioCiudad;
    private String envioIndicaciones;
    private Double costoEnvio;
    // Solo RETIRO: dónde y cuándo retirar (copia de los datos de la tienda al comprar)
    private String tiendaDireccion;
    private String tiendaCiudad;
    private String tiendaHorario;
    // Código de retiro: solo se guarda su hash, nunca viaja en el JSON ni se escribe en logs
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String codigoRetiroHash;
    private Boolean codigoRetiroUsado;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer codigoRetiroIntentos;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private LocalDateTime codigoRetiroBloqueadoHasta;
    private LocalDateTime fechaListoRetiro;
    private LocalDateTime fechaRetirado;
    // Retiro que nadie recoge (RetiroPendienteService): recordatorios al cliente enviados (1 a los 3 días, 2 a los 7)
    // y, a los 15 días, desde cuándo lo revisa el ADMIN. La decisión es suya: SEGUIR_ESPERANDO o REEMBOLSO.
    private Integer recordatoriosRetiro;
    private LocalDateTime revisionRetiroDesde;
    private String resolucionRetiro;
    private LocalDateTime fechaResolucionRetiro;
    private String motivoResolucionRetiro;
    private String retiroResueltoPor;

    // Envío: confirmación de recepción por el cliente
    private LocalDateTime fechaRecibidoCliente;
    // true si nadie respondió y se confirmó sola al vencer el plazo
    private Boolean recepcionAutomatica;
    // Desde el despacho + N días hábiles (PLAZO_AUTOCONFIRMACION_DIAS_HABILES); null en retiros y pedidos del taller
    private LocalDateTime autoconfirmaDespuesDe;
    // "Reportar un problema": pausa la autoconfirmación y la liquidación hasta que el ADMIN resuelva
    private Boolean problemaReportado;
    private String problemaDetalle;
    private LocalDateTime fechaProblema;
    // ENTREGA_CONFIRMADA_POR_ADMIN o REEMBOLSO
    private String resolucionProblema;
    private LocalDateTime fechaResolucionProblema;
    // Motivo escrito por el ADMIN al resolver y quién lo resolvió
    private String motivoResolucion;
    private String resueltoPor;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean esCatalogo() {
        return ORIGEN_CATALOGO.equals(origen);
    }
}
