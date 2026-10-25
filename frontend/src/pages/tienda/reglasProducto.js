// Formulario de producto de la tienda: listas (categorías, marcas, modelos) y validación en línea. Sin
// componentes: lo prueban los tests con node --test. Las reglas son las de siempre; solo cambian los textos.

export const CATEGORIAS = [
  "Motor",
  "Transmision y Embrague",
  "Frenos",
  "Suspension y Direccion",
  "Sistema Electrico",
  "Baterias y Carga",
  "Enfriamiento y Radiadores",
  "Lubricantes y Fluidos",
  "Filtros",
  "Escape y Emisiones",
  "Combustible e Inyeccion",
  "Aire Acondicionado y Climatizacion",
  "Carroceria Exterior",
  "Interior y Accesorios",
  "Iluminacion",
  "Neumaticos y Llantas",
  "Audio, Multimedia y Electronica",
  "Herramientas y Equipos",
  "Consumibles y Taller",
  "Otro",
];

export const MARCAS_VEHICULO = {
  Toyota:       ["Corolla","Camry","RAV4","Highlander","Prius","Land Cruiser","Yaris","Hilux","Fortuner","4Runner","Avalon","C-HR","Venza","Sequoia","Tacoma","Tundra","GR86","Supra","FJ Cruiser","Rush","Innova","Avanza","Wigo","Starlet","Etios"],
  Mazda:        ["Mazda2","Mazda3","Mazda6","CX-3","CX-30","CX-5","CX-50","CX-60","CX-90","MX-5 Miata","MX-30","BT-50","RX-7","RX-8","626","323"],
  Kia:          ["Picanto","Rio","Cerato","Forte","Stinger","Sportage","Sorento","Telluride","Soul","Seltos","Niro","EV6","EV9","Carnival","Stonic","XCeed","ProCeed","K5","K8","K900","Mohave"],
  Renault:      ["Logan","Sandero","Duster","Kwid","Captur","Koleos","Megane","Clio","Fluence","Symbol","Oroch","Alaskan","Arkana","Zoe","Triber","Kardian","Stepway"],
  Chevrolet:    ["Spark","Sail","Aveo","Cruze","Malibu","Camaro","Corvette","Equinox","Trax","Trailblazer","Traverse","Tahoe","Suburban","Silverado","Colorado","Captiva","Onix","Tracker","Montana","Blazer","Bolt EV"],
  Hyundai:      ["i10","i20","i30","Elantra","Sonata","Accent","Tucson","Santa Fe","Palisade","Venue","Creta","Kona","Ioniq","Ioniq 5","Ioniq 6","Starex","H1","Veloster","Genesis","Nexo"],
  Ford:         ["Fiesta","Focus","Mustang","Fusion","Taurus","EcoSport","Escape","Edge","Explorer","Expedition","Bronco","Bronco Sport","Maverick","Ranger","F-150","F-250","Transit","Puma","Kuga","Territory"],
  Volkswagen:   ["Polo","Golf","Jetta","Passat","Arteon","Tiguan","T-Cross","T-Roc","Touareg","Atlas","ID.4","ID.3","Amarok","Caddy","Taos","Virtus","Nivus","Saveiro"],
  Nissan:       ["Versa","Sentra","Altima","Maxima","Tiida","Kicks","Qashqai","X-Trail","Murano","Pathfinder","Armada","Frontier","Navara","GT-R","370Z","400Z","Leaf","Ariya","Terra","NP300"],
  Honda:        ["Fit","Jazz","City","Civic","Accord","HR-V","CR-V","Pilot","Passport","Ridgeline","Odyssey","Element","BR-V","WR-V","ZR-V","Insight"],
  Suzuki:       ["Alto","Celerio","Swift","Baleno","Ciaz","Vitara","S-Cross","Ignis","Jimny","Grand Vitara","Ertiga","XL7","Fronx"],
  BMW:          ["116i","118i","120i","M2","M3","M4","M5","318i","320i","325i","330i","418i","420i","430i","520i","528i","530i","540i","730i","740i","750i","X1","X2","X3","X4","X5","X6","X7","iX","i4","i7","Z4"],
  "Mercedes-Benz": ["A180","A200","A250","C180","C200","C220","C300","E200","E250","E300","E350","S350","S400","S500","GLA180","GLA200","GLC200","GLC300","GLE300","GLE400","GLS350","GLS450","CLA200","CLA250","EQA","EQB","EQC","EQS","AMG GT"],
  Audi:         ["A1","A3","A4","A5","A6","A7","A8","Q2","Q3","Q5","Q7","Q8","TT","R8","e-tron","e-tron GT","RS3","RS4","RS5","RS6","RS7","S3","S4","S5","S6","S7","S8","SQ5","SQ7","SQ8"],
  Jeep:         ["Renegade","Compass","Cherokee","Grand Cherokee","Wrangler","Gladiator","Commander","Avenger","Grand Wagoneer"],
  Mitsubishi:   ["Mirage","Attrage","Lancer","Galant","Eclipse Cross","ASX","Outlander","Outlander Sport","Pajero","Montero","L200","Triton","Xpander","Xforce"],
  Subaru:       ["Impreza","Legacy","Outback","Forester","XV","Crosstrek","Ascent","BRZ","WRX","WRX STI","Solterra"],
  Volvo:        ["S60","S90","V60","V90","XC40","XC60","XC90","C40","EX30","EX90"],
  Peugeot:      ["208","308","408","508","2008","3008","5008","Landtrek","Partner","Expert","Boxer"],
  Fiat:         ["500","Mobi","Argo","Cronos","Siena","Pulse","Fastback","Toro","Doblo","Ducato"],
  Dodge:        ["Neon","Charger","Challenger","Durango","Journey","RAM 1500","RAM 2500","Hornet","Viper"],
  "Land Rover": ["Defender","Discovery","Discovery Sport","Freelander","Range Rover","Range Rover Sport","Range Rover Evoque","Range Rover Velar"],
  Porsche:      ["911","718 Cayman","718 Boxster","Taycan","Panamera","Macan","Cayenne"],
  Lexus:        ["IS","ES","GS","LS","UX","NX","RX","GX","LX","RC","LC","RZ"],
  "Alfa Romeo": ["Giulia","Stelvio","Tonale","Giulietta","MiTo","4C","Spider"],
  Seat:         ["Ibiza","Leon","Arona","Ateca","Tarraco","Mii","Alhambra","Toledo"],
  Skoda:        ["Fabia","Octavia","Superb","Kamiq","Karoq","Kodiaq","Enyaq","Scala","Rapid"],
  MINI:         ["Cooper","Cooper S","Clubman","Countryman","Paceman","Convertible","JCW"],
  Tesla:        ["Model 3","Model Y","Model S","Model X","Cybertruck","Roadster"],
  BYD:          ["Atto 3","Han","Tang","Song Plus","Seal","Dolphin","Yuan Plus","Shark"],
  Chery:        ["QQ","Tiggo 2","Tiggo 4","Tiggo 7","Tiggo 8","Arrizo 5","Arrizo 6","Omoda 5","Omoda C9"],
  JAC:          ["J2","J3","J4","S2","S3","S4","S5","T6","T8","EV"],
  MG:           ["MG3","MG5","MG6","ZS","HS","RX5","RX8","One","Marvel R","EHS"],
  Geely:        ["Coolray","Okavango","Emgrand","Azkarra","BL","MK","EC7"],
  Ssangyong:    ["Tivoli","Korando","Rexton","Musso","Actyon","Rodius"],
  Isuzu:        ["D-Max","MU-X","Trooper","Crosswind","Sportivo"],
  RAM:          ["700","1000","1500","2500","3500","ProMaster"],
  GMC:          ["Sierra","Canyon","Terrain","Acadia","Yukon"],
  Cadillac:     ["CT4","CT5","XT4","XT5","XT6","Escalade","LYRIQ"],
  Ferrari:      ["Roma","Portofino","SF90","812","F8","296","GTC4Lusso","Purosangue"],
  Lamborghini:  ["Huracan","Aventador","Urus","Revuelto","Sterrato"],
  Maserati:     ["Ghibli","Quattroporte","Levante","Grecale","MC20","GranTurismo"],
};

export const MARCAS_REPUESTO = [
  "AC Delco","Aisin","Akebono","Behr","Bilstein","Bosch","Brembo","Continental",
  "Dayco","Denso","Delphi","Exide","Fag","Ferodo","Fram","Gates","Hella","KYB",
  "LUK","Mahle","Mann Filter","Moog","Monroe","Motul","NGK","NTN",
  "Pentosin","Sachs","Schaeffler","Shell","SKF","Textar","TRW","Valeo","VDO",
  "Victor Reinz","ZF",
  "Toyota","Chevrolet","Ford","Renault","Kia","Hyundai","Nissan","Honda",
  "Suzuki","Mazda","Volkswagen","BMW","Mercedes-Benz","Audi","Mitsubishi",
  "Otro",
].sort((a, b) => a.localeCompare(b));

export const MARCAS_VEHICULO_LISTA = Object.keys(MARCAS_VEHICULO).sort((a, b) => a.localeCompare(b));

export const ANO_ACTUAL = new Date().getFullYear();

export const campoVacio = v => v === null || v === undefined || String(v).trim() === "";

export function validarFormulario(form, productosExistentes, esEdicion, id) {
  const errores = {};

  if (campoVacio(form.nombre)) errores.nombre = "Escribe el nombre del producto.";
  else if (form.nombre.trim().length < 3) errores.nombre = "El nombre debe tener al menos 3 caracteres.";

  if (campoVacio(form.categoria)) errores.categoria = "Elige una categoría.";
  if (campoVacio(form.codigo)) errores.codigo = "Escribe el código SKU.";
  else {
    const duplicado = productosExistentes.find(
      p => p.codigo?.toLowerCase() === form.codigo.trim().toLowerCase() &&
           (!esEdicion || String(p.id) !== String(id))
    );
    if (duplicado) errores.codigo = "Ya tienes un producto con este código SKU.";
  }

  if (campoVacio(form.marca)) errores.marca = "Elige la marca del repuesto.";

  if (form.tipo === "ENCARGO" && !campoVacio(form.diasEntregaEncargo)) {
    const dias = Number(form.diasEntregaEncargo);
    if (!Number.isInteger(dias) || dias < 1 || dias > 120)
      errores.diasEntregaEncargo = "Escribe entre 1 y 120 días.";
  }

  const compra = Number(form.precioCompra);
  const venta  = Number(form.precioVenta);
  if (campoVacio(form.precioCompra) || isNaN(compra) || compra < 0)
    errores.precioCompra = "Escribe un precio de compra válido (solo números).";
  if (campoVacio(form.precioVenta) || isNaN(venta) || venta < 0)
    errores.precioVenta = "Escribe un precio de venta válido (solo números).";
  else if (compra > 0 && venta > 0 && venta < compra)
    errores.precioVenta = "El precio de venta no puede ser menor que el de compra.";

  const stockActual  = Number(form.stockActual);
  const stockMinimo  = Number(form.stockMinimo);
  if (campoVacio(form.stockActual) || isNaN(stockActual) || !Number.isInteger(stockActual) || stockActual < 0)
    errores.stockActual = "Escribe el stock actual: un número entero desde 0.";
  if (campoVacio(form.stockMinimo) || isNaN(stockMinimo) || !Number.isInteger(stockMinimo) || stockMinimo < 0)
    errores.stockMinimo = "Escribe el stock mínimo: un número entero desde 0.";

  // Un producto universal no necesita vehículos compatibles.
  const erroresCompat = [];
  if (!form.compatibilidadUniversal) form.modelosCompatibles.forEach((comp, i) => {
    const e = {};
    if (campoVacio(comp.marca))   e.marca  = "Elige la marca del vehículo.";
    if (campoVacio(comp.modelo))  e.modelo = "Elige el modelo.";
    const desde = Number(comp.anoDesde);
    const hasta = Number(comp.anoHasta);
    if (campoVacio(comp.anoDesde) || isNaN(desde) || desde < 1900 || desde > ANO_ACTUAL + 1)
      e.anoDesde = "Escribe un año válido.";
    if (campoVacio(comp.anoHasta) || isNaN(hasta) || hasta < 1900 || hasta > ANO_ACTUAL + 2)
      e.anoHasta = "Escribe un año válido.";
    else if (!e.anoDesde && hasta < desde)
      e.anoHasta = "El año final no puede ser anterior al inicial.";
    if (Object.keys(e).length) erroresCompat[i] = e;
  });
  if (erroresCompat.some(Boolean)) errores.compatibilidad = erroresCompat;

  return errores;
}

/** Nombre canónico de la lista del formulario (sin importar mayúsculas), o null si no está. */
export const enLista = (lista, valor) => lista.find((x) => x.toLowerCase() === String(valor || "").trim().toLowerCase()) || null;

/** La compatibilidad sugerida solo se puede agregar si marca y modelo existen en las listas del formulario. */
export function compatDeLista(c) {
  const marca = enLista(MARCAS_VEHICULO_LISTA, c.marca);
  const modelo = marca ? enLista(MARCAS_VEHICULO[marca], c.modelo) : null;
  return marca && modelo ? { marca, modelo, anoDesde: String(c.anoDesde), anoHasta: String(c.anoHasta), motor: c.motor || "" } : null;
}

export const mismaCompat = (a, b) => a.marca === b.marca && a.modelo === b.modelo
  && String(a.anoDesde) === String(b.anoDesde) && String(a.anoHasta) === String(b.anoHasta);

export const COMPAT_VACIA = { marca: "", modelo: "", anoDesde: "", anoHasta: "", motor: "" };

// Imagen: las mismas reglas que la foto del vehículo y el logo del taller
export { TIPOS_IMAGEN, IMAGEN_MAX_MB, errorImagen } from "../../utils/imagen.js";

/** Margen sobre el precio de compra, con un decimal, o null si falta algún precio. */
export function margen(precioCompra, precioVenta) {
  const compra = Number(precioCompra);
  const venta = Number(precioVenta);
  return compra > 0 && venta > 0 ? Math.round(((venta - compra) / compra) * 1000) / 10 : null;
}

/** Precio que ve el cliente: el de venta (sin IVA) más el IVA del parámetro (ivaRate en fracción, 0.19). null si no hay precio. */
export function precioConIva(precioVenta, ivaRate) {
  const venta = Number(precioVenta);
  return venta > 0 && typeof ivaRate === "number" && ivaRate >= 0 ? Math.round(venta * (1 + ivaRate)) : null;
}
