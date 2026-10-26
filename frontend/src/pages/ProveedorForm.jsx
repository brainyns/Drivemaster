import { useEffect, useState } from "react";
import { crearProveedor, actualizarProveedor, obtenerProveedor, listarProveedores } from "../services/proveedorService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import SeccionEditable from "../components/ui/SeccionEditable";
import Campo from "../components/ui/Campo";
import PieFormulario from "../components/ui/PieFormulario";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import Interruptor from "../components/ui/Interruptor";
import { avisar } from "../components/ui/avisar";
import { avisoDigitoNit, correoValido, erroresProveedor, nombreRepetido } from "./tienda/proveedoresTienda";
import "../css/proveedores.css";

/**
 * Alta o edición de un proveedor. Solo el nombre es obligatorio; si tiene correo, la tienda decide si le llega el PDF
 * de cada compra. El NIT repetido lo bloquea el backend; el nombre repetido y el dígito de verificación solo se avisan.
 */
export default function ProveedorForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);
  const [form, setForm] = useState({
    nombre: "", nit: "", telefono: "", correo: "", enviarPdfCompras: true,
    contacto: "", direccion: "", condicionPago: "", diasCredito: "",
  });
  const [otros, setOtros] = useState([]);
  const [cargando, setCargando] = useState(esEdicion);
  const [guardando, setGuardando] = useState(false);
  const [tocado, setTocado] = useState(false);
  const [errorCarga, setErrorCarga] = useState(null);
  const [errorGuardar, setErrorGuardar] = useState(null);
  // Errores por campo que respondió el backend; se borran al editar el campo
  const [erroresServidor, setErroresServidor] = useState({});

  useEffect(() => {
    // Para avisar si el nombre ya está en otro proveedor de la tienda
    listarProveedores(token).then((l) => setOtros(l || [])).catch(() => {});
    if (!esEdicion) return;
    obtenerProveedor(id, token)
      .then((data) => setForm({
        nombre: data.nombre || "", nit: data.nit || "", telefono: data.telefono || "", correo: data.correo || "",
        enviarPdfCompras: data.enviarPdfCompras !== false,
        contacto: data.contacto || "", direccion: data.direccion || "",
        condicionPago: data.condicionPago || "", diasCredito: data.diasCredito ?? "",
      }))
      .catch((err) => setErrorCarga(err.message))
      .finally(() => setCargando(false));
  }, [id, token, esEdicion]);

  const errores = { ...erroresServidor, ...(tocado ? erroresProveedor(form) : {}) };
  const tieneCorreo = correoValido(form.correo);
  const repetido = nombreRepetido(form.nombre, otros, id);
  const avisoNit = avisoDigitoNit(form.nit);

  const cambiar = (e) => {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
    if (erroresServidor[name]) setErroresServidor((prev) => {
      const resto = { ...prev };
      delete resto[name];
      return resto;
    });
  };

  const guardar = async (e) => {
    e.preventDefault();
    if (guardando) return;
    setTocado(true);
    if (Object.keys(erroresProveedor(form)).length) {
      requestAnimationFrame(() => document.querySelector(".prv-root [aria-invalid='true']")?.focus());
      return;
    }
    setGuardando(true);
    setErrorGuardar(null);
    setErroresServidor({});
    try {
      const datos = {
        nombre: form.nombre.trim(), nit: form.nit.trim(), telefono: form.telefono.trim(), correo: form.correo.trim(),
        // Sin correo no hay a dónde enviarlo: se guarda apagado
        enviarPdfCompras: tieneCorreo ? form.enviarPdfCompras : false,
        contacto: form.contacto.trim() || null,
        direccion: form.direccion.trim() || null,
        condicionPago: form.condicionPago || null,
        diasCredito: form.condicionPago === "CREDITO" ? Number(form.diasCredito) : null,
      };
      if (esEdicion) await actualizarProveedor(id, datos, token);
      else await crearProveedor(datos, token);
      avisar(esEdicion ? `Guardamos los cambios de ${datos.nombre}.` : `Agregamos a ${datos.nombre} a tus proveedores.`);
      onVolver();
    } catch (err) {
      // Los datos se conservan; el error va bajo su campo o, si no es de un campo, al pie
      const campos = err.campos && Object.keys(err.campos).some((c) => c in form) ? err.campos : null;
      if (campos) {
        setErroresServidor(campos);
        requestAnimationFrame(() => document.querySelector(".prv-root [aria-invalid='true']")?.focus());
      } else {
        setErrorGuardar(err.message);
      }
      setGuardando(false);
    }
  };

  const titulo = esEdicion ? "Editar proveedor" : "Nuevo proveedor";

  return (
    <div className="ss-panel--tienda prv-root prv-form">
      <EncabezadoPagina titulo={esEdicion && form.nombre ? form.nombre : titulo} onVolver={onVolver} textoVolver="Proveedores"
        descripcion="Lo eliges al registrar una compra. Si tiene correo, puede recibir el PDF de cada compra que le hagas." />

      {errorCarga && <Alerta className="prv-alerta">No pudimos cargar el proveedor. {errorCarga}</Alerta>}

      {cargando ? (
        <Esqueleto variante="formulario" cantidad={4} texto="Cargando el proveedor…" />
      ) : (
        <SeccionEditable id="prv-datos" icono="users" titulo="Datos del proveedor" editando>
          <form className="ui-form" onSubmit={guardar} noValidate>
            <div className="ui-form__campos ui-form__campos--2">
              <Campo id="prv-nombre" label="Nombre de la empresa" error={errores.nombre}
                aviso={repetido ? `Ya tienes un proveedor llamado “${repetido.nombre}”. Puedes guardarlo igual si es otro.` : undefined}>
                <input className="ui-field__input" name="nombre" value={form.nombre} onChange={cambiar} autoFocus
                  autoComplete="organization" placeholder="Ej. Repuestos Eléctricos S.A.S." />
              </Campo>
              <Campo id="prv-nit" label="NIT" opcional error={errores.nit} aviso={avisoNit}
                ayuda="Con el dígito de verificación, como 900123456-7. No puede repetirse entre tus proveedores.">
                <input className="ui-field__input" name="nit" value={form.nit} onChange={cambiar} inputMode="numeric" placeholder="Ej. 900123456-7" />
              </Campo>
              <Campo id="prv-correo" label="Correo" opcional error={errores.correo}>
                <input className="ui-field__input" name="correo" type="email" value={form.correo} onChange={cambiar}
                  autoComplete="email" placeholder="contacto@proveedor.com" />
              </Campo>
              <Campo id="prv-telefono" label="Teléfono" opcional error={errores.telefono}>
                <input className="ui-field__input" name="telefono" type="tel" inputMode="tel" value={form.telefono} onChange={cambiar}
                  autoComplete="tel" placeholder="+57 300 123 4567" />
              </Campo>
            </div>

            <div className="ui-form__campos ui-form__campos--2">
              <Campo id="prv-contacto" label="Contacto comercial" opcional error={errores.contacto} ayuda="La persona con la que hablas para pedir.">
                <input className="ui-field__input" name="contacto" value={form.contacto} onChange={cambiar} maxLength={80}
                  autoComplete="name" placeholder="Ej. Laura Gómez" />
              </Campo>
              <Campo id="prv-direccion" label="Dirección" opcional error={errores.direccion}>
                <input className="ui-field__input" name="direccion" value={form.direccion} onChange={cambiar} maxLength={160}
                  autoComplete="street-address" placeholder="Ej. Cra 30 # 10-25, Bogotá" />
              </Campo>
              <Campo id="prv-condicion" label="Condiciones de pago" opcional error={errores.condicionPago}>
                <select className="ui-field__input" name="condicionPago" value={form.condicionPago} onChange={cambiar}>
                  <option value="">Sin indicar</option>
                  <option value="CONTADO">De contado</option>
                  <option value="CREDITO">A crédito</option>
                </select>
              </Campo>
              {form.condicionPago === "CREDITO" && (
                <Campo id="prv-dias" label="Plazo del crédito (días)" error={errores.diasCredito} ayuda="Entre 1 y 180 días.">
                  <input className="ui-field__input" name="diasCredito" type="text" inputMode="numeric" value={form.diasCredito}
                    onChange={cambiar} placeholder="Ej. 30" />
                </Campo>
              )}
            </div>

            <div className="prv-pdf">
              <span className="prv-pdf__texto">
                <strong id="prv-pdf-titulo">Enviar el PDF de cada compra a este correo</strong>
                <span>{tieneCorreo
                  ? "Al registrar una compra le llega la orden en PDF."
                  : "Escribe un correo válido para poder enviarle el PDF."}</span>
              </span>
              <Interruptor activo={tieneCorreo && form.enviarPdfCompras} disabled={!tieneCorreo} textos={["Sí", "No"]}
                etiqueta="Enviar el PDF de cada compra a este correo"
                onCambiar={(v) => setForm((f) => ({ ...f, enviarPdfCompras: v }))} />
            </div>

            <PieFormulario guardando={guardando} error={errorGuardar} onCancelar={onVolver}
              textoGuardar={esEdicion ? "Guardar cambios" : "Agregar proveedor"} />
          </form>
        </SeccionEditable>
      )}
    </div>
  );
}
