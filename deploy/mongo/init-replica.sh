#!/bin/sh
# Inicia el replica set rs0 de un solo nodo y espera a que el nodo sea primario.
#
# ms-02 y ms-04 usan transacciones de MongoDB para su outbox: sin replica set el driver
# rechaza las sesiones. El contenedor corre una sola vez (restart: "no") y los servicios
# dependen de el con service_completed_successfully.
set -eu

HOST="${MONGO_HOST:-mongo:27017}"

echo "[mongo-init] esperando a que mongod responda en $HOST"
i=0
while [ "$i" -lt 60 ]; do
  if mongosh --host "$HOST" --quiet --eval 'db.adminCommand({ ping: 1 }).ok' 2>/dev/null | grep -q 1; then
    break
  fi
  i=$((i + 1))
  sleep 1
done

echo "[mongo-init] iniciando el replica set rs0"
mongosh --host "$HOST" --quiet --eval '
try {
  rs.status();
} catch (e) {
  rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "mongo:27017" }] });
}
'

echo "[mongo-init] esperando al primario"
i=0
while [ "$i" -lt 60 ]; do
  if mongosh --host "$HOST" --quiet --eval 'db.hello().isWritablePrimary' 2>/dev/null | grep -q true; then
    echo "[mongo-init] primario elegido"
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done

echo "[mongo-init] el replica set no eligio primario a tiempo" >&2
exit 1
