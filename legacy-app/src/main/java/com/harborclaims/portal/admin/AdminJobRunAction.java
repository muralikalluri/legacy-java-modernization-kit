package com.harborclaims.portal.admin;

import java.time.LocalDate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.jobs.JobRun;
import com.harborclaims.portal.jobs.PolicyExpiryRunner;
import com.harborclaims.portal.reports.ReportManager;

/**
 * Deliberately insecure for demonstration. Do not deploy.
 * Runs the policy expiry job now. SEEDED FLAW: the mapping has no roles= attribute, so any signed-in user
 * can trigger it. Redirects afterwards so a page refresh does not run it again.
 */
public class AdminJobRunAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        LocalDate asOf;
        try {
            asOf = ReportManager.getInstance().parseAsOf(request.getParameter("asOf"));
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            return mapping.findForward("jobs");
        }
        JobRun run = PolicyExpiryRunner.run(asOf, PolicyExpiryRunner.MANUAL, currentUser(request).getUsername());
        if (run == null) {
            return new ActionForward("/admin/jobs.do?busy=1", true);
        }
        return new ActionForward("/admin/jobs.do?run=" + run.getId(), true);
    }
}
