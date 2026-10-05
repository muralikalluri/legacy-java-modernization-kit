package com.harborclaims.portal.customers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

public class CustomerEditAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        CustomerForm f = (CustomerForm) form;
        String raw = request.getParameter("id");
        if (raw != null && raw.trim().length() > 0) {
            f.load(CustomerManager.getInstance().get(intParam(request, "id")));
        }
        return mapping.findForward("success");
    }
}
