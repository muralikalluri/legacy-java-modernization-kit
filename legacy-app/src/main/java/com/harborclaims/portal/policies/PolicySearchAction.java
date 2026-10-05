package com.harborclaims.portal.policies;

import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.common.Paging;
import com.harborclaims.portal.common.SearchForm;

public class PolicySearchAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        SearchForm f = (SearchForm) form;
        int page = Paging.parsePage(f.getPage());
        Page<Policy> result = PolicyManager.getInstance().search(f.cleanQ(), f.cleanStatus(), page);
        request.setAttribute("result", result);
        request.setAttribute("statuses", PolicyRules.ALL_STATUSES);
        request.setAttribute("pagerBase", "/policies/search.do?q=" + URLEncoder.encode(f.cleanQ(), "UTF-8")
                + "&status=" + URLEncoder.encode(f.cleanStatus(), "UTF-8") + "&page=");
        return mapping.findForward("success");
    }
}
