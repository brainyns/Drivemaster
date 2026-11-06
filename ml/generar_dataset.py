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

NUM_REGISTROS = 2500

MARCAS_MODELOS = {
    "Chevrolet": ["Spark GT", "Sail", "Onix", "Tracker"],
    "Renault": ["Logan", "Sandero", "Duster", "Kwid"],
    "Mazda": ["Mazda 2", "Mazda 3", "CX-30", "CX-5"],
    "Toyota": ["Corolla", "Hilux", "Prado", "Yaris"],
    "Kia": ["Picanto", "Rio", "Sportage", "Cerato"],
    "Nissan": ["March", "Versa", "Frontier", "Kicks"],
    "Hyundai": ["i10", "Accent", "Tucson", "Creta"],
    "Ford": ["Fiesta", "EcoSport", "Ranger", "Explorer"]
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
    "DIAGNOSTICO_GENERAL": (40000, 80000),
    "MANTENIMIENTO_PREVENTIVO": (80000, 160000),
    "FRENOS": (70000, 150000),
    "SISTEMA_ELECTRICO": (90000, 220000),
    "SUSPENSION": (120000, 280000),
    "MOTOR_AFINAMIENTO": (150000, 380000),
    "EMBRAGUE_TRANSMISION": (250000, 600000)
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
    "aprobacion_cotizacion"
]

filas = []
aprobadas = 0

for i in range(NUM_REGISTROS):
    marca = random.choice(list(MARCAS_MODELOS.keys()))
    modelo = random.choice(MARCAS_MODELOS[marca])
    anio = random.randint(2008, 2024)
    antiguedad = 2026 - anio
    ciudad = random.choices(CIUDADES, weights=CIUDADES_PESOS)[0]
    
    if ciudad == "Barranquilla":
        distancia_km = round(random.uniform(0.8, 12.0), 1)
    elif ciudad in ["Soledad", "Puerto Colombia"]:
        distancia_km = round(random.uniform(8.0, 22.0), 1)
    else:
        distancia_km = round(random.uniform(18.0, 50.0), 1)
        
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
        costo_base_repuesto = random.uniform(45000, 200000) * factor_gama
        monto_repuestos = round(cantidad_repuestos * costo_base_repuesto, -3)
    else:
        monto_repuestos = 0.0
        
    rango_mo = TARIFAS_MANO_OBRA[tipo_servicio]
    monto_mano_obra = round(random.uniform(rango_mo[0], rango_mo[1]), -3)
    
    subtotal = monto_repuestos + monto_mano_obra
    iva = round(subtotal * 0.19, -2)
    total_con_iva = round(subtotal + iva, -2)
    
    proporcion_repuestos = round(monto_repuestos / total_con_iva, 3) if total_con_iva > 0 else 0.0
    
    # Tiempo de respuesta con distribución exponencial
    u = random.random()
    tiempo_respuesta_horas = round(-10.0 * math.log(1.0 - u) + 0.5, 1)
    tiempo_respuesta_horas = min(tiempo_respuesta_horas, 72.0)
    
    calificacion_taller = round(random.uniform(3.7, 5.0), 1)
    
    # Modelo Econométrico de Utilidad del Consumidor (Random Utility Model - McFadden)
    # V: componente sistemático de valor
    V = 2.85
    # Elasticidad al precio total
    V -= 1.65 * (total_con_iva / 1200000.0)
    # Sensibilidad a la demora en respuesta (efecto logarítmico cóncavo)
    V -= 1.35 * (math.log(tiempo_respuesta_horas + 1.0) / math.log(18.0 + 1.0))
    # Prestigio y reputación del taller
    V += 1.40 * ((calificacion_taller - 4.1) / 0.9)
    # Fricción por distancia geográfica
    V -= 1.05 * (math.log(distancia_km + 1.0) / math.log(25.0 + 1.0))
    # Interacción: vehículo de mayor antigüedad con presupuesto alto
    antig_factor = (antiguedad / 10.0) * (total_con_iva / 900000.0)
    V -= 0.65 * antig_factor
    # Fricción logística si requiere repuestos de múltiples tiendas
    if num_tiendas >= 3:
        V -= 0.35
        
    # Preferencia individual no observable (término de error estocástico gaussiano)
    epsilon = random.gauss(0, 0.40)
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
