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
import org.terasology.assets.management.AssetManager;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.factions.components.FactionComponent;
import org.terasology.factions.components.FactionDatabaseComponent;
import org.terasology.factions.components.FactionMemberComponent;
import org.terasology.factions.policies.FactionPolicySystem;
import org.terasology.factions.policies.policies.InternalPolicy;
import org.terasology.factions.policies.policies.Policy;
import org.terasology.factions.policies.PolicyType;
import org.terasology.factions.policies.PolicyComponent;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.registry.In;
import org.terasology.registry.Share;

import java.util.HashMap;
import java.util.Map;

@RegisterSystem
@Share(FactionSystem.class)
public class FactionSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    private EntityRef database;
    private Map<String, FactionComponent> factions = new HashMap<>();

    @In
    private EntityManager entityManager;
    @In
    private AssetManager assetManager;
    @In
    private PrefabManager prefabManager;
    @In
    private FactionPolicySystem factionPolicySystem;

    private static final Logger logger = LoggerFactory.getLogger(FactionSystem.class);

    @Override
    public void update(float delta) {
        if (entityManager.getCountOfEntitiesWith(FactionDatabaseComponent.class) != 0) {
            return;
        }

        EntityBuilder databaseBuilder = entityManager.newBuilder();
        databaseBuilder.addComponent(new FactionDatabaseComponent());
        for (Class<? extends Policy> policyClass : factionPolicySystem.getPolicyClasses()) {
            databaseBuilder.addComponent(factionPolicySystem.getPolicyComponent(policyClass));
        }

        database = databaseBuilder.build();

        for (Prefab prefab : prefabManager.listPrefabs(FactionComponent.class)) {
            FactionComponent factionComponent = prefab.getComponent(FactionComponent.class);
            createFaction(factionComponent);
        }

    }

    private boolean isExistingFaction(String factionName) {
        return factions.containsKey(factionName);
    }

    private String getTwoWayPolicyKey(String factionOne, String factionTwo) {
        if (factionOne.compareTo(factionTwo) == 1) {
            return factionOne + "`" + factionTwo;
        } else {
            return factionTwo + "`" + factionOne;
        }
    }

    private void createFaction(FactionComponent newFactionComponent) {
        String newFaction = newFactionComponent.name;
        if (isExistingFaction(newFaction)) {
            logger.error("Faction with name " + newFaction + " already exists.");
            return;
        }


        for (Class<? extends Policy> policyClass : factionPolicySystem.getPolicyClasses()) {
            final Class<? extends PolicyComponent> policyComponentClass = factionPolicySystem
                    .getPolicyComponentClass(policyClass);

            PolicyComponent policyComponent = database.getComponent(policyComponentClass);

            PolicyType policyType = PolicyType.getPolicyType(policyClass);
            if (policyType == PolicyType.INTERNAL) {
                policyComponent.getPolicyMap().put(newFaction,
                        policyComponent.newDefaultPolicy());
            } else {
                for (String existingFaction : factions.keySet()) {
                    if (policyType == PolicyType.ONE_WAY) {
                        policyComponent.getPolicyMap().put(newFaction + "`" + existingFaction,
                                policyComponent.newDefaultPolicy());
                        policyComponent.getPolicyMap().put(existingFaction + "`" + newFaction,
                                policyComponent.newDefaultPolicy());
                    } else {
                        String key = getTwoWayPolicyKey(newFaction, existingFaction);
                        policyComponent.getPolicyMap().put(key, policyComponent.newDefaultPolicy());
                    }
                }
            }

            database.saveComponent(policyComponent);
        }

        factions.put(newFaction, newFactionComponent);
    }

    private <T extends Policy> PolicyComponent getPolicyComponent(Class<T> policyClass) {
        Class<? extends PolicyComponent> policyComponentClass = factionPolicySystem.getPolicyComponentClass(policyClass);
        return database.getComponent(policyComponentClass);
    }

    public <T extends InternalPolicy> T getInternalPolicy(String factionName, Class<T> internalPolicyClass) {
        if (!isExistingFaction(factionName)) {
            logger.error("Faction " + factionName + " does not exist");
            return null;
        }

        if (!InternalPolicy.class.isAssignableFrom(internalPolicyClass)) {
            logger.error(internalPolicyClass.getName() + " is not a subclass of InternalPolicy");
            return null;

        }

        return (T) getPolicyComponent(internalPolicyClass).getPolicyMap().get(factionName);
    }

    public <T extends InternalPolicy> void saveInternalPolicy(String factionName, T internalPolicy) {
        if (!isExistingFaction(factionName)) {
            logger.error("Faction " + factionName + " does not exist");
            return;
        }

        PolicyComponent policyComponent = getPolicyComponent(internalPolicy.getClass());
        policyComponent.getPolicyMap().put(factionName, internalPolicy);

        database.saveComponent(policyComponent);
    }

/*
    public <T extends OneWayPolicy> T getOneWayPolicy(String firstFactionName, String secondFactionName
            , Class<T> oneWayPolicyClass) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return null;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return null;
        }

    }

    public <T extends OneWayPolicy> void saveOneWayPolicy(String firstFactionName, String secondFactionName, T oneWayPolicy) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return;
        }
        OrderedPair<String, String> factionPair = new OrderedPair<>(firstFactionName, secondFactionName);
    }

    public <T extends TwoWayPolicy> T getTwoWayPolicy(String firstFactionName, String secondFactionName
            , Class<T> twoWayPolicyClass) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return null;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return null;
        }
        UnorderedPair<String, String> factionPair = new UnorderedPair<>(firstFactionName, secondFactionName);
    }

    public <T extends TwoWayPolicy> void saveTwoWayPolicy(String firstFactionName, String secondFactionName
            , T twoWayPolicy) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return;
        }
        UnorderedPair<String, String> factionPair = new UnorderedPair<>(firstFactionName, secondFactionName);
    }
*/


    @ReceiveEvent
    public void onPlayerSpawn(OnPlayerSpawnedEvent event, EntityRef entity) {
        entity.saveComponent(new FactionMemberComponent("Elves"));
    }

}
