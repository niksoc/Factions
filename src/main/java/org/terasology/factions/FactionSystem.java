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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.terasology.factions.policies.PolicyComponent;
import org.terasology.factions.policies.PolicyType;
import org.terasology.factions.policies.policies.*;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.registry.In;
import org.terasology.registry.Share;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RegisterSystem
@Share(FactionSystem.class)
public class FactionSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    private EntityRef database;
    private Map<String, FactionComponent> factions = new HashMap<>();
    private SetMultimap<Class<? extends InternalPolicy>
            , InternalPolicyChangeCallback> internalPolicySubscribers = HashMultimap.create();
    private SetMultimap<Class<? extends ExternalPolicy>
            , ExternalPolicyChangeCallback> externalPolicySubscribers = HashMultimap.create();

    @In
    private EntityManager entityManager;
    @In
    private PrefabManager prefabManager;
    @In
    private FactionPolicySystem factionPolicySystem;

    private static final Logger logger = LoggerFactory.getLogger(FactionSystem.class);

    public Collection<FactionComponent> getFactions() {
        return factions.values();
    }

    public <T extends InternalPolicy>
    void registerPolicyChangeSubscriber(Class<T> policyClass, InternalPolicyChangeCallback<T> callback) {
        internalPolicySubscribers.put(policyClass, callback);
    }

    public <T extends ExternalPolicy>
    void registerPolicyChangeSubscriber(Class<T> policyClass, ExternalPolicyChangeCallback<T> callback) {
        externalPolicySubscribers.put(policyClass, callback);
    }

    private <T extends InternalPolicy> void notifyInternalPolicyChangeSubscribers(T policy, String faction) {
        internalPolicySubscribers.get(policy.getClass()).forEach(sub -> sub.onPolicyChange(policy, faction));
    }

    private <T extends ExternalPolicy> void notifyExternalPolicyChangeSubscribers(T policy,
                                                                                  String factionOne, String factionTwo) {
        externalPolicySubscribers.get(policy.getClass()).forEach(sub -> sub.onPolicyChange(policy, factionOne, factionTwo));
    }

    @Override
    public void update(float delta) {
        if (database != null) {
            return;
        }

        if (entityManager.getCountOfEntitiesWith(FactionDatabaseComponent.class) != 0) {
            database = Iterables.getOnlyElement(
                    entityManager.getEntitiesWith(FactionDatabaseComponent.class));
            return;
        }

        try {
            Prefab factionDatabasePrefab = Iterables.getOnlyElement(prefabManager.listPrefabs(FactionDatabaseComponent.class));
            database = entityManager.create(factionDatabasePrefab);
        } catch (NoSuchElementException e) {
        } catch (IllegalArgumentException e) {
            logger.error("more than one prefab with FactionDatabaseComponent -> ignoring!");
        }

        if (database == null) {
            database = entityManager.create(new FactionDatabaseComponent());
        }

        for (Class<? extends Policy> policyClass : factionPolicySystem.getPolicyClasses()) {
            final PolicyComponent policyComponent = factionPolicySystem.getPolicyComponent(policyClass);
            if (!database.hasComponent(policyComponent.getClass())) {
                database.addComponent(policyComponent);
            }
        }

        for (Prefab prefab : prefabManager.listPrefabs(FactionComponent.class)) {
            FactionComponent factionComponent = prefab.getComponent(FactionComponent.class);
            createFaction(factionComponent);
        }

    }

    private boolean isExistingFaction(String factionName) {
        return factions.containsKey(factionName);
    }

    private String getTwoWayPolicyKey(String factionOne, String factionTwo) {
        if (factionOne.compareTo(factionTwo) > 0) {
            return factionTwo + "&" + factionOne;
        } else {
            return factionOne + "&" + factionTwo;
        }
    }

    private String getOneWayPolicyKey(String factionOne, String factionTwo) {
        return factionOne + ">" + factionTwo;
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
                policyComponent.getPolicyMap().putIfAbsent(newFaction,
                        policyComponent.newDefaultPolicy());
            } else {
                for (String existingFaction : factions.keySet()) {
                    if (policyType == PolicyType.ONE_WAY) {
                        policyComponent.getPolicyMap().putIfAbsent(getOneWayPolicyKey(newFaction, existingFaction),
                                policyComponent.newDefaultPolicy());
                        policyComponent.getPolicyMap().putIfAbsent(getOneWayPolicyKey(existingFaction, newFaction),
                                policyComponent.newDefaultPolicy());
                    } else {
                        String key = getTwoWayPolicyKey(newFaction, existingFaction);
                        policyComponent.getPolicyMap().putIfAbsent(key, policyComponent.newDefaultPolicy());
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

    public <T extends InternalPolicy> T getInternalPolicy(Class<T> internalPolicyClass, String factionName) {
        if (!isExistingFaction(factionName)) {
            logger.error("Faction " + factionName + " does not exist");
            return null;
        }

        if (!InternalPolicy.class.isAssignableFrom(internalPolicyClass)) {
            logger.error(internalPolicyClass.getName() + " is not a subclass of InternalPolicy");
            return null;

        }


        return (T) ((Policy) (getPolicyComponent(internalPolicyClass).getPolicyMap().get(factionName))).clone();
    }

    public <T extends InternalPolicy> void saveInternalPolicy(T internalPolicy, String factionName) {
        if (!isExistingFaction(factionName)) {
            logger.error("Faction " + factionName + " does not exist");
            return;
        }

        PolicyComponent policyComponent = getPolicyComponent(internalPolicy.getClass());
        policyComponent.getPolicyMap().put(factionName, internalPolicy);

        database.saveComponent(policyComponent);

        notifyInternalPolicyChangeSubscribers(internalPolicy, factionName);

    }

    public <T extends OneWayPolicy> T getOneWayPolicy(Class<T> oneWayPolicyClass, String firstFactionName, String secondFactionName) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return null;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return null;
        }

        return (T) ((Policy) (getPolicyComponent(oneWayPolicyClass).getPolicyMap()
                .get(getOneWayPolicyKey(firstFactionName, secondFactionName)))).clone();
    }

    public <T extends OneWayPolicy> void saveOneWayPolicy(T oneWayPolicy, String firstFactionName, String secondFactionName) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return;
        }

        PolicyComponent policyComponent = getPolicyComponent(oneWayPolicy.getClass());
        policyComponent.getPolicyMap()
                .put(getOneWayPolicyKey(firstFactionName, secondFactionName), oneWayPolicy);

        database.saveComponent(policyComponent);

        notifyExternalPolicyChangeSubscribers(oneWayPolicy, firstFactionName, secondFactionName);
    }

    public <T extends TwoWayPolicy> T getTwoWayPolicy(Class<T> twoWayPolicyClass, String firstFactionName, String secondFactionName) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return null;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return null;
        }

        return (T) ((Policy) (getPolicyComponent(twoWayPolicyClass).getPolicyMap()
                .get(getTwoWayPolicyKey(firstFactionName, secondFactionName)))).clone();

    }

    public <T extends TwoWayPolicy> void saveTwoWayPolicy(T twoWayPolicy, String firstFactionName, String secondFactionName) {
        if (!isExistingFaction(firstFactionName)) {
            logger.error("Faction " + firstFactionName + " does not exist");
            return;
        }
        if (!isExistingFaction(secondFactionName)) {
            logger.error("Faction " + secondFactionName + " does not exist");
            return;
        }
        PolicyComponent policyComponent = getPolicyComponent(twoWayPolicy.getClass());
        policyComponent.getPolicyMap()
                .put(getTwoWayPolicyKey(firstFactionName, secondFactionName), twoWayPolicy);

        database.saveComponent(policyComponent);

        notifyExternalPolicyChangeSubscribers(twoWayPolicy, firstFactionName, secondFactionName);
    }


    @ReceiveEvent
    public void onPlayerSpawn(OnPlayerSpawnedEvent event, EntityRef entity) {
        entity.saveComponent(new FactionMemberComponent("Elves"));
    }

    @FunctionalInterface
    public static interface InternalPolicyChangeCallback<T extends Policy> {
        public void onPolicyChange(T newPolicy, String faction);
    }

    @FunctionalInterface
    public static interface ExternalPolicyChangeCallback<T extends Policy> {
        public void onPolicyChange(T policy, String factionOne, String factionTwo);
    }
}
