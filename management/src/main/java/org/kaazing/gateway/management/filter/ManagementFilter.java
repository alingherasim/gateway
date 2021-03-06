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

package org.kaazing.gateway.management.filter;

import java.util.Properties;

import org.apache.mina.core.write.WriteRequest;
import org.kaazing.gateway.management.Utils;
import org.kaazing.gateway.management.Utils.ManagementSessionType;
import org.kaazing.gateway.management.context.ManagementContext;
import org.kaazing.gateway.management.monitoring.entity.StringMonitoringEntity;
import org.kaazing.gateway.management.monitoring.entity.factory.MonitoringEntityFactory;
import org.kaazing.gateway.management.monitoring.entity.manager.ServiceSessionCounterManager;
import org.kaazing.gateway.management.monitoring.entity.manager.factory.CounterManagerFactory;
import org.kaazing.gateway.management.monitoring.entity.manager.impl.CounterManagerFactoryImpl;
import org.kaazing.gateway.management.service.ServiceManagementBean;
import org.kaazing.gateway.service.ServiceContext;
import org.kaazing.gateway.transport.IoFilterAdapter;
import org.kaazing.gateway.util.InternalSystemProperty;
import org.kaazing.mina.core.session.IoSessionEx;

/**
 * Filter for session filter chains. Supports 'strategies' to tailor the amount of processing based on whether management is
 * desired or not at the time. Strategies that actually create a SessionManagementBean store the bean as a session attribute (see
 * IoSession and AttributeKey). All the strategies are stateless and final--they get the sessionManagementBean that they're
 * operating on from the incoming IoSessionEx.
 * <p/>
 * The global strategy is changed from the DefaultManagementContext. As it can be changed at any time, rather than keep it in
 * each ManagementFilter and require it to be updated from DMC, each ManagementFilter will just request it each time it needs to
 * do something.
 * <p/>
 * There is exactly 1 ManagementFilter per service instance (excluding the management services, for which we do not want any
 * management).
 */
public class ManagementFilter extends IoFilterAdapter<IoSessionEx> {

    private static final String SEPARATOR = "-";
    protected ServiceManagementBean serviceBean;
    protected ManagementContext managementContext;
    protected ServiceContext serviceContext;
    private ServiceSessionCounterManager serviceSessionCounterManager;
    private StringMonitoringEntity latestException;
    private static final String LATEST_EXCEPTION = "-latest-exception";

    public ManagementFilter(ServiceManagementBean serviceBean,
                            MonitoringEntityFactory monitoringEntityFactory,
                            String serviceName,
                            Properties configuration) {
        this.serviceBean = serviceBean;
        this.managementContext = serviceBean.getGatewayManagementBean().getManagementContext();
        this.serviceContext = serviceBean.getServiceContext();
        String gatewayId = InternalSystemProperty.GATEWAY_IDENTIFIER.getProperty(configuration);

        CounterManagerFactory counterFactory = new CounterManagerFactoryImpl();
        serviceSessionCounterManager = counterFactory.makeServiceSessionCounterManager(monitoringEntityFactory,
                serviceName, gatewayId);
        serviceSessionCounterManager.initializeCounters();
        latestException = monitoringEntityFactory.makeStringMonitoringEntity(gatewayId +
                SEPARATOR + serviceName + LATEST_EXCEPTION, "");
    }

    public ServiceManagementBean getServiceBean() {
        return this.serviceBean;  // needed for at least JMS
    }

    public ManagementContext getManagementContext() {
        return this.managementContext;
    }

    @Override
    protected void doSessionClosed(NextFilter nextFilter, IoSessionEx session) throws Exception {

        ManagementSessionType managementSessionType = Utils.getManagementSessionType(session);
        managementContext.getManagementFilterStrategy()
                .doSessionClosed(managementContext, serviceBean, session.getId(), managementSessionType);
        managementContext.decrementOverallSessionCount();
        serviceSessionCounterManager.decrementCounters(managementSessionType);
        latestException.reset();

        super.doSessionClosed(nextFilter, session);
    }

    @Override
    protected void doMessageReceived(NextFilter nextFilter, IoSessionEx session, Object message) throws Exception {
        managementContext.getManagementFilterStrategy()
                .doMessageReceived(managementContext, serviceBean, session.getId(), session.getReadBytes(), message);
        super.doMessageReceived(nextFilter, session, message);
    }

    @Override
    protected void doFilterWrite(NextFilter nextFilter, IoSessionEx session, WriteRequest writeRequest) throws Exception {
        managementContext.getManagementFilterStrategy()
                .doFilterWrite(managementContext, serviceBean, session.getId(), session.getWrittenBytes(), writeRequest);
        super.doFilterWrite(nextFilter, session, writeRequest);
    }

    @Override
    protected void doExceptionCaught(NextFilter nextFilter, IoSessionEx session, Throwable cause) throws Exception {
        managementContext.getManagementFilterStrategy()
                .doExceptionCaught(managementContext, serviceBean, session.getId(), cause);
        latestException.setValue(cause.getMessage());
        super.doExceptionCaught(nextFilter, session, cause);
    }

    //
    // Add a new method that informs the management layer there is a new session because of a
    // possible problem with getting writes before doSessionOpened has been called,
    // (per Chris B, it's to fix failures in SOCKS forward connectivity tests because events
    // like filterWrite were sometimes occurring before sessionOpened.  This method delegates
    // to the strategy objects to present the new session if desired.
    //
    public void newManagementSession(IoSessionEx session) throws Exception {
        managementContext.incrementOverallSessionCount();
        ManagementSessionType managementSessionType = Utils.getManagementSessionType(session);

        // Because strategy may change during execution of this method, refetch it when we need it.
        managementContext.getManagementFilterStrategy()
                .doSessionCreated(managementContext, serviceBean, session, managementSessionType);
        serviceSessionCounterManager.incrementCounters(managementSessionType);
    }
}
