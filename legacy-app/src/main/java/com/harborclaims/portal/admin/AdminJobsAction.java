package com.harborclaims.portal.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import com.harborclaims.portal.common.BaseAction;
import com.harborclaims.portal.jobs.JobRunDao;
import com.harborclaims.portal.jobs.SchedulerListener;

public class AdminJobsAction extends BaseAction {

    @Override
    protected ActionForward doExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        request.setAttribute("runs", new JobRunDao().recent(10));
        request.setAttribute("scheduled", Boolean.valueOf(SchedulerListener.isScheduled()));
        request.setAttribute("nextFire", SchedulerListener.nextFire());
        request.setAttribute("busy", Boolean.valueOf("1".equals(request.getParameter("busy"))));
        return mapping.findForward("success");
    }
}
