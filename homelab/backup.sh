#!/usr/bin/env bash
# Respaldo de MongoDB y PostgreSQL de DriveMaster.
#
# Uso:      ./backup.sh [carpeta_destino]          (por defecto ./backups)
# Con cron: 0 3 * * * /opt/drivemaster/homelab/backup.sh /mnt/nas/backups/drivemaster
#
# Guarda un .archive.gz de Mongo y un .sql.gz de Postgres con fecha, y borra los de más de 14 días.
# Recuerda tu plan: "Almacenamiento ≠ Backup". Lleva esta carpeta a OTRO equipo (NAS, G52, disco externo).

set -euo pipefail

DIR_SCRIPT="$(cd "$(dirname "$0")" && pwd)"
DESTINO="${1:-$DIR_SCRIPT/backups}"
DIAS_RETENCION=14
FECHA="$(date +%Y-%m-%d_%H%M)"

cd "$DIR_SCRIPT"
set -a; source ./.env; set +a
mkdir -p "$DESTINO"

echo "[$(date)] Respaldando MongoDB..."
docker compose exec -T mongo mongodump \
  --username "$MONGO_USER" --password "$MONGO_PASSWORD" --authenticationDatabase admin \
  --db "${MONGO_DB:-drivemasterDB}" --archive --gzip \
  > "$DESTINO/mongo_${FECHA}.archive.gz"

echo "[$(date)] Respaldando PostgreSQL..."
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "${POSTGRES_DB:-drivemaster}" \
  | gzip > "$DESTINO/postgres_${FECHA}.sql.gz"

find "$DESTINO" -name 'mongo_*.archive.gz' -mtime +"$DIAS_RETENCION" -delete
find "$DESTINO" -name 'postgres_*.sql.gz'  -mtime +"$DIAS_RETENCION" -delete

echo "[$(date)] Listo en $DESTINO"
ls -lh "$DESTINO" | tail -n 4
