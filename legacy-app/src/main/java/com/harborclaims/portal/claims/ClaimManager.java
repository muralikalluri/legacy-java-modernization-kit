package com.harborclaims.portal.claims;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.log4j.Logger;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.common.NotFoundException;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.policies.Policy;
import com.harborclaims.portal.policies.PolicyDao;

/** "Manager" singleton holding the claim workflow. It grows in M2. */
public final class ClaimManager {

    private static final Logger LOG = Logger.getLogger(ClaimManager.class);
    private static final ClaimManager INSTANCE = new ClaimManager();

    private final ClaimDao dao = new ClaimDao();
    private final PolicyDao policyDao = new PolicyDao();

    private ClaimManager() {
    }

    public static ClaimManager getInstance() {
        return INSTANCE;
    }

    public Page<Claim> search(String term, String status, int page) throws SQLException {
        return dao.search(term, status, page);
    }

    public Claim get(int id) throws SQLException, NotFoundException {
        Claim c = dao.findById(id);
        if (c == null) {
            throw new NotFoundException("Claim " + id);
        }
        return c;
    }

    public List<ClaimNote> notes(int claimId) throws SQLException {
        return dao.notes(claimId);
    }

    public List<String> availableTransitions(AppUser user, Claim claim) {
        return ClaimRules.targetsFor(user.getRole(), claim.getStatus(), claim.getAmountClaimed());
    }

    /** Validates against the policy and creates a NEW claim. Returns the new id. */
    public int create(String policyNumber, String lossDateText, String amountText, String description, AppUser user)
            throws SQLException, BusinessException {
        Policy policy = policyDao.findByNumber(policyNumber.trim());
        if (policy == null) {
            throw new BusinessException("error.claim.policy.unknown");
        }
        if (!"ACTIVE".equals(policy.getStatus())) {
            throw new BusinessException("error.claim.policy.inactive");
        }
        LocalDate loss = ClaimRules.parseDate(lossDateText);
        if (loss.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new BusinessException("error.claim.loss.future");
        }
        if (!ClaimRules.withinPeriod(loss, LocalDate.parse(policy.getStartDate()), LocalDate.parse(policy.getEndDate()))) {
            throw new BusinessException("error.claim.loss.outside");
        }
        BigDecimal amount = ClaimRules.parseMoney(amountText);
        if (amount.signum() <= 0) {
            throw new BusinessException("error.claim.amount.positive");
        }
        if (amount.compareTo(policy.getCoverageLimit()) > 0) {
            throw new BusinessException("error.claim.amount.limit", policy.getCoverageLimitDisplay());
        }
        int id = dao.insert(policy.getId(), policy.getCustomerId(), loss, amount, description.trim());
        LOG.info("Claim " + id + " created by " + user.getUsername());
        return id;
    }

    public void transition(int claimId, String to, String approvedText, String reason, AppUser user)
            throws SQLException, BusinessException, NotFoundException {
        Claim claim = get(claimId);
        String from = claim.getStatus();
        if (!ClaimRules.isValidEdge(from, to)) {
            throw new BusinessException("error.transition.invalid", from, to);
        }
        if (!ClaimRules.mayTransition(user.getRole(), from, to, claim.getAmountClaimed())) {
            throw new BusinessException("error.transition.role", to);
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (ClaimRules.DENIED.equals(to) && cleanReason.length() == 0) {
            throw new BusinessException("error.transition.reason.required");
        }
        BigDecimal approved = null;
        if (ClaimRules.APPROVED.equals(to)) {
            approved = approvedAmount(claim, approvedText);
        }
        String note = "Status changed from " + from + " to " + to + (cleanReason.length() == 0 ? "" : ": " + cleanReason);
        if (!dao.transition(claimId, from, to, approved, user.getUsername(), note)) {
            throw new BusinessException("error.transition.stale");
        }
        LOG.info("Claim " + claimId + " " + from + " -> " + to + " by " + user.getUsername());
    }

    private BigDecimal approvedAmount(Claim claim, String text) throws BusinessException {
        if (text == null || text.trim().length() == 0) {
            return claim.getAmountClaimed();
        }
        BigDecimal v = ClaimRules.parseMoney(text);
        if (v == null || v.signum() <= 0 || v.compareTo(claim.getAmountClaimed()) > 0) {
            throw new BusinessException("error.transition.approved.invalid");
        }
        return v;
    }

    public void addNote(int claimId, String text, AppUser user) throws SQLException, NotFoundException {
        get(claimId);
        dao.addNote(claimId, user.getUsername(), text.trim());
    }
}
