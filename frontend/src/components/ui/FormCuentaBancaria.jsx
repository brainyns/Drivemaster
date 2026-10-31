import { useState } from "react";
import Campo from "./Campo";
import PieFormulario from "./PieFormulario";
import { BANCOS_COLOMBIA, TIPOS_CUENTA, TIPOS_DOCUMENTO } from "../../utils/bancosColombia";

const NUMERO_CUENTA_REGEX = /^[0-9\-]{4,25}$/;
const NUMERO_DOC_REGEX = /^[0-9a-zA-Z\-]{4,30}$/;

export default function FormCuentaBancaria({ cuenta, onGuardar, onCancelar }) {
  const [bancoCodigo, setBancoCodigo] = useState(cuenta?.bancoCodigo || BANCOS_COLOMBIA[0].codigo);
  const [banco, setBanco] = useState(cuenta?.banco || BANCOS_COLOMBIA[0].nombre);
  const [tipoCuenta, setTipoCuenta] = useState(cuenta?.tipoCuenta || "AHORROS");
  const [numeroCuenta, setNumeroCuenta] = useState(cuenta?.numeroCuenta || "");
  const [titularNombre, setTitularNombre] = useState(cuenta?.titularNombre || "");
  const [titularDocumentoTipo, setTitularDocumentoTipo] = useState(cuenta?.titularDocumentoTipo || "CC");
  const [titularDocumentoNumero, setTitularDocumentoNumero] = useState(cuenta?.titularDocumentoNumero || "");

  const [errores, setErrores] = useState({});
  const [guardando, setGuardando] = useState(false);
  const [errorServidor, setErrorServidor] = useState(null);

  const cambiarBanco = (codigo) => {
    setBancoCodigo(codigo);
    const encontrado = BANCOS_COLOMBIA.find((b) => b.codigo === codigo);
    if (encontrado) setBanco(encontrado.nombre);
    if (errores.banco) setErrores((prev) => ({ ...prev, banco: null }));
  };

  const validar = () => {
    const err = {};
    if (!bancoCodigo || !banco) err.banco = "Selecciona tu entidad financiera";
    if (!tipoCuenta) err.tipoCuenta = "Selecciona el tipo de cuenta";
    const num = numeroCuenta.trim();
    if (!num) {
      err.numeroCuenta = "Ingresa el número de cuenta";
    } else if (!NUMERO_CUENTA_REGEX.test(num)) {
      err.numeroCuenta = "Solo dígitos y guiones (entre 4 y 25 caracteres)";
    }

    const nombre = titularNombre.trim();
    if (!nombre || nombre.length < 3) {
      err.titularNombre = "El nombre del titular debe tener al menos 3 caracteres";
    }

    if (!titularDocumentoTipo) err.titularDocumentoTipo = "Selecciona el tipo de documento";

    const docNum = titularDocumentoNumero.trim();
    if (!docNum) {
      err.titularDocumentoNumero = "Ingresa el número de documento";
    } else if (!NUMERO_DOC_REGEX.test(docNum)) {
      err.titularDocumentoNumero = "Documento no válido (entre 4 y 30 caracteres alfanuméricos)";
    }

    setErrores(err);
    return Object.keys(err).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validar()) return;

    setGuardando(true);
    setErrorServidor(null);
    try {
      await onGuardar({
        banco: banco.trim(),
        bancoCodigo: bancoCodigo.trim(),
        tipoCuenta: tipoCuenta.trim().toUpperCase(),
        numeroCuenta: numeroCuenta.trim(),
        titularNombre: titularNombre.trim(),
        titularDocumentoTipo: titularDocumentoTipo.trim().toUpperCase(),
        titularDocumentoNumero: titularDocumentoNumero.trim(),
      });
    } catch (err) {
      setErrorServidor(err.message || "Error al guardar los datos bancarios");
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form" onSubmit={handleSubmit} noValidate>
      <div className="ui-form__campos">
        <Campo id="fcb-banco" label="Entidad bancaria o financiera" error={errores.banco}
          ayuda="Entidad donde está radicada tu cuenta para recibir transferencias ACH.">
          <select className="ui-field__input" value={bancoCodigo} onChange={(e) => cambiarBanco(e.target.value)}>
            {BANCOS_COLOMBIA.map((b) => (
              <option key={b.codigo} value={b.codigo}>{b.nombre}</option>
            ))}
          </select>
        </Campo>

        <Campo id="fcb-tipo" label="Tipo de cuenta" error={errores.tipoCuenta}>
          <select className="ui-field__input" value={tipoCuenta} onChange={(e) => setTipoCuenta(e.target.value)}>
            {TIPOS_CUENTA.map((t) => (
              <option key={t.id} value={t.id}>{t.label}</option>
            ))}
          </select>
        </Campo>

        <Campo id="fcb-numero" label="Número de cuenta" error={errores.numeroCuenta}
          ayuda="Sin espacios. Si es Nequi o Daviplata, suele ser tu número de celular.">
          <input className="ui-field__input" type="text" inputMode="numeric" placeholder="Ej: 1234567890"
            value={numeroCuenta} onChange={(e) => {
              setNumeroCuenta(e.target.value);
              if (errores.numeroCuenta) setErrores((prev) => ({ ...prev, numeroCuenta: null }));
            }} />
        </Campo>

        <Campo id="fcb-titular" label="Nombre completo o razón social del titular" error={errores.titularNombre}
          ayuda="Tal como figura en el certificado bancario.">
          <input className="ui-field__input" type="text" placeholder="Ej: Inversiones Los Andes S.A.S. o Juan Pérez"
            value={titularNombre} onChange={(e) => {
              setTitularNombre(e.target.value);
              if (errores.titularNombre) setErrores((prev) => ({ ...prev, titularNombre: null }));
            }} />
        </Campo>

        <Campo id="fcb-doc-tipo" label="Tipo de documento del titular" error={errores.titularDocumentoTipo}>
          <select className="ui-field__input" value={titularDocumentoTipo} onChange={(e) => setTitularDocumentoTipo(e.target.value)}>
            {TIPOS_DOCUMENTO.map((d) => (
              <option key={d.id} value={d.id}>{d.label}</option>
            ))}
          </select>
        </Campo>

        <Campo id="fcb-doc-num" label="Número de documento" error={errores.titularDocumentoNumero}
          ayuda="Sin puntos ni guiones (si es NIT, incluye el dígito de verificación si aplica).">
          <input className="ui-field__input" type="text" placeholder="Ej: 900123456-1 o 1020304050"
            value={titularDocumentoNumero} onChange={(e) => {
              setTitularDocumentoNumero(e.target.value);
              if (errores.titularDocumentoNumero) setErrores((prev) => ({ ...prev, titularDocumentoNumero: null }));
            }} />
        </Campo>
      </div>

      <PieFormulario guardando={guardando} error={errorServidor} onCancelar={onCancelar} textoGuardar="Guardar cuenta bancaria" />
    </form>
  );
}

