package com.harborclaims.portal.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.common.BaseAction;

public class AdminUserEditAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        AdminUserForm f = (AdminUserForm) form;
        String raw = request.getParameter("id");
        if ("GET".equals(request.getMethod()) && raw != null && raw.trim().length() > 0) {
            AppUser u = AdminManager.getInstance().get(intParam(request, "id"));
            f.setId(String.valueOf(u.getId()));
            f.setUsername(u.getUsername());
            f.setDisplayName(u.getDisplayName());
            f.setRole(u.getRole());
            f.setActive(String.valueOf(u.isActive()));
            f.setPassword("");
        }
        request.setAttribute("roleNames", AdminManager.getInstance().roleNames());
        return mapping.findForward("success");
    }
}
