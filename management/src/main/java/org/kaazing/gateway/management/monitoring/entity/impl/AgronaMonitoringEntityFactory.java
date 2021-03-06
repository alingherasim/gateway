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

package org.kaazing.gateway.management.monitoring.entity.impl;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.kaazing.gateway.management.agrona.ex.AtomicStringEntity;
import org.kaazing.gateway.management.agrona.ex.StringsManager;
import org.kaazing.gateway.management.monitoring.entity.LongMonitoringCounter;
import org.kaazing.gateway.management.monitoring.entity.StringMonitoringEntity;
import org.kaazing.gateway.management.monitoring.entity.factory.MonitoringEntityFactory;

import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.CountersManager;

/**
 * MonitoringEntityFactory which provides Agrona specific monitoring entities
 */
public class AgronaMonitoringEntityFactory implements MonitoringEntityFactory {

    private CountersManager countersManager;
    private StringsManager stringsManager;

    // These are needed for the cleanup work that needs to be done in the close method.
    private File monitoringDirectory;
    private MappedByteBuffer mappedMonitorDirectory;
    private List<AtomicCounter> counters = new CopyOnWriteArrayList<AtomicCounter>();
    private List<AtomicStringEntity> stringEntities = new CopyOnWriteArrayList<AtomicStringEntity>();

    public AgronaMonitoringEntityFactory(CountersManager countersManager, StringsManager stringsManager,
            MappedByteBuffer mappedMonitorFile, File monitoringDirectory) {
        this.countersManager = countersManager;
        this.stringsManager = stringsManager;
        this.mappedMonitorDirectory = mappedMonitorFile;
        this.monitoringDirectory = monitoringDirectory;
    }

    @Override
    public LongMonitoringCounter makeLongMonitoringCounter(String name) {
        // We create the new AtomicCounter using the CountersManager and we also add it to the list of counters
        // in order to close them when needed.
        AtomicCounter counter = countersManager.newCounter(name);
        counters.add(counter);

        LongMonitoringCounter longMonitoringCounter = new AgronaLongMonitoringCounter(counter);

        return longMonitoringCounter;
    }

    @Override
    public StringMonitoringEntity makeStringMonitoringEntity(String name, String value) {
        AtomicStringEntity entity = stringsManager.newStringEntity(name, value);
        stringEntities.add(entity);

        StringMonitoringEntity stringMonitoringEntity = new AgronaStringMonitoringEntity(entity);

        return stringMonitoringEntity;
    }

    @Override
    public void close() {
        // We close the counters, the String monitoring entities and the we also need to unmap the file and delete the
        // monitoring directory.
        for (AtomicCounter atomicCounter : counters) {
            atomicCounter.close();
        }

        for (AtomicStringEntity atomicStringEntity : stringEntities) {
            atomicStringEntity.close();
        }

        IoUtil.unmap(mappedMonitorDirectory);

        IoUtil.delete(monitoringDirectory, false);
    }

}
