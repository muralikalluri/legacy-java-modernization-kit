package com.harborclaims.modern.customers;

import static org.assertj.core.api.Assertions.assertThat;

import com.harborclaims.modern.PostgresIT;
import com.harborclaims.modern.claims.ClaimRow;
import com.harborclaims.modern.claims.ClaimSearchRepository;
import com.harborclaims.modern.common.Page;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Search semantics against the real seed. The same expectations the characterisation suite pins through HTTP. */
@SpringBootTest
class CustomerRepositoryIT extends PostgresIT {

    private static final String INJECTION = "zzz%') OR 1=1 OR 'a' LIKE lower('%";

    @Autowired
    CustomerRepository customers;

    @Autowired
    ClaimSearchRepository claims;

    private static List<Integer> ids(Page<Customer> p) {
        return p.items().stream().map(Customer::id).toList();
    }

    private static List<Integer> claimIds(Page<ClaimRow> p) {
        return p.items().stream().map(ClaimRow::id).toList();
    }

    @Test
    void aLastNameFindsItsCustomersOrderedByLastNameFirstNameThenId() {
        Page<Customer> p = customers.search("almeida", 1);
        assertThat(ids(p)).containsExactly(1, 21, 11);
        assertThat(p.total()).isEqualTo(3);
    }

    @Test
    void searchIsCaseInsensitiveAndMatchesEmailAndFirstName() {
        assertThat(ids(customers.search("ALMEIDA", 1))).containsExactly(1, 21, 11);
        assertThat(ids(customers.search("amara.almeida@", 1))).containsExactly(1);
        assertThat(ids(customers.search("amara", 1))).contains(1, 13, 25);
        assertThat(ids(customers.search("Amara Almeida", 1))).isEmpty();
    }

    @Test
    void percentAndUnderscoreStayWildcards() {
        assertThat(ids(customers.search("almeid_", 1))).containsExactly(1, 21, 11);
        assertThat(ids(customers.search("almei%a", 1))).containsExactly(1, 21, 11);
        assertThat(ids(customers.search("%", 1))).isEqualTo(ids(customers.search("", 1)));
    }

    @Test
    void theUnfilteredFirstPageIsInTheDocumentedOrder() {
        assertThat(ids(customers.search("", 1))).containsExactly(1, 21, 11, 2, 22, 12, 13, 3, 23, 14);
        assertThat(customers.search("", 2).items()).hasSize(10);
    }

    @Test
    void anInjectionPayloadIsJustText() {
        assertThat(customers.search(INJECTION, 1).items()).isEmpty();
        assertThat(claims.search(INJECTION, "", 1).items()).isEmpty();
        assertThat(claims.search("", "PAID' OR '1'='1", 1).items()).isEmpty();
    }

    @Test
    void anApostropheIsHarmless() {
        assertThat(customers.search("O'Brien", 1).items()).isEmpty();
        assertThat(claims.search("O'Brien", "", 1).items()).isEmpty();
        assertThat(claims.search("", "PAID'", 1).items()).isEmpty();
    }

    @Test
    void aPagePastTheEndIsEmpty() {
        Page<Customer> p = customers.search("almeida", 99);
        assertThat(p.items()).isEmpty();
        assertThat(p.hasNext()).isFalse();
    }

    @Test
    void claimsAreFoundByNumberOrLastNameInIdOrder() {
        assertThat(claimIds(claims.search("CLM-000007", "", 1))).containsExactly(7);
        assertThat(claimIds(claims.search("clm-000007", "", 1))).containsExactly(7);
        assertThat(claimIds(claims.search("CLM-00000", "", 1))).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(claimIds(claims.search("almeida", "", 1))).startsWith(1, 11, 21, 31, 37, 47, 57);
        assertThat(claims.search("Amara", "", 1).items()).isEmpty();
        assertThat(claims.search("POL-000001", "", 1).items()).isEmpty();
    }

    @Test
    void theStatusFilterIsExactAndNotCaseFolded() {
        assertThat(claimIds(claims.search("CLM-00000", "UNDER_REVIEW", 1))).containsExactly(1, 7);
        assertThat(claims.search("", "PAID", 1).items()).allMatch(c -> c.status().equals("PAID"));
        assertThat(claims.search("", "paid", 1).items()).isEmpty();
        assertThat(claims.search("", "NOPE", 1).items()).isEmpty();
        assertThat(claimIds(claims.search("", "", 1))).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void customerDetailsAndPoliciesForASeededCustomer() {
        Customer c = customers.findById(1).orElseThrow();
        assertThat(c.fullName()).isEqualTo("Amara Almeida");
        assertThat(c.email()).isEqualTo("amara.almeida@example.test");
        assertThat(c.dateOfBirth()).isEqualTo("1961-02-01");
        assertThat(customers.policies(1)).extracting(PolicyRow::policyNumber).containsExactly("POL-000001", "POL-000031");
        assertThat(customers.policies(1).get(0).coverageLimitDisplay()).isEqualTo("50,000.00");
        assertThat(customers.policyCount(1)).isEqualTo(2);
        assertThat(customers.findById(999999)).isEmpty();
    }
}
