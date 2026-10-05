package com.harborclaims.portal.claims;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class ClaimForm extends ActionForm {

    private static final long serialVersionUID = 1L;

    private String policyNumber = "";
    private String lossDate = "";
    private String amountClaimed = "";
    private String description = "";

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if (blank(policyNumber)) {
            errors.add("policyNumber", new ActionMessage("error.required", "policyNumber"));
        }
        if (blank(lossDate)) {
            errors.add("lossDate", new ActionMessage("error.required", "lossDate"));
        } else if (ClaimRules.parseDate(lossDate) == null) {
            errors.add("lossDate", new ActionMessage("error.date.invalid", "lossDate"));
        }
        if (blank(amountClaimed)) {
            errors.add("amountClaimed", new ActionMessage("error.required", "amountClaimed"));
        } else if (ClaimRules.parseMoney(amountClaimed) == null) {
            errors.add("amountClaimed", new ActionMessage("error.amount.invalid", "amountClaimed"));
        }
        if (blank(description)) {
            errors.add("description", new ActionMessage("error.required", "description"));
        } else if (description.trim().length() > 2000) {
            errors.add("description", new ActionMessage("error.too.long", "description", 2000));
        }
        return errors;
    }

    private boolean blank(String s) {
        return s == null || s.trim().length() == 0;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String getLossDate() {
        return lossDate;
    }

    public void setLossDate(String lossDate) {
        this.lossDate = lossDate;
    }

    public String getAmountClaimed() {
        return amountClaimed;
    }

    public void setAmountClaimed(String amountClaimed) {
        this.amountClaimed = amountClaimed;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
