package com.harborclaims.modern.customers;

import com.harborclaims.modern.common.Page;
import com.harborclaims.modern.common.Paging;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Every statement is parameterised. The search term is bound, so a quote or a tautology is just text. */
@Repository
public class CustomerRepository {

    private static final String COLS = "id, first_name, last_name, email, phone, to_char(date_of_birth, 'YYYY-MM-DD') AS date_of_birth, "
            + "address_line, city, state, postal_code";

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Case-insensitive contains over first name, last name and e-mail. % and _ in the term stay wildcards, as in legacy. */
    public Page<Customer> search(String term, int page) {
        List<Integer> totals = new ArrayList<>();
        List<Customer> rows = jdbc.sql("SELECT " + COLS + ", count(*) OVER() AS total_rows FROM customers "
                + "WHERE lower(first_name) LIKE lower(:term) OR lower(last_name) LIKE lower(:term) OR lower(email) LIKE lower(:term) "
                + "ORDER BY last_name, first_name, id LIMIT :limit OFFSET :offset")
                .param("term", "%" + term + "%").param("limit", Paging.PAGE_SIZE).param("offset", Paging.offset(page))
                .query((rs, n) -> {
                    totals.add(rs.getInt("total_rows"));
                    return map(rs);
                }).list();
        return new Page<>(rows, page, totals.isEmpty() ? 0 : totals.get(totals.size() - 1));
    }

    public Optional<Customer> findById(int id) {
        return jdbc.sql("SELECT " + COLS + " FROM customers WHERE id = :id").param("id", id).query((rs, n) -> map(rs)).optional();
    }

    public boolean emailInUse(String email, int excludeId) {
        Integer n = jdbc.sql("SELECT count(*) FROM customers WHERE lower(email) = lower(:email) AND id <> :id")
                .param("email", email).param("id", excludeId).query(Integer.class).single();
        return n > 0;
    }

    public int insert(Customer c) {
        return jdbc.sql("INSERT INTO customers (first_name, last_name, email, phone, date_of_birth, address_line, city, state, postal_code) "
                + "VALUES (:firstName, :lastName, :email, :phone, CAST(:dob AS date), :address, :city, :state, :postal) RETURNING id")
                .param("firstName", c.firstName()).param("lastName", c.lastName()).param("email", c.email()).param("phone", c.phone())
                .param("dob", c.dateOfBirth()).param("address", c.addressLine()).param("city", c.city()).param("state", c.state())
                .param("postal", c.postalCode()).query(Integer.class).single();
    }

    public void update(Customer c) {
        jdbc.sql("UPDATE customers SET first_name = :firstName, last_name = :lastName, email = :email, phone = :phone, "
                + "date_of_birth = CAST(:dob AS date), address_line = :address, city = :city, state = :state, postal_code = :postal "
                + "WHERE id = :id")
                .param("firstName", c.firstName()).param("lastName", c.lastName()).param("email", c.email()).param("phone", c.phone())
                .param("dob", c.dateOfBirth()).param("address", c.addressLine()).param("city", c.city()).param("state", c.state())
                .param("postal", c.postalCode()).param("id", c.id()).update();
    }

    public void delete(int id) {
        jdbc.sql("DELETE FROM customers WHERE id = :id").param("id", id).update();
    }

    /** All policies count, whatever their status: a customer with only a cancelled policy still cannot be deleted. */
    public int policyCount(int customerId) {
        return jdbc.sql("SELECT count(*) FROM policies WHERE customer_id = :id").param("id", customerId).query(Integer.class).single();
    }

    public List<PolicyRow> policies(int customerId) {
        return jdbc.sql("SELECT policy_number, product_type, coverage_limit, to_char(start_date, 'YYYY-MM-DD') AS start_date, "
                + "to_char(end_date, 'YYYY-MM-DD') AS end_date, status FROM policies WHERE customer_id = :id ORDER BY id")
                .param("id", customerId)
                .query((rs, n) -> new PolicyRow(rs.getString("policy_number"), rs.getString("product_type"), rs.getBigDecimal("coverage_limit"),
                        rs.getString("start_date"), rs.getString("end_date"), rs.getString("status")))
                .list();
    }

    private static Customer map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Customer(rs.getInt("id"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"), rs.getString("phone"),
                rs.getString("date_of_birth"), rs.getString("address_line"), rs.getString("city"), rs.getString("state"), rs.getString("postal_code"));
    }
}
