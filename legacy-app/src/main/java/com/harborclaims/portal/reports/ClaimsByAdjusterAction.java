package com.harborclaims.portal.reports;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.claims.ClaimManager;
import com.harborclaims.portal.common.BaseAction;

public class ClaimsByAdjusterAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        java.util.List<ClaimManager.AdjusterRow> rows = ReportManager.getInstance().claimsByAdjuster();
        request.setAttribute("rows", rows);
        ClaimManager.AdjusterRow busiest = ClaimManager.busiest(rows);
        request.setAttribute("busiest", busiest == null ? "" : busiest.getName());
        return mapping.findForward("success");
    }
}
