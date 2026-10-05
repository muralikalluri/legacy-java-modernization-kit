package com.harborclaims.portal.policies;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

public class PolicyEditAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        PolicyForm f = (PolicyForm) form;
        String raw = request.getParameter("id");
        if ("GET".equals(request.getMethod()) && raw != null && raw.trim().length() > 0) {
            f.load(PolicyManager.getInstance().get(intParam(request, "id")));
        } else if ("GET".equals(request.getMethod()) && request.getParameter("customerId") != null) {
            f.setCustomerId(request.getParameter("customerId"));
        }
        return mapping.findForward("success");
    }
}
