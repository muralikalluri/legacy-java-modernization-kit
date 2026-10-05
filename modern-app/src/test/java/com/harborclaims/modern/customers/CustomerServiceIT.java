package com.harborclaims.modern.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harborclaims.modern.PostgresIT;
import com.harborclaims.modern.web.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
class CustomerServiceIT extends PostgresIT {

    @Autowired
    CustomerService service;

    @Autowired
    CustomerRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private int highestIdBefore;

    /** The database is shared by every test class in the run: remove what each test creates, so no test sees another's rows. */
    @BeforeEach
    void rememberHighestId() {
        highestIdBefore = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM customers", Integer.class);
    }

    @AfterEach
    void removeWhatTheTestCreated() {
        jdbc.update("DELETE FROM customers WHERE id > ?", highestIdBefore);
    }

    private static String unique(String label) {
        return label + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static CustomerForm form(String id, String last, String email) {
        return new CustomerForm(id, "Test", last, email, "", "", "", "", "", "");
    }

    @Test
    @WithMockUser(roles = "CSR")
    void validValuesAreTrimmedAndTheStateIsUpperCased() {
        String last = unique("Trim");
        CustomerService.SaveResult r = service.save(new CustomerForm("", "  Pat  ", last, "  " + last + "@example.test ", " 555-1234 ", "1990-01-02",
                " 1 Harbor Lane ", "  Dunmere ", "ny", " 12345 "));
        assertThat(r.ok()).isTrue();
        Customer c = service.get(r.id());
        assertThat(c.firstName()).isEqualTo("Pat");
        assertThat(c.email()).isEqualTo(last + "@example.test");
        assertThat(c.phone()).isEqualTo("555-1234");
        assertThat(c.state()).isEqualTo("NY");
        assertThat(c.city()).isEqualTo("Dunmere");
        assertThat(c.postalCode()).isEqualTo("12345");
    }

    @Test
    @WithMockUser(roles = "CSR")
    void blankOptionalFieldsAreStoredAsNull() {
        String last = unique("Blank");
        Customer c = service.get(service.save(form("", last, last + "@example.test")).id());
        assertThat(c.phone()).isNull();
        assertThat(c.dateOfBirth()).isNull();
        assertThat(c.state()).isNull();
    }

    @Test
    @WithMockUser(roles = "CSR")
    void validationFailuresComeBackAsMessagesAndStoreNothing() {
        String last = unique("Bad");
        CustomerService.SaveResult r = service.save(form("", last, "not-an-email"));
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).containsExactly("Email address is not valid");
        assertThat(repository.search(last, 1).items()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "CSR")
    void aDuplicateEmailInAnyCaseIsRejected() {
        String last = unique("Dup");
        assertThat(service.save(form("", last, last + "@example.test")).ok()).isTrue();
        CustomerService.SaveResult again = service.save(form("", unique("Dup2"), "  " + (last + "@EXAMPLE.test").toUpperCase() + " "));
        assertThat(again.ok()).isFalse();
        assertThat(again.errors()).containsExactly(CustomerService.DUPLICATE_EMAIL);
    }

    @Test
    @WithMockUser(roles = "CSR")
    void aCustomerKeepsItsOwnEmailOnUpdate() {
        String last = unique("Own");
        int id = service.save(form("", last, last + "@example.test")).id();
        CustomerService.SaveResult r = service.save(new CustomerForm(String.valueOf(id), "Test", last, last + "@example.test", "555-0000", "", "", "", "", ""));
        assertThat(r.ok()).isTrue();
        assertThat(r.id()).isEqualTo(id);
        assertThat(service.get(id).phone()).isEqualTo("555-0000");
    }

    @Test
    @WithMockUser(roles = "CSR")
    void anIdOfZeroCreatesANewCustomer() {
        String last = unique("Zero");
        CustomerService.SaveResult r = service.save(form("000", last, last + "@example.test"));
        assertThat(r.ok()).isTrue();
        assertThat(r.id()).isGreaterThan(0);
    }

    @Test
    @WithMockUser(roles = "CSR")
    void anUnknownIdIs404ButADuplicateEmailIsReportedFirst() {
        String last = unique("Ghost");
        assertThatThrownBy(() -> service.save(form("99999", last, last + "@example.test"))).isInstanceOf(NotFoundException.class);
        String taken = unique("Taken");
        service.save(form("", taken, taken + "@example.test"));
        CustomerService.SaveResult dup = service.save(form("99999", unique("Ghost2"), taken + "@example.test"));
        assertThat(dup.errors()).containsExactly(CustomerService.DUPLICATE_EMAIL);
    }

    @Test
    void theDatabaseIndexCatchesWhatTheCheckCannot() {
        String last = unique("Race");
        repository.insert(new Customer(0, "A", last, last + "@example.test", null, null, null, null, null, null));
        assertThatThrownBy(() -> repository.insert(new Customer(0, "B", last, (last + "@EXAMPLE.TEST"), null, null, null, null, null, null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aCustomerWithPoliciesCannotBeDeleted() {
        assertThat(service.delete(1)).isEqualTo(CustomerService.HAS_POLICIES);
        assertThat(service.get(1).fullName()).isEqualTo("Amara Almeida");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aCustomerWithoutPoliciesIsDeleted() {
        String last = unique("Gone");
        int id = service.save(form("", last, last + "@example.test")).id();
        assertThat(service.delete(id)).isNull();
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void methodSecurityIsASecondLineOfDefence() {
        assertThatThrownBy(() -> service.save(form("", "X", "x@example.test"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "CSR")
    void onlyAnAdministratorMayDeleteEvenAtTheServiceLayer() {
        assertThatThrownBy(() -> service.delete(1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void withoutAnyAuthenticationTheServiceRefusesToWrite() {
        assertThatThrownBy(() -> service.save(form("", "X", "x@example.test"))).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
