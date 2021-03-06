/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.management.system;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hyperic.sigar.SigarException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kaazing.gateway.management.AbstractManagementBean;
import org.kaazing.gateway.management.SummaryManagementInterval;
import org.kaazing.gateway.management.context.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the common functionality between the various classes that provide system-level 'summary' data (e.g., JVM,
 * CpuList).
 */
public abstract class AbstractSystemManagementBean extends AbstractManagementBean implements SystemManagementBean {

    // the name to use for the summary data in the JSON object we send out, e.g. 'jvmData', 'cpuData'.
    private final String dataTypeStr;
            // the string that identifies the type of stats, like 'system stats'. Used in logger msg.
    private final AtomicReference<JSONArray> summaryDataList;

    private boolean notificationsEnabled;

    protected boolean errorShown;

    private SummaryManagementInterval gatherInterval;

    private String schedulerName;

    private ScheduledExecutorService gatherScheduler;

    private ScheduledFuture gatherSchedulerFuture;

    private static final Logger logger = LoggerFactory.getLogger(AbstractSystemManagementBean.class);

    public AbstractSystemManagementBean(ManagementContext managementContext,
                                        SummaryManagementInterval summaryInterval,
                                        String[] summaryDataFields,
                                        SummaryManagementInterval gatherInterval,
                                        String dataTypeStr,
                                        String schedulerName) {
        super(managementContext, summaryInterval, summaryDataFields);
        this.dataTypeStr = dataTypeStr;
        this.summaryDataList = new AtomicReference<>(new JSONArray());
        this.schedulerName = schedulerName;
        this.gatherInterval = gatherInterval;

        managementContext.addManagementStrategyChangeListener(this);
    }

    // per bugs KG-12946 and KG-13668, we need to delay using gatherScheduler
    // until the gateway starts (i.e., not during init()). We'll
    // just lazily retrieve/create it here.
    public ScheduledExecutorService getGatherScheduler() {
        if (gatherScheduler == null) {
            gatherScheduler = getSchedulerProvider().getScheduler(schedulerName, false);
        }

        return gatherScheduler;
    }

    public String getSummaryData() {
        // get existing data and clear the array before sending out, so
        // we don't get into collisions while processing it.
        JSONArray jsonArray = summaryDataList.getAndSet(new JSONArray());
        return jsonArray.toString();
    }

    public void enableNotifications(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean areNotificationsEnabled() {
        return notificationsEnabled;
    }

    /**
     * Return the current gather interval. The reason this is abstract is because the actual value needs to be stored/accessed
     * from an external object that is specific to each type of summary data provider.
     */
    public final int getSummaryDataGatherInterval() {
        return gatherInterval.getInterval();
    }

    public final void setSummaryDataGatherInterval(int interval) {
        this.gatherInterval.setInterval(interval);
    }

    /**
     * Implement the ManagementStrategyChangeListener interface. All we're really trying to do is get told that the strategy has
     * changed, so we can get the new strategy and start it (and end the previous one, if we had one).
     */
    public void managementStrategyChanged() {
        ManagementSystemStrategy systemStrategy = managementContext.getManagementSystemStrategy();
        systemStrategy.gatherStats(this);
    }

    /**
     * Do actual gathering of stats now (i.e. as 'execute' rather than 'schedule').
     * <p/>
     * THIS ROUTINE IS CALLED INITIALLY ON AN IO THREAD, BUT MUST RUN *OFF* THE IO THREAD.
     */
    public void gatherStats() {
        managementContext.runManagementTask(new Runnable() {
            public void run() {
                try {
                    // System.out.println("SystemMngtBean.gatherStats for " + Utils.getClassName(AbstractSystemManagementBean
                    // .this));
                    long readTime = System.currentTimeMillis();

                    // record all the items into an object, then put that into
                    // the summaryDataList JSONArray.
                    JSONObject jsonObj = new JSONObject();

                    doGatherStats(jsonObj, readTime);

                    jsonObj.put("readTime", readTime);

                    summaryDataList.get().put(jsonObj);
                } catch (SigarException ex) {
                    if (!errorShown) {
                        logger.warn("Caught SIGAR exception trying to get " + dataTypeStr, ex);
                        errorShown = true;
                    }
                } catch (JSONException ex) {
                    // We should really never get here
                    if (!errorShown) {
                        logger.warn("Caught JSON exception trying to get " + dataTypeStr, ex);
                        errorShown = true;
                    }
                } catch (Exception ex) {
                    if (!errorShown) {
                        logger.warn("Caught unexpected exception trying to get " + dataTypeStr, ex);
                        errorShown = true;
                    }
                }

                // tell the listeners we have some new data. They'll decide what to do.
                markChanged();

                // It is possible that the strategy changes while we're running.
                // Therefore, re-gather the strategy and let it control scheduling
                // our next invocation of gatherStats using the current value of
                // the gatherInterval, which is under management user control.
                ManagementSystemStrategy systemStrategy = managementContext.getManagementSystemStrategy();
                systemStrategy.continueGatherStats(AbstractSystemManagementBean.this);
            }
        });
    }

    /**
     * Schedule the next round of stats gathering.
     * <p/>
     * THIS ROUTINE MUST RUN *OFF* THE IO THREAD.
     */
    public void continueGatherStats() {
        gatherSchedulerFuture = getGatherScheduler().schedule(new Runnable() {
            @Override
            public void run() {
                gatherStats();
            }
        }, getSummaryDataGatherInterval(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stop any currently-scheduled round of stats gathering
     */
    public void stopGatherStats() {
        if (gatherSchedulerFuture != null) {
            if (!gatherSchedulerFuture.isDone()) {
                gatherSchedulerFuture.cancel(false);
            }
            gatherSchedulerFuture = null;
        }
    }

    /**
     * The portion of 'gatherStats' that's specific to the particular stats (e.g., storing the relevant stats locally in the
     * object. The object is supposed to gather and store the summary data values as needed. We'll add the readTime.
     */
    public abstract void doGatherStats(JSONObject jsonObj, long readTime) throws SigarException, JSONException;
}
