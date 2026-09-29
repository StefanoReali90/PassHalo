package org.spring.passhalo.notification.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class SmtpHostValidator {
    // A configurable SMTP server must not become a route into the server's private network.
    public void validate(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || isRestrictedAddress(address)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Il server SMTP deve avere un indirizzo pubblico");
                }
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host SMTP non raggiungibile");
        }
    }

    private static boolean isRestrictedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
                    || (first == 198 && (second == 18 || second == 19));
        }
        return (bytes[0] & 0xfe) == 0xfc; // IPv6 unique local addresses.
    }
}
