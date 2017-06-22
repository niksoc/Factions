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
package org.terasology.factions;

import org.terasology.entitySystem.Component;
import org.terasology.factions.policies.InternalPolicy;
import org.terasology.factions.policies.OneWayPolicy;
import org.terasology.factions.policies.TwoWayPolicy;
import org.terasology.factions.utils.OrderedPair;
import org.terasology.factions.utils.UnorderedPair;

import java.util.Map;

public class FactionDataComponent implements Component {
    Map<String, Map<Class<? extends InternalPolicy>, InternalPolicy> > internalPolicies;
    Map<OrderedPair<String, String>, Map<Class<? extends OneWayPolicy>, OneWayPolicy> > oneWayPolicies;
    Map<UnorderedPair<String , String>, Map<Class<? extends TwoWayPolicy>, TwoWayPolicy> > twoWayPolicies;
}/*public class FactionDataComponent implements Component {
    Map<String, List<InternalPolicy> > internalPolicies;
    Map<OrderedPair<String, String>, List<OneWayPolicy> > oneWayPolicies;
    Map<UnorderedPair<String , String>, List<TwoWayPolicy> > twoWayPolicies;
}*/
