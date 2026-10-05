package com.harborclaims.portal.claims;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class TransitionForm extends ActionForm {

    private static final long serialVersionUID = 1L;

    private String id = "";
    private String toStatus = "";
    private String approvedAmount = "";
    private String reason = "";

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if (id == null || !id.trim().matches("[0-9]{1,9}")) {
            errors.add("id", new ActionMessage("error.id.invalid"));
        }
        if (toStatus == null || !ClaimRules.ALL_STATUSES.contains(toStatus.trim())) {
            errors.add("toStatus", new ActionMessage("error.status.invalid"));
        }
        if (reason != null && reason.trim().length() > 900) {
            errors.add("reason", new ActionMessage("error.too.long", "reason", 900));
        }
        return errors;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(String approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
