package com.harborclaims.modern.web;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Which controls a page shows for the signed-in user. The security chain enforces the same rules; this only hides links. */
final class Roles {

    private Roles() {
    }

    static Set<String> of(Authentication a) {
        if (a == null) {
            return Set.of();
        }
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    static boolean any(Authentication a, String... roles) {
        Set<String> have = of(a);
        for (String r : roles) {
            if (have.contains("ROLE_" + r)) {
                return true;
            }
        }
        return false;
    }

    static boolean canEditCustomers(Authentication a) {
        return any(a, "ADMIN", "SUPERVISOR", "CSR");
    }

    static boolean canDeleteCustomers(Authentication a) {
        return any(a, "ADMIN");
    }
}
