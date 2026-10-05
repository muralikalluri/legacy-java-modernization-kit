package com.harborclaims.portal.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;

/**
 * Deliberately insecure for demonstration. Do not deploy.
 * SEEDED FLAW: the mapping has no roles= attribute, so any signed-in user can call this.
 */
public class AdminUserSaveAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        AdminUserForm f = (AdminUserForm) form;
        try {
            AdminManager.getInstance().save(f, currentUser(request));
            return new ActionForward("/admin/users.do?q=" + java.net.URLEncoder.encode(f.getUsername(), "UTF-8"), true);
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            request.setAttribute("roleNames", AdminManager.getInstance().roleNames());
            return mapping.getInputForward();
        }
    }
}
