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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.factions.policies.InternalPolicy;
import org.terasology.factions.policies.OneWayPolicy;
import org.terasology.factions.policies.TwoWayPolicy;
import org.terasology.factions.utils.OrderedPair;
import org.terasology.factions.utils.UnorderedPair;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.registry.Share;

import java.util.HashMap;
import java.util.Map;

@RegisterSystem
@Share(FactionSystem.class)
public class FactionSystem extends BaseComponentSystem{
    /* I've used strings as UIDs for factions, but as faction names may change, probably use Integers?
     * but integers may be difficult from a content creator point of view, probably separate faction editor
     * software needed.
     */
    private Map<String, Map<Class<? extends InternalPolicy>, InternalPolicy> > internalPolicies;
    private Map<OrderedPair<String, String>, Map<Class<? extends OneWayPolicy>, OneWayPolicy> > oneWayPolicies;
    private Map<UnorderedPair<String , String>, Map<Class<? extends TwoWayPolicy>, TwoWayPolicy> > twoWayPolicies;

    private static final Logger logger = LoggerFactory.getLogger(FactionSystem.class);

    public FactionSystem() {
        internalPolicies = new HashMap<>();
        oneWayPolicies = new HashMap<>();
        twoWayPolicies = new HashMap<>();
    }

    private boolean isExistingFaction(String factionName) {
        return internalPolicies.get(factionName) != null;
    }

    public void createFaction(String newFactionName) {
        if(isExistingFaction(newFactionName)) {
            logger.error("Faction with name " + newFactionName + " already exists.");
            return;
        }

        for(String existingFaction : internalPolicies.keySet()) {
            OrderedPair<String, String> a_b = new OrderedPair<>(existingFaction, newFactionName);
            OrderedPair<String, String> b_a = new OrderedPair<>(newFactionName, existingFaction);
            oneWayPolicies.put(a_b, new HashMap<>());
            oneWayPolicies.put(b_a, new HashMap<>());
        }

        for(String existingFaction : internalPolicies.keySet()) {
            UnorderedPair<String, String> factionPair = new UnorderedPair<>(existingFaction, newFactionName);
            twoWayPolicies.put(factionPair, new HashMap<>());
        }

        internalPolicies.put(newFactionName, new HashMap<>());
    }

    public <T extends InternalPolicy> T getInternalPolicy(String factionName, Class<T> internalPolicyClass) {
        if(!isExistingFaction(factionName)) {
            logger.error("Faction " + factionName + " does not exist");
            return null;
        }
        return (T) internalPolicies.get(factionName).get(internalPolicyClass);
    }

    public <T extends InternalPolicy> void saveInternalPolicy(String factionName, T internalPolicy) {
        if(!isExistingFaction(factionName)) {
            logger.error("Faction " + factionName + " does not exist");
            return;
        }
        internalPolicies.get(factionName).put(internalPolicy.getClass(), internalPolicy);
    }

    public <T extends OneWayPolicy> T getOneWayPolicy(String firstFactionName, String secondFactionName
            , Class<T> oneWayPolicyClass) {
        if(!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return null;
        }
        if(!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return null;
        }
        OrderedPair<String, String> factionPair = new OrderedPair<>(firstFactionName, secondFactionName);
        return (T) oneWayPolicies.get(factionPair).get(oneWayPolicyClass);
    }

    public <T extends OneWayPolicy> void saveOneWayPolicy(String firstFactionName, String secondFactionName, T oneWayPolicy) {
        if(!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return;
        }
        if(!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return;
        }
        OrderedPair<String, String> factionPair = new OrderedPair<>(firstFactionName, secondFactionName);
        oneWayPolicies.get(factionPair).put(oneWayPolicy.getClass(), oneWayPolicy);
    }

    public <T extends TwoWayPolicy> T getTwoWayPolicy(String firstFactionName, String secondFactionName
            , Class<T> twoWayPolicyClass) {
        if(!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return null;
        }
        if(!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return null;
        }
        UnorderedPair<String, String> factionPair = new UnorderedPair<>(firstFactionName, secondFactionName);
        return (T) twoWayPolicies.get(factionPair).get(twoWayPolicyClass);
    }

    public <T extends TwoWayPolicy> void saveTwoWayPolicy(String firstFactionName, String secondFactionName
            , T twoWayPolicy) {
        if(!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return;
        }
        if(!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return;
        }
        UnorderedPair<String, String> factionPair = new UnorderedPair<>(firstFactionName, secondFactionName);
        twoWayPolicies.get(factionPair).put(twoWayPolicy.getClass(), twoWayPolicy);
    }

    @Override
    public void initialise() {
        // testing done here
        createFaction("Elves");
        createFaction("Dwarves");
        createFaction("Humans");
    }

    @ReceiveEvent
    public void onPlayerSpawn(OnPlayerSpawnedEvent event, EntityRef entity) {
        entity.saveComponent(new FactionComponent("Elves"));
    }

}
