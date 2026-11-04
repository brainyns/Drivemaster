"""
Generador de Documento Word (.docx)
Explicación detallada del Modelo Predictivo de Aprobación de Cotizaciones - DriveMaster (Versión Pulida XAI 2.0)
Autores: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello
"""

import os
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

def set_cell_background(cell, fill_hex):
    tcPr = cell._element.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=120, bottom=120, left=180, right=180):
    tcPr = cell._element.get_or_add_tcPr()
    tcMar = parse_xml(
        f'<w:tcMar {nsdecls("w")}>'
        f'<w:top w:w="{top}" w:type="dxa"/>'
        f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
        f'<w:left w:w="{left}" w:type="dxa"/>'
        f'<w:right w:w="{right}" w:type="dxa"/>'
        f'</w:tcMar>'
    )
    tcPr.append(tcMar)

def crear_documento():
    doc = Document()
    
    # Configuración de márgenes
    sections = doc.sections
    for section in sections:
        section.top_margin = Inches(1)
        section.bottom_margin = Inches(1)
        section.left_margin = Inches(1)
        section.right_margin = Inches(1)

    COLOR_PRIMARIO = RGBColor(15, 76, 129)     # Azul Institucional
    COLOR_SECUNDARIO = RGBColor(30, 41, 59)   # Slate oscuro
    COLOR_FONDO_TABLA = "F1F5F9"
    COLOR_HEADER_TABLA = "0F4C81"

    # --- ENCABEZADO ---
    title_p = doc.add_paragraph()
    title_p.paragraph_format.space_before = Pt(10)
    title_p.paragraph_format.space_after = Pt(4)
    run_sub = title_p.add_run("PROPUESTA E IMPLEMENTACIÓN · PROYECTO DE AULA")
    run_sub.font.name = "Calibri"
    run_sub.font.size = Pt(11)
    run_sub.font.bold = True
    run_sub.font.color.rgb = COLOR_PRIMARIO

    h1_p = doc.add_paragraph()
    h1_p.paragraph_format.space_after = Pt(14)
    run_title = h1_p.add_run("Modelo Predictivo y de Explicabilidad (XAI) para la Aprobación de Cotizaciones en DriveMaster")
    run_title.font.name = "Calibri"
    run_title.font.size = Pt(22)
    run_title.font.bold = True
    run_title.font.color.rgb = COLOR_SECUNDARIO

    # Info card
    p_meta = doc.add_paragraph()
    p_meta.paragraph_format.space_after = Pt(20)
    r1 = p_meta.add_run("Integrantes: ")
    r1.bold = True
    p_meta.add_run("Brainy Blanco · Sebastian Ortega · Juan Cantillo · Elias Puello\n")
    r2 = p_meta.add_run("Plataforma: ")
    r2.bold = True
    p_meta.add_run("DriveMaster (Marketplace Automotriz & Red de Talleres)\n")
    r3 = p_meta.add_run("Módulo: ")
    r3.bold = True
    p_meta.add_run("Machine Learning, Inteligencia de Negocio y Explicabilidad (XAI 2.0)")

    doc.add_paragraph().paragraph_format.space_after = Pt(10)

    # --- SECCIÓN 1: INTRODUCCIÓN Y PROBLEMA ---
    h_sec1 = doc.add_heading("1. Descripción del Problema y Justificación", level=1)
    h_sec1.style.font.color.rgb = COLOR_PRIMARIO

    p1 = doc.add_paragraph(
        "En la plataforma DriveMaster, los talleres mecánicos reciben solicitudes de diagnóstico y mantenimiento "
        "de clientes particulares y flotillas. Para responder a una solicitud, el taller formula una cotización formal que incluye "
        "los repuestos (provenientes de tiendas de autopartes asociadas en el marketplace), la mano de obra calificada y el IVA del 19 %."
    )
    p2 = doc.add_paragraph(
        "Tradicionalmente, el taller opera 'a ciegas' sin saber si el cliente va a aceptar "
        "o rechazar la propuesta. Cada cotización rechazada genera consecuencias negativas para el ecosistema:"
    )
    
    b1 = doc.add_paragraph(style='List Bullet')
    r = b1.add_run("Tiempo invertido no remunerado: ")
    r.bold = True
    b1.add_run("El personal del taller dedica tiempo inspeccionando y cotizando sin concretar el trabajo.")

    b2 = doc.add_paragraph(style='List Bullet')
    r = b2.add_run("Pérdida de tracción del cliente: ")
    r.bold = True
    b2.add_run("El cliente que recibe una cotización desproporcionada o demorada suele desertar de la plataforma.")

    b3 = doc.add_paragraph(style='List Bullet')
    r = b3.add_run("Falta de retroalimentación constructiva: ")
    r.bold = True
    b3.add_run("El taller no sabe si el rechazo se debió al precio, a la demora en responder, a la distancia geográfica o a la reputación.")

    # --- SECCIÓN 2: FORMULACIÓN MATEMÁTICA ---
    h_sec2 = doc.add_heading("2. Formulación Matemática y Algoritmo Seleccionado", level=1)
    h_sec2.style.font.color.rgb = COLOR_PRIMARIO

    doc.add_paragraph(
        "Se implementó un modelo de Regresión Logística Optimizada con Descenso de Gradiente Adam "
        "(Adaptive Moment Estimation), regularización L2 y feature engineering no lineal. "
        "La probabilidad de aprobación P(Y=1|X) se calcula mediante la función sigmoide:"
    )

    doc.add_paragraph("P(Y = 1 | X) = σ(z) = 1 / (1 + exp(-z))\n"
                      "z = b + Σ (w_j · x_j_norm)")

    doc.add_paragraph(
        "Ventajas de esta formulación para el ecosistema DriveMaster:\n"
        "1. Inferencia ultrarrápida (< 1 ms por solicitud) con complejidad temporal O(n).\n"
        "2. Portabilidad matemática total: Se ejecuta en Python estándar y en un motor embebido en Java Spring Boot.\n"
        "3. Explicabilidad Directa (XAI): Cada variable x_j aporta un valor w_j · x_j_norm al logit, lo que permite "
        "explicar con exactitud qué factores suman o restan probabilidad en cada cotización individual."
    )

    # --- SECCIÓN 3: INGENIERÍA DE VARIABLES ---
    h_sec3 = doc.add_heading("3. Ingeniería de Características e Interacciones", level=1)
    h_sec3.style.font.color.rgb = COLOR_PRIMARIO

    table = doc.add_table(rows=1, cols=4)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False

    headers = ["Variable / Característica", "Tipo de Dato", "Significado en DriveMaster", "Impacto / Coeficiente"]
    hdr_cells = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr_cells[i].text = h
        set_cell_background(hdr_cells[i], COLOR_HEADER_TABLA)
        set_cell_margins(hdr_cells[i], 140, 140, 180, 180)
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        for run in p.runs:
            run.font.bold = True
            run.font.color.rgb = RGBColor(255, 255, 255)
            run.font.size = Pt(9.5)

    criterios_data = [
        ("calificacion_taller", "Numérica (1.0 - 5.0)", "Reputación y rating promedio obtenido por el taller a partir de reseñas de clientes.", "ALTO POSITIVO (+2.688): Talleres con alta reputación generan confianza contundente."),
        ("antiguedad_x_precio", "Interacción continua", "Producto de la antigüedad del carro (2026 - año) por el total cotizado.", "CRÍTICO NEGATIVO (-1.756): Un valor alto en un vehículo antiguo dispara la tasa de rechazo."),
        ("log_tiempo_respuesta", "Logarítmica continua", "Transformación cóncava ln(horas + 1) del tiempo de respuesta del taller.", "CRÍTICO NEGATIVO (-1.643): Responder en las primeras 3h maximiza conversión; superar 24h perjudica fuertemente."),
        ("log_distancia", "Logarítmica continua", "Distancia geográfica cliente-taller en escala logarítmica.", "NEGATIVO (-1.430): Distancias considerables desincentivan la aceptación por costo y tiempo de traslado."),
        ("total_con_iva", "Numérica (COP)", "Monto total facturable con 19% de IVA y repuestos.", "NEGATIVO (-1.195): Presupuestos que superan los rangos promedio experimentan mayor sensibilidad al precio."),
        ("monto_mano_obra", "Numérica (COP)", "Tarifa cobrada por el taller por concepto de servicio técnico mecánico.", "NEGATIVO (-0.622): Sensibilidad moderada a la mano de obra cotizada."),
        ("num_tiendas", "Numérica entera", "Número de tiendas de autopartes involucradas para abastecer los repuestos.", "NEGATIVO (-0.582): Dispersión logística genera incertidumbre en tiempos de entrega."),
        ("reputacion_vs_distancia", "Ratio de interacción", "Calificación del taller dividida por la distancia geográfica logarítmica.", "Mide la disposición del cliente a desplazarse si la reputación del taller es sobresaliente."),
        ("marca_vehiculo", "Categórica (One-Hot)", "Marca del auto (Chevrolet, Renault, Mazda, Toyota, Kia, Nissan, etc.).", "Segmenta el poder adquisitivo esperado y costo relativo de repuestos por fabricante."),
        ("tipo_servicio", "Categórica (One-Hot)", "Categoría del trabajo (Frenos, Suspensión, Mantenimiento preventivo, Motor, etc.).", "Distingue servicios de alta urgencia (frenos/motor) frente a servicios electivos.")
    ]

    for row_idx, fila in enumerate(criterios_data):
        row_cells = table.add_row().cells
        bg_color = "FFFFFF" if row_idx % 2 == 0 else COLOR_FONDO_TABLA
        for col_idx, valor in enumerate(fila):
            row_cells[col_idx].text = valor
            set_cell_background(row_cells[col_idx], bg_color)
            set_cell_margins(row_cells[col_idx], 100, 100, 140, 140)
            p = row_cells[col_idx].paragraphs[0]
            if col_idx == 0:
                p.runs[0].font.bold = True
            for r in p.runs:
                r.font.size = Pt(8.5)

    doc.add_paragraph().paragraph_format.space_after = Pt(10)

    # --- SECCIÓN 4: ARQUITECTURA DE INTEGRACIÓN Y EXPLICABILIDAD ---
    h_sec4 = doc.add_heading("4. Arquitectura de Integración y Explicabilidad (XAI 2.0)", level=1)
    h_sec4.style.font.color.rgb = COLOR_PRIMARIO

    doc.add_paragraph(
        "Para garantizar alta resiliencia y cero fallos operativos, la solución opera con una arquitectura redundante:"
    )

    b_a1 = doc.add_paragraph(style='List Bullet')
    r = b_a1.add_run("Microservicio en Python (ml/app.py): ")
    r.bold = True
    b_a1.add_run("Expone /predict y /metricas en el puerto 8000, calculando en tiempo real las contribuciones de cada factor positivo y de riesgo.")

    b_a2 = doc.add_paragraph(style='List Bullet')
    r = b_a2.add_run("Motor Embebido en Java Spring Boot (PredictorCotizacionService): ")
    r.bold = True
    b_a2.add_run("Consume el microservicio Python. Si este no responde, activa inmediatamente el motor de fallback en memoria Java utilizando los pesos calibrados (modelo_pesos.json).")

    b_a3 = doc.add_paragraph(style='List Bullet')
    r = b_a3.add_run("Frontend en React (AlertaPrediccionCotizacion.jsx): ")
    r.bold = True
    b_a3.add_run("Renderiza el semáforo visual de riesgo, la barra de probabilidad y el desglose de 'Factores a Favor' vs 'Puntos de Riesgo' con sugerencias concretas.")

    # --- SECCIÓN 5: MÉTRICAS Y RESULTADOS ---
    h_sec5 = doc.add_heading("5. Evaluación del Modelo y Métricas Obtenidas", level=1)
    h_sec5.style.font.color.rgb = COLOR_PRIMARIO

    doc.add_paragraph(
        "El modelo fue entrenado con 2.500 registros y evaluado rigurosamente en un conjunto de prueba independiente "
        "del 20% (500 cotizaciones no vistas durante el entrenamiento):"
    )

    t_met = doc.add_table(rows=1, cols=3)
    t_met.alignment = WD_TABLE_ALIGNMENT.CENTER
    t_met.rows[0].cells[0].text = "Métrica"
    t_met.rows[0].cells[1].text = "Resultado Obtenido"
    t_met.rows[0].cells[2].text = "Interpretación Académica y de Negocio"
    for c in t_met.rows[0].cells:
        set_cell_background(c, COLOR_HEADER_TABLA)
        set_cell_margins(c, 120, 120, 160, 160)
        p = c.paragraphs[0]
        for r in p.runs:
            r.font.bold = True
            r.font.color.rgb = RGBColor(255, 255, 255)
            r.font.size = Pt(9.5)

    metricas_rows = [
        ("Área Bajo la Curva ROC (ROC-AUC)", "0.9715", "Excelente capacidad de discriminación entre cotizaciones exitosas y rechazadas."),
        ("Sensibilidad (Recall)", "96.81 %", "Captura el 96.8% de todas las cotizaciones aprobadas, minimizando falsos negativos."),
        ("Exactitud (Accuracy)", "91.80 %", "Tasa de acierto global sobresaliente en el conjunto de prueba independiente."),
        ("Puntuación F1 (F1-Score)", "92.22 %", "Balance armónico óptimo entre precisión y exhaustividad."),
        ("Precisión (Precision)", "88.04 %", "Cuando el modelo predice aprobación, acierta en el 88 % de las ocasiones."),
        ("Umbral de Decisión Calibrado", "0.40", "Umbral óptimo obtenido mediante optimización de la métrica F1 en validación."),
        ("Verdaderos Positivos (TP)", "243", "Cotizaciones aprobadas predichas con precisión."),
        ("Verdaderos Negativos (TN)", "216", "Cotizaciones rechazadas anticipadas a tiempo.")
    ]

    for idx, (m, val, desc) in enumerate(metricas_rows):
        rc = t_met.add_row().cells
        bg = "FFFFFF" if idx % 2 == 0 else COLOR_FONDO_TABLA
        for col_i, texto in enumerate([m, val, desc]):
            rc[col_i].text = texto
            set_cell_background(rc[col_i], bg)
            set_cell_margins(rc[col_i], 90, 90, 120, 120)
            p = rc[col_i].paragraphs[0]
            if col_i == 0:
                p.runs[0].font.bold = True
            if col_i == 1:
                p.runs[0].font.bold = True
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for r in p.runs:
                r.font.size = Pt(9)

    doc.add_paragraph().paragraph_format.space_after = Pt(10)

    # --- SECCIÓN 6: PASO A PASO ---
    h_sec6 = doc.add_heading("6. Flujo de Uso en la Aplicación (Paso a Paso)", level=1)
    h_sec6.style.font.color.rgb = COLOR_PRIMARIO

    pasos = [
        ("Paso 1: Recepción de la Solicitud", "El cliente sube la información de su vehículo, describe la necesidad y solicita presupuesto."),
        ("Paso 2: Formulación y Previsualización", "El taller ingresa mano de obra y selecciona repuestos de tiendas vinculadas."),
        ("Paso 3: Evaluación Predictiva y Explicabilidad Instantánea", "Al presionar 'Calcular', la IA evalúa las 14 variables e interacciones y responde en < 10 ms."),
        ("Paso 4: Análisis de Factores de Éxito y Riesgo", "El taller revisa los 'Factores a Favor' (ej. respuesta ágil, reputación) y los 'Puntos de Riesgo' (ej. precio para la antigüedad del auto)."),
        ("Paso 5: Optimización y Emisión", "El taller ajusta el presupuesto si lo desea y emite la cotización al cliente con alta certidumbre de aprobación.")
    ]

    for titulo, desc in pasos:
        p = doc.add_paragraph()
        r = p.add_run(f"{titulo}\n")
        r.bold = True
        r.font.color.rgb = COLOR_SECUNDARIO
        p.add_run(desc)

    # Guardar documento en la raíz del proyecto
    OUTPUT_PATH = os.path.join(r"c:\Users\Sebastian Ortega\Desktop\DriveMaster\Drivemaster", "Modelo_Predictivo_DriveMaster.docx")
    doc.save(OUTPUT_PATH)
    print(f"Documento Word generado exitosamente en: {OUTPUT_PATH}")

if __name__ == "__main__":
    crear_documento()
