# Gateway HTTP(S) del stack SM-Xplore.
#
# Imagen de desarrollo: nginx alpine + openssl. El binario de openssl solo se usa al
# arrancar para generar el certificado autofirmado local (ver entrypoint.sh). La
# configuracion de nginx y las rutas NO se copian aqui: se montan desde el repositorio
# en compose.yaml, de forma que editarlas solo requiera reiniciar el contenedor.
#
# En produccion (deploy/prod/compose.yaml) se usa nginx:1.27-alpine sin construir esta
# imagen, porque los certificados se inyectan como secretos y no hay que generarlos.
FROM nginx:1.27-alpine

RUN apk add --no-cache openssl

# El server por defecto de la imagen no se usa: las rutas se montan en conf.d/.
RUN rm -f /etc/nginx/conf.d/default.conf

COPY entrypoint.sh /docker-entrypoint.d/10-smxplore-certs.sh
RUN chmod +x /docker-entrypoint.d/10-smxplore-certs.sh
