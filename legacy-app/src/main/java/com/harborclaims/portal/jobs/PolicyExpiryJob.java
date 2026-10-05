package com.harborclaims.portal.jobs;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

import com.harborclaims.portal.policies.PolicyManager;

/** Quartz 1.x nightly batch: lapses ACTIVE policies whose end date has passed. */
public class PolicyExpiryJob implements StatefulJob {

    private static final Logger LOG = Logger.getLogger(PolicyExpiryJob.class);

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobRun run = PolicyExpiryRunner.run(PolicyManager.today(), PolicyExpiryRunner.NIGHTLY, "scheduler");
            if (run == null) {
                LOG.warn("Nightly policy expiry skipped: a run is already in progress");
            }
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
