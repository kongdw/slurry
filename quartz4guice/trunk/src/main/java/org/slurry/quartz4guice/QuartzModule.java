/*
 *    Copyright 2009-2010 The slurry Team
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.slurry.quartz4guice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.SchedulerListener;
import org.quartz.Trigger;
import org.quartz.TriggerListener;
import org.quartz.spi.JobFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.multibindings.Multibinder;

/**
 * 
 * @version $Id$
 */
public final class QuartzModule extends AbstractModule {

    private final Set<Class<? extends JobListener>> globalJobListeners = new HashSet<Class<? extends JobListener>>();

    private final Set<Class<? extends JobListener>> jobListeners = new HashSet<Class<? extends JobListener>>();

    private final Set<Class<? extends TriggerListener>> globalTriggerListeners = new HashSet<Class<? extends TriggerListener>>();

    private final Set<Class<? extends TriggerListener>> triggerListeners = new HashSet<Class<? extends TriggerListener>>();

    private final Set<Class<? extends SchedulerListener>> schedulerListeners = new HashSet<Class<? extends SchedulerListener>>();

    private final Map<JobDetail, Trigger> jobMaps = new HashMap<JobDetail, Trigger>();

    private final Class<? extends Provider<SchedulerFactory>> schedulerFactoryProviderClass;

    public QuartzModule() {
        this(DefaultSchedulerFactoryProvider.class);
    }

    public QuartzModule(Class<? extends Provider<SchedulerFactory>> schedulerFactoryProviderClass) {
        this.schedulerFactoryProviderClass = schedulerFactoryProviderClass;
    }

    public QuartzModule addGlobalJobListener(Class<? extends JobListener> jobListenerType) {
        return this.addElement(this.globalJobListeners, jobListenerType);
    }

    public QuartzModule addJobListener(Class<? extends JobListener> jobListenerType) {
        return this.addElement(this.jobListeners, jobListenerType);
    }

    public QuartzModule addGlobalTriggerListener(Class<? extends TriggerListener> triggerListenerType) {
        return this.addElement(this.globalTriggerListeners, triggerListenerType);
    }

    public QuartzModule addTriggerListener(Class<? extends TriggerListener> triggerListenerType) {
        return this.addElement(this.triggerListeners, triggerListenerType);
    }

    public QuartzModule addSchedulerListener(Class<? extends SchedulerListener> schedulerListenerType) {
        return this.addElement(this.schedulerListeners, schedulerListenerType);
    }

    private <I> QuartzModule addElement(Set<Class<? extends I>> set, Class<? extends I> type) {
        set.add(type);
        return this;
    }

    public QuartzModule addJob(Class<? extends Job> jobClass) throws Exception {
        if (!jobClass.isAnnotationPresent(Scheduled.class)) {
            throw new Exception("This method allows only '"
                    + Job.class.getName()
                    + "' implementations annotated with @"
                    + Scheduled.class.getName());
        }

        Scheduled scheduled = jobClass.getAnnotation(Scheduled.class);

        JobDetail jobDetail = new JobDetail(scheduled.jobName(), // job name
                scheduled.jobGroup(), // job group (you can also specify 'null'
                                   // to use the default group)
                jobClass, // the java class to execute
                scheduled.volatility(),
                scheduled.durability(),
                scheduled.recover());

        for (String jobListenerName : scheduled.jobListenerNames()) {
            jobDetail.addJobListener(jobListenerName);
        }

        String triggerName = null;
        if (Scheduled.DEFAULT.equals(scheduled.triggerName())) {
            triggerName = jobClass.getCanonicalName();
        } else {
            triggerName = scheduled.triggerName();
        }

        TimeZone timeZone = null;
        if (Scheduled.DEFAULT.equals(scheduled.timeZoneId())) {
            timeZone = TimeZone.getDefault();
        } else {
            timeZone = TimeZone.getTimeZone(scheduled.timeZoneId());
            if (timeZone == null) {
                timeZone = TimeZone.getDefault();
            }
        }

        CronTrigger trigger = new CronTrigger(triggerName,
                scheduled.triggerGroup(),
                scheduled.jobName(),
                scheduled.jobGroup(),
                scheduled.cronExpression(),
                timeZone);

        return this.addJob(jobDetail, trigger);
    }

    public QuartzModule addJob(JobDetail jobDetail, Trigger trigger) {
        this.jobMaps.put(jobDetail, trigger);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    protected void configure() {
        this.bind(SchedulerFactory.class).toProvider(this.schedulerFactoryProviderClass).in(Scopes.SINGLETON);
        this.bind(JobFactory.class).to(InjectorJobFactory.class);
        this.bind(Scheduler.class).toProvider(SchedulerProvider.class);
        this.bind(new TypeLiteral<Map<JobDetail, Trigger>>() {}).toInstance(this.jobMaps);

        bind(this.binder(), this.globalJobListeners, JobListener.class, true);
        bind(this.binder(), this.jobListeners, JobListener.class, false);
        bind(this.binder(), this.schedulerListeners, SchedulerListener.class, false);
        bind(this.binder(), this.globalTriggerListeners, TriggerListener.class, true);
        bind(this.binder(), this.triggerListeners, TriggerListener.class, false);
    }

    private static <I> void bind(Binder binder, Set<Class<? extends I>> set, Class<I> type, boolean global) {
        if (!set.isEmpty()) {
            Multibinder<I> multibinder = Multibinder.newSetBinder(binder, type);
            for (Class<? extends I> currentType : set) {
                ScopedBindingBuilder bindingBuilder = multibinder.addBinding().to(currentType);
                if (global) {
                    bindingBuilder.in(Global.class);
                }
            }
        }
    }

}
