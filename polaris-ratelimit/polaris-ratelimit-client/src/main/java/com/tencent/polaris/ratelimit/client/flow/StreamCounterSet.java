/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.tencent.polaris.ratelimit.client.flow;

import com.tencent.polaris.logging.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

/**
 * 计数器对象
 */
public class StreamCounterSet {

    private static final Logger LOG = LoggerFactory.getLogger(StreamCounterSet.class);

    /**
     * 引用的KEY
     */
    private final AtomicInteger reference = new AtomicInteger();

    private final Object resourceLock = new Object();

    /**
     * 节点唯一标识
     */
    private final HostIdentifier identifier;

    /**
     * 当前的资源
     */
    private final AtomicReference<StreamResource> currentStreamResource = new AtomicReference<>();


    public StreamCounterSet(HostIdentifier identifier) {
        this.identifier = identifier;
    }

    public HostIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * 获取同步阻塞的客户端
     *
     * @return 同步阻塞的客户端
     */
    public StreamResource checkAndCreateResource(ServiceIdentifier serviceIdentifier,
                                                 RateLimitWindow rateLimitWindow) {
        StreamResource streamResource = currentStreamResource.get();
        if (null != streamResource && !streamResource.isEndStream()) {
            return streamResource;
        }
        synchronized (resourceLock) {
            streamResource = currentStreamResource.get();
            if (null == streamResource || streamResource.isEndStream()) {
                LOG.info("[RateLimit] stream resource for {} not exists or destroyed, start to create", identifier);
                streamResource = new StreamResource(identifier);
                currentStreamResource.set(streamResource);
            }
            return streamResource;
        }
    }

    public void addReference() {
        reference.incrementAndGet();
    }

    public boolean decreaseReference() {
        int value = reference.decrementAndGet();
        if (value == 0) {
            synchronized (resourceLock) {
                StreamResource streamResource = currentStreamResource.get();
                if (null != streamResource && !streamResource.isEndStream()) {
                    streamResource.closeStream(true);
                }
            }
            return true;
        }
        return false;
    }

    public void deleteInitRecord(ServiceIdentifier serviceIdentifier) {
        StreamResource streamResource = currentStreamResource.get();
        if (null != streamResource) {
            streamResource.deleteInitRecord(serviceIdentifier);
        }
    }


}
