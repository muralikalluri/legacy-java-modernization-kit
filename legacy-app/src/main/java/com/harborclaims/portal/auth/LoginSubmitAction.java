package com.harborclaims.portal.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

public class LoginSubmitAction extends Action {

    private static final Logger LOG = Logger.getLogger(LoginSubmitAction.class);

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        LoginForm f = (LoginForm) form;
        AppUser user = AuthManager.getInstance().authenticate(f.getUsername(), f.getPassword());
        if (user == null) {
            LOG.warn("Login failed for user " + f.getUsername());
            ActionErrors errors = new ActionErrors();
            errors.add(ActionErrors.GLOBAL_MESSAGE, new ActionMessage("error.login.invalid"));
            saveErrors(request, errors);
            f.setPassword("");
            return mapping.findForward("failure");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(LoginFilter.SESSION_USER, user);
        long now = System.currentTimeMillis();
        session.setAttribute(LoginFilter.AUTH_TIME, Long.valueOf(now));
        String sid = AuthBridge.newSid();
        session.setAttribute(LoginFilter.AUTH_SID, sid);
        long exp = AuthBridge.issue(response, user.getUsername(), sid, now, now);
        if (exp > 0) {
            session.setAttribute(LoginFilter.COOKIE_EXP, Long.valueOf(exp));
        }
        LOG.info("Login ok for user " + user.getUsername() + " role " + user.getRole());
        return mapping.findForward("success");
    }
}
