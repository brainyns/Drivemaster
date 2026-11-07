"""
Generador de Dataset Sintético de Cotizaciones para DriveMaster (Python Pure Standard Library)
Proyecto de Aula: Modelo predictivo para estimar la probabilidad de aprobación de una cotización.
Integrantes: Brainy Blanco Hernandez, Sebastian Ortega Zarza, Juan Andres Cantillo Leon, Elias Daniel Puello Florez.
"""

import csv
import math
import os
import random

random.seed(42)

NUM_REGISTROS = 5000

MARCAS_MODELOS = {
    "Chevrolet": ["Spark GT", "Sail", "Onix", "Tracker", "Aveo", "Corsa"],
    "Renault": ["Logan", "Sandero", "Duster", "Kwid", "Clio", "Stepway"],
    "Mazda": ["Mazda 2", "Mazda 3", "CX-30", "CX-5", "Allegro", "Mazda 6"],
    "Toyota": ["Corolla", "Hilux", "Prado", "Yaris", "Fortuner", "Rav4"],
    "Kia": ["Picanto", "Rio", "Sportage", "Cerato", "Soul"],
    "Nissan": ["March", "Versa", "Frontier", "Kicks", "Sentra"],
    "Hyundai": ["i10", "Accent", "Tucson", "Creta", "Getz"],
    "Ford": ["Fiesta", "EcoSport", "Ranger", "Explorer", "Focus"]
}

TIPOS_SERVICIO = [
    "MANTENIMIENTO_PREVENTIVO",
    "FRENOS",
    "SUSPENSION",
    "MOTOR_AFINAMIENTO",
    "SISTEMA_ELECTRICO",
    "DIAGNOSTICO_GENERAL",
    "EMBRAGUE_TRANSMISION"
]

CIUDADES = ["Barranquilla", "Soledad", "Puerto Colombia", "Malambo", "Cartagena", "Santa Marta"]
CIUDADES_PESOS = [0.55, 0.20, 0.10, 0.05, 0.05, 0.05]

TARIFAS_MANO_OBRA = {
    "DIAGNOSTICO_GENERAL": (40000, 90000),
    "MANTENIMIENTO_PREVENTIVO": (80000, 180000),
    "FRENOS": (80000, 170000),
    "SISTEMA_ELECTRICO": (90000, 240000),
    "SUSPENSION": (120000, 300000),
    "MOTOR_AFINAMIENTO": (150000, 420000),
    "EMBRAGUE_TRANSMISION": (250000, 650000)
}

# Modificador de utilidad según criticidad del servicio
SERVICIOS_URGENCIA = {
    "FRENOS": +0.40,               # Seguridad crítica: alta tasa de aprobación
    "EMBRAGUE_TRANSMISION": +0.35,  # Inmovilización: el cliente necesita repararlo
    "MOTOR_AFINAMIENTO": +0.20,     # Falla perceptible en marcha
    "SISTEMA_ELECTRICO": +0.10,     # Batería/arranque
    "SUSPENSION": 0.0,              # Desgaste progresivo
    "MANTENIMIENTO_PREVENTIVO": -0.20, # Mayor comparación de precios
    "DIAGNOSTICO_GENERAL": -0.30       # Inspección previa, alta sensibilidad
}

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_CSV = os.path.join(BASE_DIR, "dataset_cotizaciones_drivemaster.csv")

headers = [
    "id_cotizacion",
    "marca_vehiculo",
    "modelo_vehiculo",
    "anio_vehiculo",
    "ciudad_cliente",
    "distancia_km",
    "tipo_servicio",
    "cantidad_repuestos",
    "num_tiendas",
    "monto_repuestos",
    "monto_mano_obra",
    "total_con_iva",
    "proporcion_repuestos",
    "tiempo_respuesta_horas",
    "calificacion_taller",
    "num_calificaciones",
    "aprobacion_cotizacion"
]

filas = []
aprobadas = 0

for i in range(NUM_REGISTROS):
    marca = random.choice(list(MARCAS_MODELOS.keys()))
    modelo = random.choice(MARCAS_MODELOS[marca])
    
    # Rango amplio de años para abarcar vehículos antiguos (ej. 2000-2005) y modernos (2020-2025)
    anio = random.randint(1998, 2024)
    antiguedad = 2026 - anio
    
    ciudad = random.choices(CIUDADES, weights=CIUDADES_PESOS)[0]
    if ciudad == "Barranquilla":
        distancia_km = round(random.uniform(0.5, 12.0), 1)
    elif ciudad in ["Soledad", "Puerto Colombia"]:
        distancia_km = round(random.uniform(7.0, 20.0), 1)
    else:
        distancia_km = round(random.uniform(18.0, 48.0), 1)
        
    tipo_servicio = random.choice(TIPOS_SERVICIO)
    
    if tipo_servicio == "DIAGNOSTICO_GENERAL":
        cantidad_repuestos = random.choice([0, 1])
    elif tipo_servicio in ["FRENOS", "MANTENIMIENTO_PREVENTIVO"]:
        cantidad_repuestos = random.randint(1, 4)
    else:
        cantidad_repuestos = random.randint(2, 6)
        
    num_tiendas = 0 if cantidad_repuestos == 0 else min(random.randint(1, 3), cantidad_repuestos)
    
    factor_gama = 1.35 if marca in ["Toyota", "Mazda", "Ford"] else 1.0
    
    if cantidad_repuestos > 0:
        costo_base_repuesto = random.uniform(45000, 220000) * factor_gama
        monto_repuestos = round(cantidad_repuestos * costo_base_repuesto, -3)
    else:
        monto_repuestos = 0.0
        
    rango_mo = TARIFAS_MANO_OBRA[tipo_servicio]
    monto_mano_obra = round(random.uniform(rango_mo[0], rango_mo[1]), -3)
    
    subtotal = monto_repuestos + monto_mano_obra
    iva = round(subtotal * 0.19, -2)
    total_con_iva = round(subtotal + iva, -2)
    
    proporcion_repuestos = round(monto_repuestos / total_con_iva, 3) if total_con_iva > 0 else 0.0
    
    # Tiempo de respuesta en horas
    u = random.random()
    tiempo_respuesta_horas = round(-8.0 * math.log(1.0 - u) + 0.4, 1)
    tiempo_respuesta_horas = min(tiempo_respuesta_horas, 72.0)
    
    # Modelar talleres con y sin calificaciones (25% de talleres nuevos o sin reseñas)
    es_taller_nuevo = (random.random() < 0.25)
    if es_taller_nuevo:
        calificacion_taller = 0.0
        num_calificaciones = 0
    else:
        calificacion_taller = round(random.uniform(3.6, 5.0), 1)
        num_calificaciones = random.randint(2, 120)
    
    # Modelo Econométrico de Utilidad del Consumidor Calibrado (McFadden Random Utility)
    # V: utilidad sistemática calibrada para lograr una distribución balanceada (~50% aprobación)
    V = 2.05
    
    # 1. Sensibilidad al precio total
    V -= 1.35 * (total_con_iva / 1000000.0)
    
    # 2. Interacción vehículo antiguo vs costo elevado
    antig_factor = (antiguedad / 12.0) * (total_con_iva / 900000.0)
    V -= 0.45 * antig_factor
        
    # 3. Tiempo de respuesta (demora reduce interés)
    V -= 0.55 * (math.log(tiempo_respuesta_horas + 1.0) / math.log(15.0 + 1.0))
    
    # 4. Proximidad geográfica
    V -= 0.45 * (math.log(distancia_km + 1.0) / math.log(20.0 + 1.0))
    
    # 5. Reputación del taller (o fricción si no tiene opiniones aún)
    if not es_taller_nuevo:
        V += 0.85 * ((calificacion_taller - 4.1) / 0.8)
    else:
        V -= 0.25
        
    # 6. Criticidad y urgencia según el tipo de servicio
    V += SERVICIOS_URGENCIA.get(tipo_servicio, 0.0)
    
    # 7. Dispersión logística si requiere 3 o más tiendas
    if num_tiendas >= 3:
        V -= 0.25
        
    # Error estocástico no observable (Gumbel / Logístico estándar con sigma calibrada)
    epsilon = random.gauss(0, 0.65)
    utilidad_neta = V + epsilon
    
    aprobacion = 1 if utilidad_neta >= 0.0 else 0
    if aprobacion == 1:
        aprobadas += 1
        
    filas.append([
        f"COT-{1000 + i}",
        marca,
        modelo,
        anio,
        ciudad,
        distancia_km,
        tipo_servicio,
        cantidad_repuestos,
        num_tiendas,
        monto_repuestos,
        monto_mano_obra,
        total_con_iva,
        proporcion_repuestos,
        tiempo_respuesta_horas,
        calificacion_taller,
        num_calificaciones,
        aprobacion
    ])

with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(headers)
    writer.writerows(filas)

tasa_aprobacion = round(aprobadas / NUM_REGISTROS * 100, 2)
print(f"Dataset generado exitosamente: {OUTPUT_CSV}")
print(f"Total registros: {NUM_REGISTROS}")
print(f"Cotizaciones aprobadas: {aprobadas} ({tasa_aprobacion}%) | Rechazadas: {NUM_REGISTROS - aprobadas} ({round(100 - tasa_aprobacion, 2)}%)")
