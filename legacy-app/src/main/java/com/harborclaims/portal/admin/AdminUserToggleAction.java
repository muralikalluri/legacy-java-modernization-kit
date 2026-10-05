package com.harborclaims.portal.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;

public class AdminUserToggleAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        int id = intParam(request, "id");
        try {
            AdminManager.getInstance().toggle(id, currentUser(request));
            return mapping.findForward("success");
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            return mapping.findForward("users");
        }
    }
}
