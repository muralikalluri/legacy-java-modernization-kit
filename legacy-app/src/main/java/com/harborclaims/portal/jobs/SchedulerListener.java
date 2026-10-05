package com.harborclaims.portal.jobs;

import java.util.Date;
import java.util.Properties;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Logger;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import com.harborclaims.portal.common.Db;

/**
 * Starts the Quartz scheduler with the app. Off unless -Dharbor.jobs.enabled=true, so the demo data never
 * changes behind a test's back. Nothing runs at startup: the only trigger is the nightly cron.
 */
public class SchedulerListener implements ServletContextListener {

    public static final String DEFAULT_CRON = "0 0 2 * * ?";

    private static final Logger LOG = Logger.getLogger(SchedulerListener.class);
    private static volatile Scheduler scheduler;
    private static volatile CronTrigger trigger;

    public void contextInitialized(ServletContextEvent event) {
        if (!Boolean.getBoolean("harbor.jobs.enabled")) {
            LOG.info("Scheduler disabled (harbor.jobs.enabled is not true)");
            return;
        }
        try {
            Db.get().close();
            Properties props = new Properties();
            props.setProperty("org.quartz.scheduler.instanceName", "HarborScheduler");
            props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
            props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            props.setProperty("org.quartz.threadPool.threadCount", "1");
            props.setProperty("org.quartz.threadPool.threadsInheritContextClassLoaderOfInitializingThread", "true");
            props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

            Scheduler s = new StdSchedulerFactory(props).getScheduler();
            JobDetail job = new JobDetail("policyExpiry", "harbor", PolicyExpiryJob.class);
            CronTrigger t = new CronTrigger("nightly", "harbor", System.getProperty("harbor.jobs.cron", DEFAULT_CRON));
            t.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
            s.scheduleJob(job, t);
            s.start();
            scheduler = s;
            trigger = t;
            LOG.info("Scheduler started, next policy expiry run at " + t.getNextFireTime());
        } catch (Exception e) {
            LOG.error("Scheduler failed to start", e);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        Scheduler s = scheduler;
        if (s != null) {
            try {
                s.shutdown(true);
            } catch (Exception e) {
                LOG.warn("Scheduler shutdown problem: " + e.getMessage());
            }
            scheduler = null;
            trigger = null;
        }
    }

    public static boolean isScheduled() {
        return scheduler != null;
    }

    public static String nextFire() {
        CronTrigger t = trigger;
        Date next = t == null ? null : t.getNextFireTime();
        return next == null ? "" : next.toString();
    }
}
