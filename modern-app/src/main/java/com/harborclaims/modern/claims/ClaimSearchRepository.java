package com.harborclaims.modern.claims;

import com.harborclaims.modern.common.Page;
import com.harborclaims.modern.common.Paging;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Claims search. Both the term and the status are bound parameters. */
@Repository
public class ClaimSearchRepository {

    public static final List<String> STATUSES = List.of("NEW", "UNDER_REVIEW", "APPROVED", "DENIED", "PAID", "REOPENED");

    private final JdbcClient jdbc;

    public ClaimSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Matches the claim number or the customer's last name, case-insensitively (wildcards kept). The status filter is an exact
     * match on the trimmed value: it is not case-folded, so "paid" finds nothing. Ordered by id.
     */
    public Page<ClaimRow> search(String term, String status, int page) {
        List<Integer> totals = new ArrayList<>();
        List<ClaimRow> rows = jdbc.sql("""
                SELECT cl.id, cl.claim_number, c.first_name || ' ' || c.last_name AS customer_name, p.policy_number, cl.status,
                       to_char(cl.loss_date, 'YYYY-MM-DD') AS loss_date, cl.amount_claimed, count(*) OVER() AS total_rows
                FROM claims cl JOIN policies p ON p.id = cl.policy_id JOIN customers c ON c.id = cl.customer_id
                WHERE (lower(c.last_name) LIKE lower(:term) OR lower(cl.claim_number) LIKE lower(:term))
                  AND (:status = '' OR cl.status = :status)
                ORDER BY cl.id LIMIT :limit OFFSET :offset""")
                .param("term", "%" + term + "%").param("status", status).param("limit", Paging.PAGE_SIZE).param("offset", Paging.offset(page))
                .query((rs, n) -> {
                    totals.add(rs.getInt("total_rows"));
                    return new ClaimRow(rs.getInt("id"), rs.getString("claim_number"), rs.getString("customer_name"), rs.getString("policy_number"),
                            rs.getString("status"), rs.getString("loss_date"), rs.getBigDecimal("amount_claimed"));
                }).list();
        return new Page<>(rows, page, totals.isEmpty() ? 0 : totals.get(totals.size() - 1));
    }
}
