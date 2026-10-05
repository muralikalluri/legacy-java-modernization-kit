package com.harborclaims.portal.customers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;

public class CustomerSaveAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        CustomerForm f = (CustomerForm) form;
        try {
            int id = CustomerManager.getInstance().save(f.toCustomer());
            return new ActionForward("/customers/view.do?id=" + id, true);
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            return mapping.getInputForward();
        }
    }
}
