package com.harborclaims.portal.policies;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;

public class PolicySaveAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        PolicyForm f = (PolicyForm) form;
        try {
            int id = PolicyManager.getInstance().save(f, currentUser(request));
            return new ActionForward("/policy/view.do?id=" + id, true);
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            return mapping.getInputForward();
        }
    }
}
