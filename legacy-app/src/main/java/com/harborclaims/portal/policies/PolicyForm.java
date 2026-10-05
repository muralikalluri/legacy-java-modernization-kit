package com.harborclaims.portal.policies;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

import com.harborclaims.portal.claims.ClaimRules;

public class PolicyForm extends ActionForm {

    private static final long serialVersionUID = 1L;

    private String id = "";
    private String customerId = "";
    private String productType = "";
    private String coverageLimit = "";
    private String premium = "";
    private String startDate = "";
    private String endDate = "";

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if (!blank(id) && !id.trim().matches("[0-9]{1,9}")) {
            errors.add("id", new ActionMessage("error.id.invalid"));
        }
        if (blank(customerId)) {
            errors.add("customerId", new ActionMessage("error.required", "customerId"));
        } else if (!customerId.trim().matches("[0-9]{1,9}")) {
            errors.add("customerId", new ActionMessage("error.id.invalid"));
        }
        if (blank(productType) || !PolicyRules.PRODUCTS.contains(productType.trim())) {
            errors.add("productType", new ActionMessage("error.policy.product.invalid"));
        }
        if (blank(coverageLimit)) {
            errors.add("coverageLimit", new ActionMessage("error.required", "coverageLimit"));
        } else if (ClaimRules.parseMoney(coverageLimit) == null) {
            errors.add("coverageLimit", new ActionMessage("error.amount.invalid", "coverageLimit"));
        }
        if (blank(premium)) {
            errors.add("premium", new ActionMessage("error.required", "premium"));
        } else if (ClaimRules.parseMoney(premium) == null) {
            errors.add("premium", new ActionMessage("error.amount.invalid", "premium"));
        }
        if (ClaimRules.parseDate(startDate) == null) {
            errors.add("startDate", new ActionMessage("error.date.invalid", "startDate"));
        }
        if (ClaimRules.parseDate(endDate) == null) {
            errors.add("endDate", new ActionMessage("error.date.invalid", "endDate"));
        }
        return errors;
    }

    private boolean blank(String s) {
        return s == null || s.trim().length() == 0;
    }

    public int idAsInt() {
        return blank(id) ? 0 : Integer.parseInt(id.trim());
    }

    public int customerIdAsInt() {
        return Integer.parseInt(customerId.trim());
    }

    public void load(Policy p) {
        id = String.valueOf(p.getId());
        customerId = String.valueOf(p.getCustomerId());
        productType = p.getProductType();
        coverageLimit = p.getCoverageLimit().toPlainString();
        premium = p.getPremium().toPlainString();
        startDate = p.getStartDate();
        endDate = p.getEndDate();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getCoverageLimit() {
        return coverageLimit;
    }

    public void setCoverageLimit(String coverageLimit) {
        this.coverageLimit = coverageLimit;
    }

    public String getPremium() {
        return premium;
    }

    public void setPremium(String premium) {
        this.premium = premium;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
