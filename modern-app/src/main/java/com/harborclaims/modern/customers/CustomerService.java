package com.harborclaims.modern.customers;

import com.harborclaims.modern.common.Page;
import com.harborclaims.modern.web.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Customer rules. The URL rules in the security chain already keep other roles out; method security here is the second line,
 * so the rule still holds if a controller is ever added that forgets it.
 */
@Service
public class CustomerService {

    public static final String DUPLICATE_EMAIL = "That email address is already in use";
    public static final String HAS_POLICIES = "Customer has policies and cannot be deleted";

    /** Saved when {@code id} is set; otherwise {@code errors} says why not. */
    public record SaveResult(Integer id, List<String> errors) {

        static SaveResult saved(int id) {
            return new SaveResult(id, List.of());
        }

        static SaveResult failed(List<String> errors) {
            return new SaveResult(null, errors);
        }

        public boolean ok() {
            return id != null;
        }
    }

    private final CustomerRepository repository;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Page<Customer> search(String term, int page) {
        return repository.search(term, page);
    }

    public Customer get(int id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Customer " + id));
    }

    public List<PolicyRow> policies(int customerId) {
        return repository.policies(customerId);
    }

    /**
     * Order matters and matches legacy: form validation first, then a duplicate e-mail, then 404 for an unknown id. Values are
     * trimmed, the state is upper-cased, blank optional fields are stored as null, and an id of 0 creates a new customer.
     */
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','CSR')")
    public SaveResult save(CustomerForm raw) {
        CustomerForm f = raw.normalized();
        List<String> errors = CustomerValidator.validate(f, LocalDate.now(clock));
        if (!errors.isEmpty()) {
            return SaveResult.failed(errors);
        }
        int id = f.id().isBlank() ? 0 : Integer.parseInt(f.id().trim());
        Customer c = new Customer(id, f.firstName().trim(), f.lastName().trim(), f.email().trim(), optional(f.phone()), optional(f.dateOfBirth()),
                optional(f.addressLine()), optional(f.city()), f.state().isBlank() ? null : optional(f.state().trim().toUpperCase()),
                optional(f.postalCode()));
        if (repository.emailInUse(c.email(), id)) {
            return SaveResult.failed(List.of(DUPLICATE_EMAIL));
        }
        try {
            if (id == 0) {
                return SaveResult.saved(repository.insert(c));
            }
            get(id);
            repository.update(c);
            return SaveResult.saved(id);
        } catch (DuplicateKeyException e) {
            // the unique index on lower(email) caught a concurrent create that the check above could not
            return SaveResult.failed(List.of(DUPLICATE_EMAIL));
        }
    }

    /** Returns the error message when the customer has policies, or null after deleting. */
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(int id) {
        get(id);
        if (repository.policyCount(id) > 0) {
            return HAS_POLICIES;
        }
        repository.delete(id);
        return null;
    }

    private static String optional(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
