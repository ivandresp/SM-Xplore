package com.smxplore.users.infrastructure.adapter.in.web;

import com.smxplore.users.infrastructure.config.ClientAddressProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resuelve la IP del cliente sin confiar en encabezados reenviados por el propio cliente.
 *
 * <p>La conexión TCP siempre la termina el proxy inverso, por lo que {@code remoteAddress} es la IP del proxy. Sólo si
 * esa dirección pertenece a la lista de proxies de confianza se acepta {@code X-Real-IP} (que el gateway reescribe con
 * {@code $remote_addr}); el resto de encabezados, como {@code X-Forwarded-For}, nunca se leen porque el cliente puede
 * falsificarlos.
 */
public final class ClientAddressResolver {

    private static final String CLIENT_IP_HEADER = "X-Real-IP";
    private static final String UNKNOWN_CLIENT = "desconocido";

    private final List<CidrBlock> trustedProxies;

    public ClientAddressResolver(ClientAddressProperties properties) {
        this.trustedProxies =
                properties.trustedProxies().stream().map(CidrBlock::parse).toList();
    }

    /**
     * Devuelve la IP atribuible al cliente: {@code X-Real-IP} si la conexión viene de un proxy de confianza y el valor
     * es una IP literal; en cualquier otro caso, la dirección de la conexión.
     */
    public String resolve(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return UNKNOWN_CLIENT;
        }
        InetAddress remoteAddress = remote.getAddress();
        if (!isTrustedProxy(remoteAddress)) {
            return host(remoteAddress);
        }
        InetAddress forwarded = parseLiteral(exchange.getRequest().getHeaders().getFirst(CLIENT_IP_HEADER));
        return host(forwarded != null ? forwarded : remoteAddress);
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxies.stream().anyMatch(block -> block.contains(address));
    }

    private static InetAddress parseLiteral(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return null;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return InetAddress.ofLiteral(trimmed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String host(InetAddress address) {
        return address.getHostAddress();
    }

    /** Bloque CIDR de confianza; admite IPv4 e IPv6 y una IP suelta (equivale a /32 o /128). */
    private record CidrBlock(byte[] network, int prefixBits) {

        static CidrBlock parse(String value) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("La lista de proxies de confianza contiene una entrada vacía");
            }
            int slash = trimmed.indexOf('/');
            String addressPart = slash < 0 ? trimmed : trimmed.substring(0, slash);
            InetAddress address = parseLiteral(addressPart);
            if (address == null) {
                throw new IllegalArgumentException("Proxy de confianza inválido: " + value);
            }
            byte[] network = address.getAddress();
            int maxBits = network.length * 8;
            int prefixBits = slash < 0 ? maxBits : parsePrefix(trimmed.substring(slash + 1), maxBits, value);
            return new CidrBlock(network, prefixBits);
        }

        private static int parsePrefix(String value, int maxBits, String original) {
            try {
                int prefix = Integer.parseInt(value.trim());
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException("Proxy de confianza inválido: " + original);
                }
                return prefix;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Proxy de confianza inválido: " + original);
            }
        }

        boolean contains(InetAddress candidate) {
            byte[] other = candidate.getAddress();
            if (other.length != network.length) {
                return false;
            }
            int remaining = prefixBits;
            for (int index = 0; index < network.length && remaining > 0; index++) {
                int bits = Math.min(8, remaining);
                int mask = (0xFF << (8 - bits)) & 0xFF;
                if ((network[index] & mask) != (other[index] & mask)) {
                    return false;
                }
                remaining -= bits;
            }
            return true;
        }
    }
}
