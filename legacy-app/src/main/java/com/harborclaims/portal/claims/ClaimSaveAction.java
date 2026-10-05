package com.harborclaims.portal.claims;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.common.BusinessException;

public class ClaimSaveAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        ClaimForm f = (ClaimForm) form;
        try {
            int id = ClaimManager.getInstance().create(f.getPolicyNumber(), f.getLossDate(), f.getAmountClaimed(),
                    f.getDescription(), currentUser(request));
            return new ActionForward("/claim/view.do?id=" + id, true);
        } catch (BusinessException e) {
            saveErrors(request, errorsFor(e));
            return mapping.getInputForward();
        }
    }
}
