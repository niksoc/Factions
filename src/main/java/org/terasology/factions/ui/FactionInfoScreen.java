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
package org.terasology.factions.ui;

import com.google.common.collect.Lists;
import org.terasology.factions.FactionSystem;
import org.terasology.factions.components.FactionComponent;
import org.terasology.factions.policies.FactionPolicySystem;
import org.terasology.factions.policies.FieldDescriptor;
import org.terasology.factions.policies.PolicyType;
import org.terasology.factions.policies.policies.InternalPolicy;
import org.terasology.factions.policies.policies.OneWayPolicy;
import org.terasology.factions.policies.policies.Policy;
import org.terasology.factions.policies.policies.TwoWayPolicy;
import org.terasology.registry.In;
import org.terasology.rendering.nui.CoreScreenLayer;
import org.terasology.rendering.nui.databinding.Binding;
import org.terasology.rendering.nui.databinding.ReadOnlyBinding;
import org.terasology.rendering.nui.itemRendering.ToStringTextRenderer;
import org.terasology.rendering.nui.widgets.UIDropdown;
import org.terasology.rendering.nui.widgets.UILabel;
import org.terasology.rendering.nui.widgets.UIList;

import java.util.ArrayList;
import java.util.List;

public class FactionInfoScreen extends CoreScreenLayer {

    public static final String POLICY_LIST_ITEM_OPEN = "--";
    public static final String POLICY_LIST_ITEM_CLOSE = "++";

    private UIList<FieldDescriptor> policyFields;
    private UIDropdown<FactionComponent> selectFaction1;
    private UIDropdown<FactionComponent> selectFaction2;
    private UIList<FactionPolicyComponent> policyList;
    private UILabel selectedPolicy;
    private FactionComponent selectedFaction1;
    private FactionComponent selectedFaction2;
    private FactionPolicyComponent selectedFactionPolicy;
    private Class<? extends Policy> policyClass;
    private PolicyType selectedPolicyType;
    private Policy policy;
    private List<FactionPolicyComponent> policyListItems = new ArrayList<>();

    @In
    private FactionPolicySystem factionPolicySystem;
    @In
    private FactionSystem factionSystem;

    @Override
    public void initialise() {
        selectFaction1 = find("select_faction1", UIDropdown.class);
        selectFaction2 = find("select_faction2", UIDropdown.class);
        policyList = find("policyList", UIList.class);
        policyFields = find("policyFields", UIList.class);
        selectedPolicy = find("selectedPolicy", UILabel.class);

        selectedPolicy.bindText(new Binding<String>() {
            @Override
            public String get() {
                if(selectedFactionPolicy == null) {
                    return "Select a policy from the left pane";
                }
                return selectedFactionPolicy.category + " / " + selectedFactionPolicy.name;
            }

            @Override
            public void set(String value) {
            }
        });

        Binding<List<FactionComponent>> factionBinding = new ReadOnlyBinding<List<FactionComponent>>() {
            @Override
            public List<FactionComponent> get() {
                return new ArrayList<>(factionSystem.getFactions());
            }
        };
        selectFaction1.bindOptions(factionBinding);

        selectFaction1.bindSelection(new Binding<FactionComponent>() {
            @Override
            public FactionComponent get() {
                return selectedFaction1;
            }

            @Override
            public void set(FactionComponent value) {
                selectedFaction1 = value;
                refreshPolicy();
            }
        });

        selectFaction2.bindOptions(factionBinding);

        selectFaction2.bindEnabled(new Binding<Boolean>() {
            @Override
            public Boolean get() {
                return selectedPolicyType != null
                        && selectedPolicyType != PolicyType.INTERNAL;
            }

            @Override
            public void set(Boolean value) {
            }
        });

        selectFaction2.bindSelection(new Binding<FactionComponent>() {
            @Override
            public FactionComponent get() {
                return selectedFaction2;
            }

            @Override
            public void set(FactionComponent value) {
                selectedFaction2 = value;
                refreshPolicy();
            }
        });

        policyList.bindSelection(new Binding<FactionPolicyComponent>() {
            @Override
            public FactionPolicyComponent get() {
                return null;
            }

            @Override
            public void set(FactionPolicyComponent value) {
                switch (value.name.substring(0, 2)) {
                    case POLICY_LIST_ITEM_OPEN:
                        int pos = policyListItems.indexOf(value) + 1;
                        while (shouldCollapse(pos)) {
                            policyListItems.remove(pos);
                        }
                        policyListItems.remove(pos - 1);
                        policyListItems.add(pos - 1, createCategory(value.category, false));
                        break;
                    case POLICY_LIST_ITEM_CLOSE:
                        pos = policyListItems.indexOf(value);
                        policyListItems.remove(pos);
                        FactionPolicyComponent categoryItem = createCategory(value.category, true);
                        policyListItems.add(pos, categoryItem);
                        policyListItems.addAll(pos + 1, factionPolicySystem.getFactionPolicyComponents(value.category));
                        break;
                    default:
                        selectFactionPolicy(value);
                        break;
                }
            }

            private boolean shouldCollapse(int pos) {
                if (pos < policyListItems.size()) {
                    String currentItemName = policyListItems.get(pos).name;
                    return !currentItemName.startsWith(POLICY_LIST_ITEM_OPEN)
                            && !currentItemName.startsWith(POLICY_LIST_ITEM_CLOSE);
                }
                return false;
            }
        });
        policyList.bindList(new ReadOnlyBinding<List<FactionPolicyComponent>>() {
            @Override
            public List<FactionPolicyComponent> get() {
                return policyListItems;
            }
        });
        policyList.setItemRenderer(new ToStringTextRenderer<FactionPolicyComponent>() {
            @Override
            public String getTooltip(FactionPolicyComponent value) {
                return value.description;
            }
        });

        policyListItems = findPolicyListItems();

        policyFields.setSelectable(false);
        policyFields.setCanBeFocus(false);

        policyFields.bindList(new ReadOnlyBinding<List<FieldDescriptor>>() {
            @Override
            public List<FieldDescriptor> get() {
                if(policy == null) {
                    return new ArrayList<>();
                }
                return policy.getFieldDescriptions();
            }
        });

        policyFields.setItemRenderer(new ToStringTextRenderer<FieldDescriptor>() {
            @Override
            public String getTooltip(FieldDescriptor value) {
                return value.description;
            }
        });

    }

    private void refreshPolicy() {
        if (policyClass == null) {
            return;
        }

        policy = null;
        switch (selectedPolicyType) {
            case INTERNAL:
                if (selectedFaction1 != null) {
                    policy = factionSystem.getInternalPolicy(
                            (Class<? extends InternalPolicy>) policyClass,
                            selectedFaction1.name);
                }
                break;
            case ONE_WAY:
                if (selectedFaction1 != null && selectedFaction2 != null
                        && !selectedFaction1.name.equals(selectedFaction2)) {
                    policy = factionSystem.getOneWayPolicy(
                            (Class<? extends OneWayPolicy>) policyClass,
                            selectedFaction1.name, selectedFaction2.name);
                }
                break;
            case TWO_WAY:
                if (selectedFaction1 != null && selectedFaction2 != null
                        && !selectedFaction1.name.equals(selectedFaction2)) {
                    policy = factionSystem.getTwoWayPolicy(
                            (Class<? extends TwoWayPolicy>) policyClass,
                            selectedFaction1.name, selectedFaction2.name);
                }
                break;
        }
    }

    private void selectFactionPolicy(FactionPolicyComponent factionPolicyComponent) {
        selectedFactionPolicy = factionPolicyComponent;
        policyClass = factionPolicySystem.getPolicy(factionPolicyComponent);
        selectedPolicyType = PolicyType.getPolicyType(policyClass);
        refreshPolicy();
    }

    @Override
    public boolean isLowerLayerVisible() {
        return false;
    }

    private List<FactionPolicyComponent> findPolicyListItems() {
        List<FactionPolicyComponent> items = Lists.newArrayList();
        for (String category : factionPolicySystem.getCategories()) {
            FactionPolicyComponent categoryItem = createCategory(category, true);
            items.add(categoryItem);
            items.addAll(factionPolicySystem.getFactionPolicyComponents(category));
        }
        return items;
    }

    private FactionPolicyComponent createCategory(String category, boolean open) {
        String prefix = open ? POLICY_LIST_ITEM_OPEN : POLICY_LIST_ITEM_CLOSE;
        FactionPolicyComponent categoryItem = new FactionPolicyComponent();
        categoryItem.category = category;
        categoryItem.name = prefix + category.toUpperCase() + prefix;
        return categoryItem;
    }

}
