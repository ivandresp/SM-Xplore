# Gateway HTTP(S) del stack SM-Xplore.
#
# Imagen de desarrollo: nginx unprivileged (uid 101, sin root) + openssl. El binario
# de openssl solo se usa al arrancar para generar el certificado autofirmado local
# (ver entrypoint.sh). La configuracion de nginx y las rutas NO se copian aqui: se
# montan desde el repositorio en compose.yaml, de forma que editarlas solo requiera
# reiniciar el contenedor.
#
# PLAT-12: el master de nginx corre como usuario `nginx` (uid 101), no como root.
# Por eso escucha en 8080/8443 (puertos no privilegiados) y escribe pid/logs/temps
# en /tmp; los puertos publicados del host no cambian (8088/8443). En produccion
# (deploy/prod/compose.yaml) se usa la misma imagen unprivileged con los
# certificados inyectados como secretos: no hay que generar nada.
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Solo para el certificado autofirmado de desarrollo (TLS_SELF_SIGNED=true).
USER root
RUN apk add --no-cache openssl \
    && rm -f /etc/nginx/conf.d/default.conf
USER nginx

# El server por defecto de la imagen no se usa: las rutas se montan en conf.d/.
COPY --chmod=0755 entrypoint.sh /docker-entrypoint.d/10-smxplore-certs.sh

EXPOSE 8080 8443
