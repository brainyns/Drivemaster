#!/usr/bin/env bash
# Respaldo de MongoDB y PostgreSQL de DriveMaster.
#
# Uso:      ./backup.sh [carpeta_destino]          (por defecto ./backups, junto al script)
# Con cron: 0 3 * * * /opt/drivemaster/backup.sh /mnt/nas/backups/drivemaster
#
# Funciona con el stack desplegado desde Portainer: no necesita el .env ni el docker-compose.yml.
# Busca los contenedores por las etiquetas que pone Docker Compose (nombre del stack + servicio) y
# usa el usuario y la contraseña que ya tienen dentro (MONGO_INITDB_ROOT_*, POSTGRES_*).
# Si tu stack en Portainer no se llama "drivemaster":  STACK=otro_nombre ./backup.sh
#
# Guarda un .archive.gz de Mongo y un .sql.gz de Postgres con fecha, y borra los de más de 14 días.
# Recuerda tu plan: "Almacenamiento ≠ Backup". Lleva esta carpeta a OTRO equipo (NAS, G52, disco externo).

set -euo pipefail

DIR_SCRIPT="$(cd "$(dirname "$0")" && pwd)"
DESTINO="${1:-$DIR_SCRIPT/backups}"
STACK="${STACK:-drivemaster}"
DIAS_RETENCION=14
FECHA="$(date +%Y-%m-%d_%H%M)"

contenedor() {
  local id
  id="$(docker ps -q \
    --filter "label=com.docker.compose.project=$STACK" \
    --filter "label=com.docker.compose.service=$1")"
  if [ -z "$id" ]; then
    echo "No encontré el contenedor '$1' del stack '$STACK' en ejecución." >&2
    exit 1
  fi
  echo "$id"
}

MONGO="$(contenedor mongo)"
POSTGRES="$(contenedor postgres)"
mkdir -p "$DESTINO"

echo "[$(date)] Respaldando MongoDB..."
# Comillas simples: las variables se expanden DENTRO del contenedor, no aquí.
docker exec "$MONGO" sh -c 'mongodump \
  --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE" --archive --gzip --quiet' \
  > "$DESTINO/mongo_${FECHA}.archive.gz"

echo "[$(date)] Respaldando PostgreSQL..."
docker exec "$POSTGRES" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  | gzip > "$DESTINO/postgres_${FECHA}.sql.gz"

find "$DESTINO" -name 'mongo_*.archive.gz' -mtime +"$DIAS_RETENCION" -delete
find "$DESTINO" -name 'postgres_*.sql.gz'  -mtime +"$DIAS_RETENCION" -delete

echo "[$(date)] Listo en $DESTINO"
ls -lh "$DESTINO" | tail -n 4
