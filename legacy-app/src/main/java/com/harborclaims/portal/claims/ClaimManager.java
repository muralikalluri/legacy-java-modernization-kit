package com.harborclaims.portal.claims;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.common.Fmt;
import com.harborclaims.portal.common.NotFoundException;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.policies.Policy;
import com.harborclaims.portal.policies.PolicyManager;

/**
 * The claims "manager". Over the years it absorbed workflow, validation, fraud heuristics, reserving, SLA
 * tracking, customer letters, report aggregation and formatting. Everything claim related ends up here.
 */
public final class ClaimManager {

    private static final Logger LOG = Logger.getLogger(ClaimManager.class);
    private static final ClaimManager INSTANCE = new ClaimManager();

    // ---- fraud heuristic thresholds ----
    public static final int FRAUD_NEW_POLICY_DAYS = 30;
    public static final BigDecimal FRAUD_NEAR_LIMIT_RATIO = new BigDecimal("0.90");
    public static final int FRAUD_RECENT_CLAIM_DAYS = 90;
    public static final int FRAUD_LATE_REPORT_DAYS = 60;
    public static final BigDecimal FRAUD_ROUND_MIN = new BigDecimal("5000.00");
    public static final BigDecimal FRAUD_ROUND_STEP = new BigDecimal("1000.00");
    public static final BigDecimal FRAUD_DUPLICATE_TOLERANCE = new BigDecimal("0.01");

    // ---- flag codes ----
    public static final String FLAG_NEW_POLICY = "NEW_POLICY";
    public static final String FLAG_NEAR_LIMIT = "NEAR_LIMIT";
    public static final String FLAG_RECENT_CLAIM = "RECENT_CLAIM";
    public static final String FLAG_LATE_REPORT = "LATE_REPORT";
    public static final String FLAG_ROUND_AMOUNT = "ROUND_AMOUNT";
    public static final String FLAG_POLICY_INACTIVE = "POLICY_INACTIVE";
    public static final String FLAG_DUPLICATE = "POSSIBLE_DUPLICATE";

    // ---- SLA ----
    public static final String SLA_OK = "OK";
    public static final String SLA_DUE_SOON = "DUE_SOON";
    public static final String SLA_OVERDUE = "OVERDUE";
    public static final String SLA_NONE = "NONE";

    private static final Map<String, Integer> SLA_DAYS = new HashMap<String, Integer>();
    private static final Map<String, String> STATUS_LABELS = new HashMap<String, String>();
    private static final Map<String, String> STATUS_CSS = new HashMap<String, String>();

    static {
        SLA_DAYS.put(ClaimRules.NEW, Integer.valueOf(2));
        SLA_DAYS.put(ClaimRules.UNDER_REVIEW, Integer.valueOf(14));
        SLA_DAYS.put(ClaimRules.APPROVED, Integer.valueOf(5));
        SLA_DAYS.put(ClaimRules.REOPENED, Integer.valueOf(7));

        STATUS_LABELS.put(ClaimRules.NEW, "New");
        STATUS_LABELS.put(ClaimRules.UNDER_REVIEW, "Under review");
        STATUS_LABELS.put(ClaimRules.APPROVED, "Approved");
        STATUS_LABELS.put(ClaimRules.DENIED, "Denied");
        STATUS_LABELS.put(ClaimRules.PAID, "Paid");
        STATUS_LABELS.put(ClaimRules.REOPENED, "Reopened");

        STATUS_CSS.put(ClaimRules.NEW, "badge-new");
        STATUS_CSS.put(ClaimRules.UNDER_REVIEW, "badge-review");
        STATUS_CSS.put(ClaimRules.APPROVED, "badge-approved");
        STATUS_CSS.put(ClaimRules.DENIED, "badge-denied");
        STATUS_CSS.put(ClaimRules.PAID, "badge-paid");
        STATUS_CSS.put(ClaimRules.REOPENED, "badge-reopened");
    }

    private final ClaimDao dao = new ClaimDao();

    private ClaimManager() {
    }

    public static ClaimManager getInstance() {
        return INSTANCE;
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    // =====================================================================================
    // Section 1: workflow (search, create, transition, notes)
    // =====================================================================================

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

    public List<Claim> listByPolicy(int policyId) throws SQLException {
        return dao.listByPolicy(policyId);
    }

    /** Used by PolicyManager when cancelling: approved but unpaid claims count as open. */
    public int countOpenClaims(int policyId) throws SQLException {
        return dao.countOpenForPolicy(policyId);
    }

    public List<String> availableTransitions(AppUser user, Claim claim) {
        return ClaimRules.targetsFor(user.getRole(), claim.getStatus(), claim.getAmountClaimed());
    }

    /** Validates against the policy and creates a NEW claim. Returns the new id. */
    public int create(String policyNumber, String lossDateText, String amountText, String description, AppUser user)
            throws SQLException, BusinessException {
        Policy policy = PolicyManager.getInstance().findByNumber(policyNumber.trim());
        if (policy == null) {
            throw new BusinessException("error.claim.policy.unknown");
        }
        if (!"ACTIVE".equals(policy.getStatus())) {
            throw new BusinessException("error.claim.policy.inactive");
        }
        LocalDate loss = ClaimRules.parseDate(lossDateText);
        if (loss.isAfter(today())) {
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
        LOG.info("Claim moved to " + to + " by " + user.getUsername() + ": " + oneLine(claim));
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

    // =====================================================================================
    // Section 2: claim form validation (the Struts form delegates here)
    // =====================================================================================

    /** One validation problem: the form field, the message key and its arguments. */
    public static final class FieldProblem {

        private final String field;
        private final String key;
        private final Object[] args;

        public FieldProblem(String field, String key, Object... args) {
            this.field = field;
            this.key = key;
            this.args = args;
        }

        public String getField() {
            return field;
        }

        public String getKey() {
            return key;
        }

        public Object[] getArgs() {
            return args;
        }
    }

    public static List<FieldProblem> validateClaimForm(String policyNumber, String lossDate, String amount,
            String description) {
        List<FieldProblem> problems = new ArrayList<FieldProblem>();
        if (isBlank(policyNumber)) {
            problems.add(new FieldProblem("policyNumber", "error.required", "policyNumber"));
        } else if (policyNumber.trim().length() > 20) {
            problems.add(new FieldProblem("policyNumber", "error.too.long", "policyNumber", Integer.valueOf(20)));
        }
        if (isBlank(lossDate)) {
            problems.add(new FieldProblem("lossDate", "error.required", "lossDate"));
        } else if (ClaimRules.parseDate(lossDate) == null) {
            problems.add(new FieldProblem("lossDate", "error.date.invalid", "lossDate"));
        }
        if (isBlank(amount)) {
            problems.add(new FieldProblem("amountClaimed", "error.required", "amountClaimed"));
        } else if (ClaimRules.parseMoney(amount) == null) {
            problems.add(new FieldProblem("amountClaimed", "error.amount.invalid", "amountClaimed"));
        }
        if (isBlank(description)) {
            problems.add(new FieldProblem("description", "error.required", "description"));
        } else if (description.trim().length() > 2000) {
            problems.add(new FieldProblem("description", "error.too.long", "description", Integer.valueOf(2000)));
        }
        return problems;
    }

    static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    // =====================================================================================
    // Section 3: fraud heuristics (pure functions, shown on the claim page)
    // =====================================================================================

    /**
     * Returns the codes of every heuristic the claim trips. {@code siblings} are the other claims on the
     * same policy and may include the claim itself, which is ignored.
     */
    public static List<String> fraudFlags(Claim claim, Policy policy, List<Claim> siblings, LocalDate asOf) {
        List<String> flags = new ArrayList<String>();
        LocalDate loss = LocalDate.parse(claim.getLossDate());
        LocalDate reported = LocalDate.parse(claim.getReportedDate());
        LocalDate start = LocalDate.parse(policy.getStartDate());

        if (isWithinDaysOfStart(loss, start, FRAUD_NEW_POLICY_DAYS)) {
            flags.add(FLAG_NEW_POLICY);
        }
        if (isNearLimit(claim.getAmountClaimed(), policy.getCoverageLimit())) {
            flags.add(FLAG_NEAR_LIMIT);
        }
        if (hasRecentSibling(claim, loss, siblings, FRAUD_RECENT_CLAIM_DAYS)) {
            flags.add(FLAG_RECENT_CLAIM);
        }
        if (isLateReport(loss, reported, FRAUD_LATE_REPORT_DAYS)) {
            flags.add(FLAG_LATE_REPORT);
        }
        if (isSuspiciouslyRound(claim.getAmountClaimed())) {
            flags.add(FLAG_ROUND_AMOUNT);
        }
        if (!"ACTIVE".equals(policy.getStatus())) {
            flags.add(FLAG_POLICY_INACTIVE);
        }
        if (hasDuplicate(claim, siblings)) {
            flags.add(FLAG_DUPLICATE);
        }
        return flags;
    }

    static boolean isWithinDaysOfStart(LocalDate loss, LocalDate start, int days) {
        long gap = ChronoUnit.DAYS.between(start, loss);
        return gap >= 0 && gap <= days;
    }

    static boolean isNearLimit(BigDecimal claimed, BigDecimal limit) {
        if (limit.signum() <= 0) {
            return true;
        }
        BigDecimal threshold = limit.multiply(FRAUD_NEAR_LIMIT_RATIO);
        return claimed.compareTo(threshold) >= 0;
    }

    static boolean hasRecentSibling(Claim claim, LocalDate loss, List<Claim> siblings, int days) {
        for (Claim other : siblings) {
            if (other.getId() == claim.getId()) {
                continue;
            }
            long gap = Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(other.getLossDate()), loss));
            if (gap <= days) {
                return true;
            }
        }
        return false;
    }

    static boolean isLateReport(LocalDate loss, LocalDate reported, int days) {
        return ChronoUnit.DAYS.between(loss, reported) > days;
    }

    static boolean isSuspiciouslyRound(BigDecimal claimed) {
        if (claimed.compareTo(FRAUD_ROUND_MIN) < 0) {
            return false;
        }
        return claimed.remainder(FRAUD_ROUND_STEP).signum() == 0;
    }

    /** Same policy, same loss date and amounts within one percent of each other. */
    static boolean hasDuplicate(Claim claim, List<Claim> siblings) {
        for (Claim other : siblings) {
            if (other.getId() == claim.getId()) {
                continue;
            }
            if (looksLikeDuplicate(claim, other)) {
                return true;
            }
        }
        return false;
    }

    static boolean looksLikeDuplicate(Claim a, Claim b) {
        if (a.getPolicyId() != b.getPolicyId() || !a.getLossDate().equals(b.getLossDate())) {
            return false;
        }
        BigDecimal larger = a.getAmountClaimed().max(b.getAmountClaimed());
        if (larger.signum() == 0) {
            return true;
        }
        BigDecimal diff = a.getAmountClaimed().subtract(b.getAmountClaimed()).abs();
        return diff.divide(larger, 6, RoundingMode.HALF_UP).compareTo(FRAUD_DUPLICATE_TOLERANCE) <= 0;
    }

    public static String describeFlag(String code) {
        if (FLAG_NEW_POLICY.equals(code)) {
            return "Loss occurred within " + FRAUD_NEW_POLICY_DAYS + " days of the policy start";
        }
        if (FLAG_NEAR_LIMIT.equals(code)) {
            return "Claimed amount is at least 90% of the coverage limit";
        }
        if (FLAG_RECENT_CLAIM.equals(code)) {
            return "Another claim on this policy has a loss date within " + FRAUD_RECENT_CLAIM_DAYS + " days";
        }
        if (FLAG_LATE_REPORT.equals(code)) {
            return "Reported more than " + FRAUD_LATE_REPORT_DAYS + " days after the loss";
        }
        if (FLAG_ROUND_AMOUNT.equals(code)) {
            return "Claimed amount is a round figure of 5,000 or more";
        }
        if (FLAG_POLICY_INACTIVE.equals(code)) {
            return "Policy is not active";
        }
        if (FLAG_DUPLICATE.equals(code)) {
            return "Looks like a duplicate of another claim on this policy";
        }
        return code;
    }

    public static List<String> describeFlags(List<String> codes) {
        List<String> out = new ArrayList<String>();
        for (String code : codes) {
            out.add(describeFlag(code));
        }
        return out;
    }

    // =====================================================================================
    // Section 4: reserves
    // =====================================================================================

    /** Share of the claimed amount to hold in reserve, by product and claim status. */
    public static BigDecimal reserveRate(String product, String status) {
        if (ClaimRules.PAID.equals(status) || ClaimRules.DENIED.equals(status)) {
            return BigDecimal.ZERO;
        }
        if (ClaimRules.APPROVED.equals(status)) {
            return BigDecimal.ONE;
        }
        if ("AUTO".equals(product)) {
            return autoRate(status);
        }
        if ("HOME".equals(product)) {
            return homeRate(status);
        }
        if ("LIFE".equals(product)) {
            return lifeRate(status);
        }
        return new BigDecimal("0.50");
    }

    private static BigDecimal autoRate(String status) {
        if (ClaimRules.NEW.equals(status)) {
            return new BigDecimal("0.60");
        }
        if (ClaimRules.UNDER_REVIEW.equals(status)) {
            return new BigDecimal("0.75");
        }
        if (ClaimRules.REOPENED.equals(status)) {
            return new BigDecimal("0.80");
        }
        return new BigDecimal("0.50");
    }

    private static BigDecimal homeRate(String status) {
        if (ClaimRules.NEW.equals(status)) {
            return new BigDecimal("0.70");
        }
        if (ClaimRules.UNDER_REVIEW.equals(status)) {
            return new BigDecimal("0.85");
        }
        if (ClaimRules.REOPENED.equals(status)) {
            return new BigDecimal("0.90");
        }
        return new BigDecimal("0.50");
    }

    private static BigDecimal lifeRate(String status) {
        if (ClaimRules.NEW.equals(status)) {
            return new BigDecimal("0.90");
        }
        if (ClaimRules.UNDER_REVIEW.equals(status)) {
            return new BigDecimal("0.95");
        }
        if (ClaimRules.REOPENED.equals(status)) {
            return new BigDecimal("1.00");
        }
        return new BigDecimal("0.50");
    }

    /** Approved claims reserve the approved amount, everything else works from the claimed amount. */
    public static BigDecimal reserveAmount(Claim claim, String product) {
        BigDecimal base = claim.getAmountClaimed();
        if (ClaimRules.APPROVED.equals(claim.getStatus()) && claim.getAmountApproved() != null) {
            base = claim.getAmountApproved();
        }
        return base.multiply(reserveRate(product, claim.getStatus())).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal totalReserve(List<Claim> claims, String product) {
        BigDecimal total = BigDecimal.ZERO;
        for (Claim c : claims) {
            total = total.add(reserveAmount(c, product));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // =====================================================================================
    // Section 5: SLA tracking
    // =====================================================================================

    public static int slaDaysFor(String status) {
        Integer days = SLA_DAYS.get(status);
        return days == null ? 0 : days.intValue();
    }

    /** Days since the claim was reported, never negative. */
    public static long daysOpen(Claim claim, LocalDate asOf) {
        long days = ChronoUnit.DAYS.between(LocalDate.parse(claim.getReportedDate()), asOf);
        return days < 0 ? 0 : days;
    }

    public static String slaState(Claim claim, LocalDate asOf) {
        int allowed = slaDaysFor(claim.getStatus());
        if (allowed == 0) {
            return SLA_NONE;
        }
        long open = daysOpen(claim, asOf);
        if (open > allowed) {
            return SLA_OVERDUE;
        }
        if (open >= allowed - 1) {
            return SLA_DUE_SOON;
        }
        return SLA_OK;
    }

    public static String slaText(Claim claim, LocalDate asOf) {
        String state = slaState(claim, asOf);
        if (SLA_NONE.equals(state)) {
            return "No SLA applies to " + statusLabel(claim.getStatus()).toLowerCase() + " claims";
        }
        long open = daysOpen(claim, asOf);
        int allowed = slaDaysFor(claim.getStatus());
        if (SLA_OVERDUE.equals(state)) {
            return "Overdue by " + (open - allowed) + " day(s), open " + open + " day(s) against " + allowed;
        }
        if (SLA_DUE_SOON.equals(state)) {
            return "Due soon: open " + open + " day(s) against " + allowed;
        }
        return "Within SLA: open " + open + " day(s) against " + allowed;
    }

    /** One-line guidance for the person looking at the claim. */
    public static String nextActionHint(String status, String role) {
        boolean decider = "ADJUSTER".equals(role) || "SUPERVISOR".equals(role) || "ADMIN".equals(role);
        boolean supervisor = "SUPERVISOR".equals(role) || "ADMIN".equals(role);
        if (ClaimRules.NEW.equals(status)) {
            return decider ? "Start the review." : "Waiting for an adjuster to start the review.";
        }
        if (ClaimRules.UNDER_REVIEW.equals(status)) {
            return decider ? "Approve or deny the claim." : "Under review by an adjuster.";
        }
        if (ClaimRules.APPROVED.equals(status)) {
            return supervisor ? "Mark the claim as paid once the payment clears." : "Waiting for payment.";
        }
        if (ClaimRules.DENIED.equals(status)) {
            return supervisor ? "A supervisor may reopen this claim." : "Denied. Only a supervisor can reopen it.";
        }
        if (ClaimRules.REOPENED.equals(status)) {
            return decider ? "Return the claim to review." : "Reopened, waiting for an adjuster.";
        }
        if (ClaimRules.PAID.equals(status)) {
            return "Closed. Nothing more to do.";
        }
        return "";
    }

    // =====================================================================================
    // Section 6: customer correspondence (plain text previews)
    // =====================================================================================

    /** Returns a letter preview for the statuses that trigger correspondence, otherwise an empty string. */
    public static String letterFor(Claim claim, Policy policy, String lastReason) {
        if (ClaimRules.APPROVED.equals(claim.getStatus())) {
            return approvalLetter(claim, policy);
        }
        if (ClaimRules.PAID.equals(claim.getStatus())) {
            return paymentLetter(claim, policy);
        }
        if (ClaimRules.DENIED.equals(claim.getStatus())) {
            return denialLetter(claim, policy, lastReason);
        }
        if (ClaimRules.REOPENED.equals(claim.getStatus())) {
            return reopenLetter(claim, policy);
        }
        return "";
    }

    public static String approvalLetter(Claim claim, Policy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(claim.getCustomerName()).append(",\n\n");
        sb.append("We have approved claim ").append(claim.getClaimNumber());
        sb.append(" under policy ").append(policy.getPolicyNumber()).append(".\n");
        sb.append("Loss date: ").append(claim.getLossDate()).append("\n");
        sb.append("Amount claimed: ").append(Fmt.money(claim.getAmountClaimed())).append("\n");
        sb.append("Amount approved: ").append(Fmt.money(claim.getAmountApproved())).append("\n\n");
        sb.append("Payment is normally issued within five business days.\n\n");
        sb.append("Harbor Claims Portal, Claims Department");
        return sb.toString();
    }

    public static String paymentLetter(Claim claim, Policy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(claim.getCustomerName()).append(",\n\n");
        sb.append("Payment for claim ").append(claim.getClaimNumber());
        sb.append(" has been issued.\n");
        sb.append("Policy: ").append(policy.getPolicyNumber()).append(" (").append(policy.getProductType()).append(")\n");
        sb.append("Amount paid: ").append(Fmt.money(claim.getAmountApproved())).append("\n\n");
        sb.append("This closes the claim. Please keep this letter for your records.\n\n");
        sb.append("Harbor Claims Portal, Claims Department");
        return sb.toString();
    }

    public static String denialLetter(Claim claim, Policy policy, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(claim.getCustomerName()).append(",\n\n");
        sb.append("After review we are unable to pay claim ").append(claim.getClaimNumber());
        sb.append(" under policy ").append(policy.getPolicyNumber()).append(".\n");
        sb.append("Reason: ").append(isBlank(reason) ? "see claim notes" : reason.trim()).append("\n\n");
        sb.append("You may ask a supervisor to reopen the claim if you have new information.\n\n");
        sb.append("Harbor Claims Portal, Claims Department");
        return sb.toString();
    }

    public static String reopenLetter(Claim claim, Policy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(claim.getCustomerName()).append(",\n\n");
        sb.append("We have reopened claim ").append(claim.getClaimNumber());
        sb.append(" under policy ").append(policy.getPolicyNumber()).append(".\n");
        sb.append("An adjuster will contact you about the next steps.\n\n");
        sb.append("Harbor Claims Portal, Claims Department");
        return sb.toString();
    }

    /** The reason given on the most recent denial note, or an empty string. */
    public static String lastDenialReason(List<ClaimNote> notes) {
        String marker = "to DENIED: ";
        for (int i = notes.size() - 1; i >= 0; i--) {
            String text = notes.get(i).getNote();
            int at = text.indexOf(marker);
            if (text.startsWith("Status changed from ") && at >= 0) {
                return text.substring(at + marker.length());
            }
        }
        return "";
    }

    // =====================================================================================
    // Section 7: report aggregation
    // =====================================================================================

    /** One line of the claims-by-status report. */
    public static final class StatusRow {

        private final String status;
        private final int count;
        private final BigDecimal total;
        private final String share;

        public StatusRow(String status, int count, BigDecimal total, String share) {
            this.status = status;
            this.count = count;
            this.total = total;
            this.share = share;
        }

        public String getStatus() {
            return status;
        }

        public String getLabel() {
            return statusLabel(status);
        }

        public int getCount() {
            return count;
        }

        public BigDecimal getTotal() {
            return total;
        }

        public String getTotalDisplay() {
            return Fmt.money(total);
        }

        public String getShare() {
            return share;
        }
    }

    /** One line of the claims-by-adjuster report. */
    public static final class AdjusterRow {

        private final Integer userId;
        private final String name;
        private final int count;
        private final int open;
        private final BigDecimal total;

        public AdjusterRow(Integer userId, String name, int count, int open, BigDecimal total) {
            this.userId = userId;
            this.name = name;
            this.count = count;
            this.open = open;
            this.total = total;
        }

        public Integer getUserId() {
            return userId;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public int getOpen() {
            return open;
        }

        public BigDecimal getTotal() {
            return total;
        }

        public String getTotalDisplay() {
            return Fmt.money(total);
        }

        public int getOpenPercent() {
            return openPercent(this);
        }

        public String getWorkload() {
            return workloadLabel(this);
        }
    }

    public List<StatusRow> statusReport() throws SQLException {
        return buildStatusRows(dao.statusTotals());
    }

    /** Every status appears, including those with no claims, in the fixed workflow order. */
    public static List<StatusRow> buildStatusRows(List<Object[]> raw) {
        Map<String, Object[]> byStatus = new HashMap<String, Object[]>();
        int grand = 0;
        for (Object[] row : raw) {
            byStatus.put((String) row[0], row);
            grand += ((Integer) row[1]).intValue();
        }
        List<StatusRow> out = new ArrayList<StatusRow>();
        for (String status : ClaimRules.ALL_STATUSES) {
            Object[] row = byStatus.get(status);
            int count = row == null ? 0 : ((Integer) row[1]).intValue();
            BigDecimal total = row == null ? BigDecimal.ZERO.setScale(2) : ((BigDecimal) row[2]).setScale(2);
            out.add(new StatusRow(status, count, total, percent(count, grand)));
        }
        return out;
    }

    static String percent(int part, int whole) {
        if (whole == 0) {
            return "0.0%";
        }
        BigDecimal pct = new BigDecimal(part).multiply(new BigDecimal(100)).divide(new BigDecimal(whole), 1,
                RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }

    public List<AdjusterRow> adjusterReport() throws SQLException {
        return buildAdjusterRows(dao.adjusterTotals());
    }

    /** Ordered by name then id, with the unassigned bucket always last. */
    public static List<AdjusterRow> buildAdjusterRows(List<Object[]> raw) {
        List<AdjusterRow> rows = new ArrayList<AdjusterRow>();
        for (Object[] r : raw) {
            rows.add(new AdjusterRow((Integer) r[0], (String) r[1], ((Integer) r[2]).intValue(),
                    ((Integer) r[4]).intValue(), ((BigDecimal) r[3]).setScale(2)));
        }
        Collections.sort(rows, new Comparator<AdjusterRow>() {
            public int compare(AdjusterRow a, AdjusterRow b) {
                if (a.getUserId() == null && b.getUserId() != null) {
                    return 1;
                }
                if (a.getUserId() != null && b.getUserId() == null) {
                    return -1;
                }
                int byName = a.getName().compareTo(b.getName());
                if (byName != 0) {
                    return byName;
                }
                if (a.getUserId() == null) {
                    return 0;
                }
                return a.getUserId().compareTo(b.getUserId());
            }
        });
        return rows;
    }

    public static BigDecimal grandTotal(List<StatusRow> rows) {
        BigDecimal sum = BigDecimal.ZERO.setScale(2);
        for (StatusRow r : rows) {
            sum = sum.add(r.getTotal());
        }
        return sum;
    }

    public static int grandCount(List<StatusRow> rows) {
        int sum = 0;
        for (StatusRow r : rows) {
            sum += r.getCount();
        }
        return sum;
    }

    // =====================================================================================
    // Section 8: formatting helpers (some predate Fmt and drifted from it)
    // =====================================================================================

    public static String statusLabel(String status) {
        String label = STATUS_LABELS.get(status);
        return label == null ? status : label;
    }

    public static String statusCss(String status) {
        String css = STATUS_CSS.get(status);
        return css == null ? "badge-unknown" : css;
    }

    /** Older money formatter used by the PDF export. Same output as Fmt.money for non-negative amounts. */
    public static String moneyText(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        String plain = scaled.toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        String frac = plain.substring(dot);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = whole.length() - 1; i >= 0; i--) {
            char ch = whole.charAt(i);
            if (ch != '-' && count > 0 && count % 3 == 0) {
                sb.append(',');
            }
            sb.append(ch);
            if (ch != '-') {
                count++;
            }
        }
        return sb.reverse().toString() + frac;
    }

    public static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static String padLeft(String s, int width) {
        StringBuilder sb = new StringBuilder();
        int missing = width - (s == null ? 0 : s.length());
        for (int i = 0; i < missing; i++) {
            sb.append(' ');
        }
        sb.append(s == null ? "" : s);
        return sb.toString();
    }

    /** Fixed-width text table of the status report. The policy expiry job writes it to the log after each run. */
    public static String statusTable(List<StatusRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(padRight("Status", 14)).append(padLeft("Claims", 8)).append(padLeft("Total", 16)).append(padLeft("Share", 8))
                .append('\n');
        for (StatusRow r : rows) {
            sb.append(padRight(r.getLabel(), 14)).append(padLeft(String.valueOf(r.getCount()), 8))
                    .append(padLeft(moneyText(r.getTotal()), 16)).append(padLeft(r.getShare(), 8)).append('\n');
        }
        sb.append(padRight("All", 14)).append(padLeft(String.valueOf(grandCount(rows)), 8))
                .append(padLeft(moneyText(grandTotal(rows)), 16)).append(padLeft("", 8)).append('\n');
        return sb.toString();
    }

    public static String shortDate(String isoDate) {
        if (isBlank(isoDate) || isoDate.length() != 10) {
            return "";
        }
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        int month = Integer.parseInt(isoDate.substring(5, 7));
        return isoDate.substring(8, 10) + " " + months[month - 1] + " " + isoDate.substring(0, 4);
    }

    /** A claim as a single line for logs and the digest. */
    public static String oneLine(Claim c) {
        return c.getClaimNumber() + " | " + statusLabel(c.getStatus()) + " | " + c.getCustomerName() + " | "
                + moneyText(c.getAmountClaimed()) + " | loss " + shortDate(c.getLossDate());
    }

    /** Joins any number of strings, skipping blanks. */
    public static String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }

    // =====================================================================================
    // Section 9: everything the claim page needs in one call
    // =====================================================================================

    /** The extra panels on the claim page. Built from data already loaded, so it needs no database access. */
    public static final class ClaimInsight {

        private final List<String> flags;
        private final String reserve;
        private final String sla;
        private final String slaState;
        private final String hint;
        private final String letter;

        public ClaimInsight(List<String> flags, String reserve, String sla, String slaState, String hint, String letter) {
            this.flags = flags;
            this.reserve = reserve;
            this.sla = sla;
            this.slaState = slaState;
            this.hint = hint;
            this.letter = letter;
        }

        public List<String> getFlags() {
            return flags;
        }

        public String getReserve() {
            return reserve;
        }

        public String getSla() {
            return sla;
        }

        public String getSlaState() {
            return slaState;
        }

        public String getHint() {
            return hint;
        }

        public String getLetter() {
            return letter;
        }
    }

    public static ClaimInsight insightFor(Claim claim, Policy policy, List<Claim> siblings, List<ClaimNote> notes,
            String role, LocalDate asOf) {
        List<String> flags = describeFlags(fraudFlags(claim, policy, siblings, asOf));
        String reserve = Fmt.money(reserveAmount(claim, policy.getProductType()));
        return new ClaimInsight(flags, reserve, slaText(claim, asOf), slaState(claim, asOf),
                nextActionHint(claim.getStatus(), role), letterFor(claim, policy, lastDenialReason(notes)));
    }

    // =====================================================================================
    // Section 10: per-policy summary (policy page)
    // =====================================================================================

    /** Roll-up of every claim on one policy. */
    public static final class PolicyClaimsSummary {

        private final int total;
        private final int open;
        private final int paid;
        private final BigDecimal claimed;
        private final BigDecimal paidOut;
        private final BigDecimal reserve;
        private final String text;
        private final BigDecimal largest;
        private final String byStatus;

        public PolicyClaimsSummary(int total, int open, int paid, BigDecimal claimed, BigDecimal paidOut,
                BigDecimal reserve, String text, BigDecimal largest, String byStatus) {
            this.largest = largest;
            this.byStatus = byStatus;
            this.total = total;
            this.open = open;
            this.paid = paid;
            this.claimed = claimed;
            this.paidOut = paidOut;
            this.reserve = reserve;
            this.text = text;
        }

        public int getTotal() {
            return total;
        }

        public int getOpen() {
            return open;
        }

        public int getPaid() {
            return paid;
        }

        public String getClaimedDisplay() {
            return Fmt.money(claimed);
        }

        public String getPaidOutDisplay() {
            return Fmt.money(paidOut);
        }

        public String getReserveDisplay() {
            return Fmt.money(reserve);
        }

        public String getText() {
            return text;
        }

        public String getLargestDisplay() {
            return Fmt.money(largest);
        }

        public String getByStatus() {
            return byStatus;
        }
    }

    public static PolicyClaimsSummary summarize(List<Claim> claims, Policy policy) {
        int open = 0;
        int paid = 0;
        BigDecimal claimed = BigDecimal.ZERO.setScale(2);
        BigDecimal paidOut = BigDecimal.ZERO.setScale(2);
        for (Claim c : claims) {
            claimed = claimed.add(c.getAmountClaimed());
            if (ClaimRules.PAID.equals(c.getStatus())) {
                paid++;
                if (c.getAmountApproved() != null) {
                    paidOut = paidOut.add(c.getAmountApproved());
                }
            } else if (isOpenStatus(c.getStatus())) {
                open++;
            }
        }
        BigDecimal reserve = totalReserve(claims, policy.getProductType());
        String text = joinNonBlank("; ", claims.size() + " claim(s)", open > 0 ? open + " open" : "",
                paid > 0 ? paid + " paid" : "", "claimed " + moneyText(claimed));
        StringBuilder bar = new StringBuilder();
        for (Map.Entry<String, Integer> e : countByStatus(claims).entrySet()) {
            if (bar.length() > 0) {
                bar.append(", ");
            }
            bar.append(statusLabel(e.getKey())).append(' ').append(e.getValue());
        }
        return new PolicyClaimsSummary(claims.size(), open, paid, claimed, paidOut, reserve, text,
                largestClaim(claims), bar.toString());
    }

    static boolean isOpenStatus(String status) {
        return ClaimRules.NEW.equals(status) || ClaimRules.UNDER_REVIEW.equals(status)
                || ClaimRules.REOPENED.equals(status) || ClaimRules.APPROVED.equals(status);
    }

    /** Highest claimed amount among the claims, or zero when there are none. */
    public static BigDecimal largestClaim(List<Claim> claims) {
        BigDecimal best = BigDecimal.ZERO.setScale(2);
        for (Claim c : claims) {
            if (c.getAmountClaimed().compareTo(best) > 0) {
                best = c.getAmountClaimed();
            }
        }
        return best;
    }

    /** Number of claims in each status, in workflow order, shown as a line on the policy page. */
    public static Map<String, Integer> countByStatus(List<Claim> claims) {
        Map<String, Integer> out = new java.util.LinkedHashMap<String, Integer>();
        for (String status : ClaimRules.ALL_STATUSES) {
            out.put(status, Integer.valueOf(0));
        }
        for (Claim c : claims) {
            Integer n = out.get(c.getStatus());
            out.put(c.getStatus(), Integer.valueOf(n == null ? 1 : n.intValue() + 1));
        }
        return out;
    }

    // =====================================================================================
    // Section 11: workload helpers (adjuster report)
    // =====================================================================================

    /** Share of an adjuster's claims that are still open, as a whole percentage. */
    public static int openPercent(AdjusterRow row) {
        if (row.getCount() == 0) {
            return 0;
        }
        return (int) Math.round(100.0 * row.getOpen() / row.getCount());
    }

    public static String workloadLabel(AdjusterRow row) {
        if (row.getUserId() == null) {
            return row.getOpen() > 0 ? "Needs an owner" : "Nothing open";
        }
        int open = row.getOpen();
        if (open >= 12) {
            return "Heavy";
        }
        if (open >= 6) {
            return "Busy";
        }
        if (open >= 1) {
            return "Light";
        }
        return "Clear";
    }

    /** The row with the most open claims, or null when the list is empty. */
    public static AdjusterRow busiest(List<AdjusterRow> rows) {
        AdjusterRow best = null;
        for (AdjusterRow r : rows) {
            if (r.getUserId() == null) {
                continue;
            }
            if (best == null || r.getOpen() > best.getOpen()) {
                best = r;
            }
        }
        return best;
    }
}
