package com.harborclaims.portal.reports;

import java.time.LocalDate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;

public class PolicyExpiryAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        ReportManager mgr = ReportManager.getInstance();
        try {
            LocalDate asOf = mgr.parseAsOf(request.getParameter("asOf"));
            int days = mgr.parseDays(request.getParameter("days"));
            request.setAttribute("asOf", asOf.toString());
            request.setAttribute("days", Integer.valueOf(days));
            request.setAttribute("untilDate", asOf.plusDays(days).toString());
            request.setAttribute("policies", mgr.policyExpiry(asOf, days));
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            request.setAttribute("policies", java.util.Collections.emptyList());
        }
        return mapping.findForward("success");
    }
}
