package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.model.Proveedor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reglas de un proveedor antes de guardarlo: nombre obligatorio (2 a 120 caracteres), correo con formato válido si
 * viene (ahí llega el PDF de cada compra) y NIT y teléfono de largo razonable. Contacto, dirección y condiciones de
 * pago son opcionales: de contado no lleva plazo y a crédito exige de 1 a {@value #DIAS_CREDITO_MAX} días. Limpia los
 * espacios y deja en null lo vacío. Si algo falla lanza DatosInvalidosException con el error de cada campo.
 */
public final class ValidacionProveedor {

    public static final int NOMBRE_MIN = 2;
    public static final int NOMBRE_MAX = 120;
    public static final int CORREO_MAX = 120;
    public static final int NIT_MAX = 20;
    public static final int TELEFONO_MAX = 20;
    public static final int CONTACTO_MAX = 80;
    public static final int DIRECCION_MAX = 160;
    public static final int DIAS_CREDITO_MAX = 180;

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[\\d\\s()-]{7,20}$");

    private ValidacionProveedor() {
    }

    /** Valida y normaliza el proveedor (lo modifica: textos sin espacios de más y vacíos en null). */
    public static Proveedor validar(Proveedor p) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (p == null) {
            errores.put("nombre", "Escribe el nombre de la empresa.");
            throw new DatosInvalidosException(errores);
        }
        p.setNombre(limpio(p.getNombre()));
        p.setNit(limpio(p.getNit()));
        p.setTelefono(limpio(p.getTelefono()));
        p.setCorreo(limpio(p.getCorreo()));

        if (p.getNombre() == null) {
            errores.put("nombre", "Escribe el nombre de la empresa.");
        } else if (p.getNombre().length() < NOMBRE_MIN || p.getNombre().length() > NOMBRE_MAX) {
            errores.put("nombre", "El nombre debe tener entre " + NOMBRE_MIN + " y " + NOMBRE_MAX + " caracteres.");
        }
        if (p.getCorreo() != null && (p.getCorreo().length() > CORREO_MAX || !CORREO.matcher(p.getCorreo()).matches())) {
            errores.put("correo", "Escribe un correo válido, como contacto@proveedor.com.");
        }
        if (p.getNit() != null && p.getNit().length() > NIT_MAX) {
            errores.put("nit", "El NIT no puede pasar de " + NIT_MAX + " caracteres.");
        }
        if (p.getTelefono() != null && (p.getTelefono().length() > TELEFONO_MAX || !TELEFONO.matcher(p.getTelefono()).matches())) {
            errores.put("telefono", "Escribe un teléfono válido: solo números, espacios y el signo +.");
        }

        // Opcionales: contacto comercial, dirección y condiciones de pago
        p.setContacto(limpio(p.getContacto()));
        p.setDireccion(limpio(p.getDireccion()));
        p.setCondicionPago(limpio(p.getCondicionPago()));
        if (p.getContacto() != null && p.getContacto().length() > CONTACTO_MAX) {
            errores.put("contacto", "El contacto no puede pasar de " + CONTACTO_MAX + " caracteres.");
        }
        if (p.getDireccion() != null && p.getDireccion().length() > DIRECCION_MAX) {
            errores.put("direccion", "La dirección no puede pasar de " + DIRECCION_MAX + " caracteres.");
        }
        if (p.getCondicionPago() == null || "CONTADO".equals(p.getCondicionPago())) {
            p.setDiasCredito(null); // de contado (o sin dato) no hay plazo
        } else if (!"CREDITO".equals(p.getCondicionPago())) {
            errores.put("condicionPago", "Elige contado o crédito.");
        } else if (p.getDiasCredito() == null || p.getDiasCredito() < 1 || p.getDiasCredito() > DIAS_CREDITO_MAX) {
            errores.put("diasCredito", "Escribe el plazo del crédito: entre 1 y " + DIAS_CREDITO_MAX + " días.");
        }
        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        return p;
    }

    private static String limpio(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s{2,}", " ");
        return t.isEmpty() ? null : t;
    }
}
