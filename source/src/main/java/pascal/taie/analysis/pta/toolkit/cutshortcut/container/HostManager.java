/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.toolkit.cutshortcut.container;

import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.language.type.SomeType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.collection.TwoKeyMap;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * manage all hosts instance and transformation between host kinds.
 * A host of kind MAP_ALL is a placeholder (we do not add source and target,
 * and do not add dependency for it). one MAP_ALL host is associated with three hosts
 * (with kind MAP_E, MAP_K, MAP_V), which can be accessed given MAP_ALL host.
 * see #isCompatible #addSourcfe and #addTarget
 */
public class HostManager {

    private final SomeType hostType;

    private ContainerAccessAnalysis analysis;

    /**
     * each container obj may have at most 5 host of different kinds.
     */
    private final TwoKeyMap<Obj, RetrieveKind, Host> hosts = Maps.newTwoKeyMap();

    static final Descriptor HOST_OBJ_DESC = () -> "Host";

    HostManager(Set<Type> types) {
        this.hostType = new SomeType(types);
    }

    Host getHost(Obj obj, RetrieveKind kind) {
        return hosts.computeIfAbsent(obj, kind, (o, k) -> {
            Host h = new Host(o, k);
            MockObj m = new MockObj(HOST_OBJ_DESC, h, hostType, null, false);
            h.setMockObj(m);
            if (analysis != null) {
                analysis.onNewHost(h);
            }
            return h;
        });
    }

    /**
     * access MAP associated hosts by MAP_ALL host
     * @param host the MAP_ALL host
     * @param kveKind MAP_K/MAP_V/MAP_E
     * @return host of the same obj, with kind kveKind
     */
    Host getMapKVEHost(Host host, RetrieveKind kveKind) {
        assert host.getKind() == RetrieveKind.MAP_ALL;
        assert kveKind == RetrieveKind.MAP_K
                || kveKind == RetrieveKind.MAP_V
                || kveKind == RetrieveKind.MAP_E;
        return getHost(host.getObj(), kveKind);
    }

    /**
     * attempt to add a source pointer for a host (could be MAP_ALL placeholder)
     * @param host container host
     * @param source the source pointer
     * @param entranceKind kind the entrance configured
     * @return actual host the source should be added into.
     * null if host kind and entrance kind is not compatible.
     */
    Host addHostSource(Host host, CSVar source, RetrieveKind entranceKind) {
        if (!host.isTainted()) { // no need to add source for a tainted host
            assert entranceKind != RetrieveKind.MAP_ALL;
            RetrieveKind hostKind = host.getKind();
            if (isCompatible(hostKind, entranceKind)) {
                if (hostKind == RetrieveKind.MAP_ALL) {
                    Host mapKVEHost = getMapKVEHost(host, entranceKind);
                    if (mapKVEHost.addSource(source)) {
                        return mapKVEHost;
                    }
                } else {
                    // if (two are equal) or
                    // (one is COLLECTION_ITEM and the other is MAP_K/MAP_V/MAP_ENTRY)
                    if (host.addSource(source)) {
                        return host;
                    }
                }
            }
        }
        return null;
    }

    /**
     * attempt to add a target pointer for a host (could be MAP_ALL placeholder)
     * @param host container host
     * @param target the target pointer
     * @param exitKind kind the exit configured
     * @return actual host the target should be added into.
     * null if host kind and exit kind is not compatible.
     */
    Host addHostTarget(Host host, CSVar target, RetrieveKind exitKind) {
        assert exitKind != RetrieveKind.MAP_ALL;
        RetrieveKind hostKind = host.getKind();
        if (isCompatible(hostKind, exitKind)) {
            if (hostKind == RetrieveKind.MAP_ALL) {
                Host mapKVEHost = getMapKVEHost(host, exitKind);
                if (mapKVEHost.addTarget(target)) {
                    return mapKVEHost;
                }
            } else {
                // if (two are equal) or
                // (one is COLLECTION_ITEM and the other is MAP_K/MAP_V/MAP_ENTRY)
                if (host.addTarget(target)) {
                    return host;
                }
            }
        }
        return null;
    }

    RetrieveKind getTransferredKind(Host host, RetrieveKind fromKind, RetrieveKind toKind) {
        RetrieveKind hostKind = host.getKind();
        if (isCompatible(hostKind, fromKind)) {
            if (hostKind == fromKind) {
                return toKind;
            } else {
                return hostKind;
            }
        }
        return null;
    }

    /** Decide if we should process a host and a configured method
     * 1) hostKind = MAP_ALL, then methodKind can be MAP_ALL, MAP_V, MAP_K, MAP_ENTRY
     * 2) hostKind = MAP_K, then methodKind can be MAP_K, COL_ITEM
     * 3) hostKind = MAP_V, then methodKind can be MAP_V, COL_ITEM
     * 4) hostKind = MAP_ENTRY, then methodKind can be MAP_ENTRY, COL_ITEM
     * 5) hostKind = COL_ITEM, then methodKind can be COL_ITEM, MAP_K, MAP_V, MAP_ENTRY
     * NO others
     * @param hostKind host RetrieveKind
     * @param methodKind method RetrieveKind
     */
    private boolean isCompatible(RetrieveKind hostKind, RetrieveKind methodKind) {
        switch (hostKind) {
            case COL_ITEM -> {
                return methodKind != RetrieveKind.MAP_ALL;
            }
            case MAP_ALL -> {
                return methodKind != RetrieveKind.COL_ITEM;
            }
            case MAP_K -> {
                return methodKind == RetrieveKind.MAP_K || methodKind == RetrieveKind.COL_ITEM;
            }
            case MAP_V -> {
                return methodKind == RetrieveKind.MAP_V || methodKind == RetrieveKind.COL_ITEM;
            }
            case MAP_E -> {
                return methodKind == RetrieveKind.MAP_E || methodKind == RetrieveKind.COL_ITEM;
            }
        }
        return false;
    }

    boolean containsObjHost(Obj obj) {
        return hosts.containsKey(obj);
    }

    /**
     * @param type host obj type
     * @return if obj is of collection, return set containing host of kind COLLECTION_ITEM
     *         if obj is of map, return set containing host of kind MAP_ALL
     */
    Set<Host> getDominatingHostByObjType(Type type) {
        Set<Host> result = Sets.newHybridSet();
        hosts.keySet().forEach(obj -> {
            if (obj.getType().equals(type)) {
                result.addAll(Objects.requireNonNull(hosts.get(obj)).values());
            }
        });
        return result;
    }

    void setAnalysis(ContainerAccessAnalysis analysis) {
        this.analysis = analysis;
    }

    public static boolean isHost(Obj obj) {
        return (obj instanceof MockObj m) && (m.getDescriptor().equals(HOST_OBJ_DESC));
    }

    Collection<Host> getHosts() {
        return this.hosts.values();
    }

}
