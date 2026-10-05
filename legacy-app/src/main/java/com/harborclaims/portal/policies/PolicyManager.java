package com.harborclaims.portal.policies;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.log4j.Logger;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.claims.Claim;
import com.harborclaims.portal.claims.ClaimManager;
import com.harborclaims.portal.claims.ClaimRules;
import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.common.NotFoundException;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.customers.CustomerDao;

/**
 * Policy workflow. Calls into ClaimManager (open claims block cancelling), and ClaimManager calls back
 * here for policy lookups: the Claims/Policies package cycle is real. Singletons are looked up at the
 * point of use, never cached in a field, so class initialisation order cannot bite.
 */
public final class PolicyManager {

    private static final Logger LOG = Logger.getLogger(PolicyManager.class);
    private static final PolicyManager INSTANCE = new PolicyManager();

    private final PolicyDao dao = new PolicyDao();
    private final CustomerDao customerDao = new CustomerDao();

    private PolicyManager() {
    }

    public static PolicyManager getInstance() {
        return INSTANCE;
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public Page<Policy> search(String term, String status, int page) throws SQLException {
        return dao.search(term, status, page);
    }

    public Policy get(int id) throws SQLException, NotFoundException {
        Policy p = dao.findById(id);
        if (p == null) {
            throw new NotFoundException("Policy " + id);
        }
        return p;
    }

    public Policy findByNumber(String policyNumber) throws SQLException {
        return dao.findByNumber(policyNumber);
    }

    public List<Claim> claimsOf(int policyId) throws SQLException {
        return ClaimManager.getInstance().listByPolicy(policyId);
    }

    public List<Policy> expiringBetween(LocalDate from, LocalDate to) throws SQLException {
        return dao.expiringBetween(from, to);
    }

    /** Creates (id 0) or edits a policy. Returns the policy id. */
    public int save(PolicyForm f, AppUser user) throws SQLException, BusinessException, NotFoundException {
        BigDecimal limit = ClaimRules.parseMoney(f.getCoverageLimit());
        BigDecimal premium = ClaimRules.parseMoney(f.getPremium());
        if (limit.signum() <= 0) {
            throw new BusinessException("error.policy.limit.positive");
        }
        int id = f.idAsInt();
        if (id == 0) {
            return create(f, limit, premium, user);
        }
        Policy existing = get(id);
        if (!PolicyRules.ACTIVE.equals(existing.getStatus())) {
            throw new BusinessException("error.policy.edit.status");
        }
        if (f.customerIdAsInt() != existing.getCustomerId()) {
            throw new BusinessException("error.policy.customer.locked");
        }
        if (!existing.getStartDate().equals(f.getStartDate().trim()) || !existing.getEndDate().equals(f.getEndDate().trim())) {
            throw new BusinessException("error.policy.dates.locked");
        }
        if (!dao.updateTerms(id, f.getProductType().trim(), limit, premium)) {
            throw new BusinessException("error.policy.stale");
        }
        LOG.info("Policy " + id + " edited by " + user.getUsername());
        return id;
    }

    private int create(PolicyForm f, BigDecimal limit, BigDecimal premium, AppUser user)
            throws SQLException, BusinessException {
        if (customerDao.findById(f.customerIdAsInt()) == null) {
            throw new BusinessException("error.policy.customer.unknown");
        }
        LocalDate start = ClaimRules.parseDate(f.getStartDate());
        LocalDate end = ClaimRules.parseDate(f.getEndDate());
        if (end.isBefore(start)) {
            throw new BusinessException("error.policy.dates.order");
        }
        int id = dao.insert(f.customerIdAsInt(), f.getProductType().trim(), limit, premium, start, end);
        LOG.info("Policy " + id + " created by " + user.getUsername());
        return id;
    }

    public void cancel(int id, AppUser user) throws SQLException, BusinessException, NotFoundException {
        Policy p = get(id);
        int open = ClaimManager.getInstance().countOpenClaims(id);
        String blocker = PolicyRules.cancelBlocker(p.getStatus(), open);
        if (blocker != null) {
            throw new BusinessException(blocker, open);
        }
        if (!dao.cancel(id, today())) {
            throw new BusinessException("error.policy.stale");
        }
        LOG.info("Policy " + id + " cancelled by " + user.getUsername());
    }

    public void renew(int id, String expectedEnd, AppUser user) throws SQLException, BusinessException, NotFoundException {
        Policy p = get(id);
        LocalDate currentEnd = LocalDate.parse(p.getEndDate());
        LocalDate expected = ClaimRules.parseDate(expectedEnd);
        if (expected == null || !expected.equals(currentEnd)) {
            throw new BusinessException("error.policy.stale");
        }
        String blocker = PolicyRules.renewalBlocker(p.getStatus(), currentEnd, today());
        if (blocker != null) {
            throw new BusinessException(blocker);
        }
        if (!dao.renew(id, p.getStatus(), currentEnd, PolicyRules.renewedEnd(currentEnd))) {
            throw new BusinessException("error.policy.stale");
        }
        LOG.info("Policy " + id + " renewed by " + user.getUsername());
    }
}
