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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.Component;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.prefab.PrefabManager;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.factions.policies.policies.Policy;
import org.terasology.factions.ui.FactionPolicyComponent;
import org.terasology.reflection.MappedContainer;
import org.terasology.registry.In;
import org.terasology.registry.Share;

import java.util.*;

@RegisterSystem
@Share(FactionPolicySystem.class)
public class FactionPolicySystem extends BaseComponentSystem{
    private static final Logger logger = LoggerFactory.getLogger(FactionPolicySystem.class);

    private BiMap<Class<? extends Policy>, PolicyComponent> policyComponents = HashBiMap.create();
    private BiMap<Class<? extends Policy>, FactionPolicyComponent> policies = HashBiMap.create();
    private Map<String, List<FactionPolicyComponent>> categoryComponents = Maps.newHashMap();
    private List<String> categories;

    @In
    private EntityManager entityManager;
    @In
    private PrefabManager prefabManager;

    @Override
    public void preBegin() {
        refreshLibrary();
    }

    public void refreshLibrary() {
        refreshPrefabs();
        sortLibrary();
    }

    private void sortLibrary() {
        categories = Lists.newArrayList(categoryComponents.keySet());
        Collections.sort(categories);
        for (String category : categories) {
            Collections.sort(categoryComponents.get(category), Comparator.comparing(FactionPolicyComponent::toString));
        }
    }

    private void refreshPrefabs() {
        Collection<Prefab> prefabs = prefabManager.listPrefabs(FactionPolicyComponent.class);
        for (Prefab prefab : prefabs) {
            EntityBuilder entityBuilder = entityManager.newBuilder(prefab);
            entityBuilder.setPersistent(false);
            EntityRef entityRef = entityBuilder.build();

            final FactionPolicyComponent factionPolicyComponent = entityRef.getComponent(FactionPolicyComponent.class);
            PolicyComponent policyComponent = null;
            Class<? extends Policy> policyClass = null;

            int i = 0;
            for (Component component : entityRef.iterateComponents()) {
                if (component instanceof PolicyComponent) {
                    policyComponent = (PolicyComponent) component;
                    policyClass = policyComponent.newDefaultPolicy().getClass();
                }
                i++;
            }

            if (i > 3) {
                logger.warn("Extra Components found in Policy prefab -> ignoring! for FactionPolicy "
                        + factionPolicyComponent.name);
                continue;
            }

            if (policyComponent == null) {
                logger.warn("Policy Component not found -> ignoring! for FactionPolicy " + factionPolicyComponent.name);
                continue;
            }

            if (!policyClass.isAnnotationPresent(MappedContainer.class)) {
                logger.warn("Faction Policy is not MappedContainer -> ignoring! for Policy: "
                        + policyClass + " name=" + factionPolicyComponent.name);
                continue;
            }

            try {
                policyClass.getConstructor();
            } catch (NoSuchMethodException e) {
                logger.warn("Faction Policy cannot be constructed! -> ignoring! "
                        + policyClass + " name=" + factionPolicyComponent.name);
                continue;
            }

            policies.put(policyClass, factionPolicyComponent);
            policyComponents.put(policyClass, policyComponent);
            categoryComponents.computeIfAbsent(factionPolicyComponent.category, k -> new ArrayList<>());
            categoryComponents.get(factionPolicyComponent.category).add(factionPolicyComponent);

            logger.debug("Found Faction Policy: name=" + factionPolicyComponent.name);

            entityRef.destroy();
        }
    }

    public Policy constructPolicy(Class<? extends Policy> policyClass) {
        try {
            return policyClass.getConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public PolicyComponent getPolicyComponent(Class<? extends Policy> policy) {
            return policyComponents.get(policy);
    }

    public Set<Class<? extends Policy>> getPolicyClasses() {
        return policyComponents.keySet();
    }

    public Class<? extends PolicyComponent> getPolicyComponentClass(Class<? extends Policy> policy) {
        return policyComponents.get(policy).getClass();
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<FactionPolicyComponent> getFactionPolicyComponents(String category) {
        return categoryComponents.get(category);
    }

    public Class<? extends Policy> getPolicy(FactionPolicyComponent factionPolicyComponent) {
        return policies.inverse().get(factionPolicyComponent);
    }

}
