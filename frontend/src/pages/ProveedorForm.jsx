import { useEffect, useState } from "react";
import { crearProveedor, actualizarProveedor, obtenerProveedor } from "../services/proveedorService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import SeccionEditable from "../components/ui/SeccionEditable";
import Campo from "../components/ui/Campo";
import PieFormulario from "../components/ui/PieFormulario";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import { erroresProveedor } from "./tienda/proveedoresTienda";
import "../css/proveedores.css";

/** Alta o edición de un proveedor: nombre y correo obligatorios (al correo le llega el PDF de cada compra). */
export default function ProveedorForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);
  const [form, setForm] = useState({ nombre: "", nit: "", telefono: "", correo: "" });
  const [cargando, setCargando] = useState(esEdicion);
  const [guardando, setGuardando] = useState(false);
  const [tocado, setTocado] = useState(false);
  const [errorCarga, setErrorCarga] = useState(null);
  const [errorGuardar, setErrorGuardar] = useState(null);

  useEffect(() => {
    if (!esEdicion) return;
    obtenerProveedor(id, token)
      .then((data) => setForm({
        nombre: data.nombre || "", nit: data.nit || "", telefono: data.telefono || "", correo: data.correo || "",
      }))
      .catch((err) => setErrorCarga(err.message))
      .finally(() => setCargando(false));
  }, [id, token, esEdicion]);

  const errores = tocado ? erroresProveedor(form) : {};
  const cambiar = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const guardar = async (e) => {
    e.preventDefault();
    setTocado(true);
    if (Object.keys(erroresProveedor(form)).length) {
      requestAnimationFrame(() => document.querySelector(".prv-root [aria-invalid='true']")?.focus());
      return;
    }
    setGuardando(true);
    setErrorGuardar(null);
    try {
      const datos = { nombre: form.nombre.trim(), nit: form.nit.trim(), telefono: form.telefono.trim(), correo: form.correo.trim() };
      if (esEdicion) await actualizarProveedor(id, datos, token);
      else await crearProveedor(datos, token);
      onVolver();
    } catch (err) {
      setErrorGuardar(err.message);
      setGuardando(false);
    }
  };

  const titulo = esEdicion ? "Editar proveedor" : "Nuevo proveedor";

  return (
    <div className="ss-panel--tienda prv-root prv-form">
      <EncabezadoPagina titulo={esEdicion && form.nombre ? form.nombre : titulo} onVolver={onVolver} textoVolver="Proveedores"
        descripcion="Lo eliges al registrar una compra. Al correo le llega el PDF de cada compra que le hagas." />

      {errorCarga && <Alerta className="prv-alerta">No pudimos cargar el proveedor. {errorCarga}</Alerta>}

      {cargando ? (
        <Esqueleto variante="formulario" cantidad={4} texto="Cargando el proveedor…" />
      ) : (
        <SeccionEditable id="prv-datos" icono="users" titulo="Datos del proveedor" editando>
          <form className="ui-form" onSubmit={guardar} noValidate>
            <div className="ui-form__campos ui-form__campos--2">
              <Campo id="prv-nombre" label="Nombre de la empresa" error={errores.nombre}>
                <input className="ui-field__input" name="nombre" value={form.nombre} onChange={cambiar} autoFocus
                  autoComplete="organization" placeholder="Ej. Repuestos Eléctricos S.A.S." />
              </Campo>
              <Campo id="prv-nit" label="NIT" opcional error={errores.nit}>
                <input className="ui-field__input" name="nit" value={form.nit} onChange={cambiar} inputMode="numeric" placeholder="Ej. 900123456-7" />
              </Campo>
              <Campo id="prv-correo" label="Correo" error={errores.correo} ayuda="Ahí le enviamos el PDF de cada compra.">
                <input className="ui-field__input" name="correo" type="email" value={form.correo} onChange={cambiar}
                  autoComplete="email" placeholder="contacto@proveedor.com" />
              </Campo>
              <Campo id="prv-telefono" label="Teléfono" opcional error={errores.telefono}>
                <input className="ui-field__input" name="telefono" type="tel" inputMode="tel" value={form.telefono} onChange={cambiar}
                  autoComplete="tel" placeholder="+57 300 123 4567" />
              </Campo>
            </div>
            <PieFormulario guardando={guardando} error={errorGuardar} onCancelar={onVolver}
              textoGuardar={esEdicion ? "Guardar cambios" : "Agregar proveedor"} />
          </form>
        </SeccionEditable>
      )}
    </div>
  );
}
