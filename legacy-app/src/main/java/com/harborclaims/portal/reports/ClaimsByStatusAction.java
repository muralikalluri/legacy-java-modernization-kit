package com.harborclaims.portal.reports;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.claims.ClaimManager;
import com.harborclaims.portal.common.BaseAction;

public class ClaimsByStatusAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        java.util.List<ClaimManager.StatusRow> rows = ReportManager.getInstance().claimsByStatus();
        request.setAttribute("rows", rows);
        request.setAttribute("grandCount", Integer.valueOf(ClaimManager.grandCount(rows)));
        request.setAttribute("grandTotal", ClaimManager.moneyText(ClaimManager.grandTotal(rows)));
        return mapping.findForward("success");
    }
}
