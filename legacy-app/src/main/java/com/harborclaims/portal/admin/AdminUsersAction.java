package com.harborclaims.portal.admin;

import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.common.Paging;
import com.harborclaims.portal.common.SearchForm;

public class AdminUsersAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        SearchForm f = (SearchForm) form;
        Page<AppUser> result = AdminManager.getInstance().search(f.cleanQ(), Paging.parsePage(f.getPage()));
        request.setAttribute("result", result);
        request.setAttribute("pagerBase", "/admin/users.do?q=" + URLEncoder.encode(f.cleanQ(), "UTF-8") + "&page=");
        return mapping.findForward("success");
    }
}
