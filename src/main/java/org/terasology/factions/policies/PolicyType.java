/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.factions.policies;

import org.terasology.factions.policies.policies.InternalPolicy;
import org.terasology.factions.policies.policies.OneWayPolicy;
import org.terasology.factions.policies.policies.Policy;
import org.terasology.factions.policies.policies.TwoWayPolicy;

public enum PolicyType {
    INTERNAL,
    ONE_WAY,
    TWO_WAY;

    public static PolicyType getPolicyType(Class<? extends Policy> policyClass) {
        if(InternalPolicy.class.isAssignableFrom(policyClass)) {
            return INTERNAL;
        } else if(OneWayPolicy.class.isAssignableFrom(policyClass)) {
            return ONE_WAY;
        } else if(TwoWayPolicy.class.isAssignableFrom(policyClass)) {
            return TWO_WAY;
        } else {
            throw new IllegalArgumentException("not a valid policy class");
        }
    }
}
