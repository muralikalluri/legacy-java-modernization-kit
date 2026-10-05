package com.harborclaims.portal.customers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;

public class CustomerViewAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        int id = intParam(request, "id");
        Customer c = CustomerManager.getInstance().get(id);
        request.setAttribute("customer", c);
        request.setAttribute("policies", CustomerManager.getInstance().policiesOf(id));
        return mapping.findForward("success");
    }
}
