/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.persistence;

import java.util.Map;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.util.typedef.T2;

/**
 *
 */
public interface DbCheckpointListener {
    /**
     * Context with information about current snapshots.
     */
    public interface Context {
        /**
         *
         */
        public boolean nextSnapshot();

        /**
         *
         */
        public Map<T2<Integer, Integer>, T2<Integer, Integer>> partitionStatMap();

        /**
         * @param cacheOrGrpName Cache or group name.
         */
        public boolean needToSnapshot(String cacheOrGrpName);
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    public void onCheckpointBegin(Context ctx) throws IgniteCheckedException;
}
