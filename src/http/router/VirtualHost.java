package http.router;

import java.util.List;

import config.ServerConfig;

public class VirtualHost {

    // name-based match first (curl --resolve test.com:80:127.0.0.1),
    // then whichever block said "default_server on;",
    // then just the first block bound to this address.
    public static ServerConfig resolve(List<ServerConfig> candidates, String hostHeader) {
        System.out.println("server configs candidates: ");
        for(ServerConfig sc: candidates){
            System.out.println(sc.toString());
        }

        String name = (hostHeader != null) ? hostHeader.split(":")[0] : null;

        if (name != null) {
            for (ServerConfig sc : candidates) {
                if (name.equalsIgnoreCase(sc.getServerName())) {
                    return sc;
                }
            }
        }

        for (ServerConfig sc : candidates) {
            if (sc.isDefaultServer()) {
                return sc;
            }
        }

        return candidates.get(0);
    }
}