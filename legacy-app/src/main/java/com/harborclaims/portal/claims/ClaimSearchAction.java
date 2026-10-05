package com.harborclaims.portal.claims;

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

public class ClaimSearchAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        SearchForm f = (SearchForm) form;
        int page = Paging.parsePage(f.getPage());
        Page<Claim> result = ClaimManager.getInstance().search(f.cleanQ(), f.cleanStatus(), page);
        request.setAttribute("result", result);
        request.setAttribute("statuses", ClaimRules.ALL_STATUSES);
        request.setAttribute("pagerBase", "/claims/search.do?q=" + URLEncoder.encode(f.cleanQ(), "UTF-8")
                + "&status=" + URLEncoder.encode(f.cleanStatus(), "UTF-8") + "&page=");
        return mapping.findForward("success");
    }
}
