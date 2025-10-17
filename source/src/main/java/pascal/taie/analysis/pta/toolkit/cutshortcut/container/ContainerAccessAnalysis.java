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

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.analysis.pta.toolkit.cutshortcut.stream.Strm;
import pascal.taie.analysis.pta.toolkit.cutshortcut.stream.StreamAnalysis;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.TopType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

/**
 * maintain the host dependency graph
 * if a host H has sources S, all reachable hosts from H also get source S.
 * if a host H is marked as tainted, taint all reachable hosts from H.
 */
public class ContainerAccessAnalysis {

    Solver solver;
    CSManager csManager;
    ClassHierarchy hierarchy;
    HostManager hostManager;
    ContainerConfiger containerConfiger;
    ContainerAccessHandler containerAccessHandler;
    private StreamAnalysis streamAnalysis;
    private ContainerLambdaAnalysis containerLambdaAnalysis;

    /**
     * host dependency
     */
    MultiMap<CSVar, CSVar> smallToLargeContainer;
    MultiMap<CSVar, CSVar> largeToSmallContainer;

    /**
     * for host inner source handling
     */
    MultiMap<Type, Pair<CSVar, RetrieveKind>> hostInnerSources;

    /**
     * for map derived kinds
     */
    EnumSet<RetrieveKind> mapDerivedKinds;


    /**
     * soundness control
     */
    private final MultiMap<Host, Strm> host2Strm;

    private final MultiMap<Host, CSVar> host2Function;

    /**
     * statistics
     */
    final MultiMap<Host, CSVar> containerTargets;

    /**
     * thisHubHost
     */
    private static final Descriptor THIS_HOST = () -> "this_hub";

    private final Map<RetrieveKind, Host> thisHubHosts = Maps.newMap();

    ContainerAccessAnalysis(Solver solver,
                            CSManager csManager,
                            ClassHierarchy hierarchy,
                            HostManager hostManager,
                            ContainerConfiger containerConfiger,
                            ContainerAccessHandler containerAccessHandler) {
        this.smallToLargeContainer = Maps.newMultiMap();
        this.largeToSmallContainer = Maps.newMultiMap();
        this.hostInnerSources = Maps.newMultiMap();
        this.hierarchy = hierarchy;
        this.mapDerivedKinds = EnumSet.of(
                RetrieveKind.MAP_K, RetrieveKind.MAP_V, RetrieveKind.MAP_E);
        this.solver = solver;
        this.csManager = csManager;
        this.hostManager = hostManager;
        this.containerConfiger = containerConfiger;
        this.containerAccessHandler = containerAccessHandler;
        this.host2Strm = Maps.newMultiMap();
        this.host2Function = Maps.newMultiMap();
        this.containerTargets = Maps.newMultiMap();
        setUpThisHubHosts();
    }

    void setStreamAnalysis(StreamAnalysis analysis) {
        this.streamAnalysis = analysis;
    }

    void setContainerLambdaAnalysis(ContainerLambdaAnalysis analysis) {
        this.containerLambdaAnalysis = analysis;
    }

    void setUpThisHubHosts() {
        MockObj thisHubHostObj = new MockObj(THIS_HOST, THIS_HOST,
                NullType.NULL, null, false);
        EnumSet.of(RetrieveKind.COL_ITEM, RetrieveKind.MAP_ALL, RetrieveKind.MAP_K,
                RetrieveKind.MAP_V, RetrieveKind.MAP_E).forEach(k -> {
                    Host hub = hostManager.getHost(thisHubHostObj, k);
                    thisHubHosts.put(k, hub);
                    markTaint(hub);
        });
    }

    Collection<Host> getThisHubHosts() {
        return thisHubHosts.values();
    }

    /**
     * invoked when a new host is generated
     */
    void onNewHost(Host host) {
        // create source hub mock var and target hub mock var
        Var sourceHub = new Var(null, "[SourceHub]-{" + host + "}", TopType.Top, -1);
        Context emptyContext = solver.getContextSelector().getEmptyContext();
        CSVar csSourceHub = csManager.getCSVar(emptyContext, sourceHub);
        host.setSourceHub(csSourceHub);
        // add inner sources for this host
        hostInnerSources.get(host.getType()).forEach(pair -> {
            containerAccessHandler.addHostSrcHelper(host, pair.first(), pair.second());
        });
    }

    /**
     * when a new host is associated with a pointer, find and
     * connect all successor and predecessor for this host
     */
    void onNewHostOfVarSmallToLarge(CSVar small, Host host) {
        //find all large host for this host
        this.smallToLargeContainer.get(small).forEach(largeContainerVar -> {
            for (CSObj csObj : solver.getPointsToSetOf(largeContainerVar)) {
                if (HostManager.isHost(csObj.getObject())) {
                    Host largeHost = (Host) csObj.getObject().getAllocation();
                    addHostDependency(host, largeHost);
                }
            }
        });
    }
    void onNewHostOfVarLargeToSmall(CSVar large, Host host) {
        //find all small host for this host
        this.largeToSmallContainer.get(large).forEach(smallContainerVar -> {
            for (CSObj csObj : solver.getPointsToSetOf(smallContainerVar)) {
                if (HostManager.isHost(csObj.getObject())) {
                    Host smallHost = (Host) csObj.getObject().getAllocation();
                    addHostDependency(smallHost, host);
                }
            }
        });
    }

    /**
     * when a new source is added to a host H, propagate this source
     * to all reachable hosts (from H) on the graph.
     */
    void onNewHostSource(Host host, CSVar source) {
        solver.addPFGEdge(new ShortcutEdge(source, host.getSourceHub()));
    }

    /**
     * when a new target is found for a host, add PFG shortcut
     * edge from all sources of this host to this target.
     */
    void onNewHostTarget(Host host, CSVar target) {
        containerTargets.put(host, target);
        solver.addPFGEdge(new ShortcutEdge(host.getSourceHub(), target), target.getType());
    }

    /**
     * handling for invocation of form large.batchEntrance(small)
     * add host dependency for hosts associated with large and small,
     * record relation (small <-> large) for future arriving hosts
     */
    void addContainerVarDependency(CSVar small, CSVar large) {
        this.smallToLargeContainer.put(small, large);
        this.largeToSmallContainer.put(large, small);
        for (CSObj sObj : solver.getPointsToSetOf(small)) {
            Obj sobj = sObj.getObject();
            if (HostManager.isHost(sobj)) {
                Host smallHost = (Host) sobj.getAllocation();
                for (CSObj lObj : solver.getPointsToSetOf(large)) {
                    if (HostManager.isHost(lObj.getObject())) {
                        Host largeHost = (Host) lObj.getObject().getAllocation();
                        addHostDependency(smallHost, largeHost);
                    }
                }
            }
        }
    }

    /**
     * connect two hosts on dependency graph
     * if is MAP_ALL placeholder, connect three pair of actual hosts.
     */
    void addHostDependency(Host small, Host large) {
        if (large.isTainted()) {
            return;
        }
        RetrieveKind smallKind = small.getKind();
        RetrieveKind largeKind = large.getKind();
        if (isLegalDependency(largeKind, smallKind)) {
            if (smallKind == RetrieveKind.MAP_ALL && largeKind == RetrieveKind.MAP_ALL) {
                mapDerivedKinds.forEach(derivedKind -> {
                    Host smallDerivedHost = hostManager.getMapKVEHost(small, derivedKind);
                    Host largeDerivedHost = hostManager.getMapKVEHost(large, derivedKind);
                    smallDerivedHost.addSuccessor(largeDerivedHost);
                    largeDerivedHost.addPredecessor(smallDerivedHost);
                    onNewSuccessor(smallDerivedHost, largeDerivedHost);
                });
            } else if (smallKind == RetrieveKind.MAP_ALL) {
                Host smallDerivedHost = hostManager.getMapKVEHost(small, largeKind);
                smallDerivedHost.addSuccessor(large);
                large.addPredecessor(smallDerivedHost);
                onNewSuccessor(smallDerivedHost, large);
            }
            else {
                small.addSuccessor(large);
                large.addPredecessor(small);
                onNewSuccessor(small, large);
            }
        }
    }

    /**
     * invoked when a new successor is found for a host
     * propagate all sources of small host to large one.
     * if the small host is tainted, tainted the large one.
     */
    void onNewSuccessor(Host small, Host large) {
        if (!large.isTainted()) {
            CSVar smallSourceHub = small.getSourceHub();
            CSVar largeSourceHub = large.getSourceHub();
            large.addSource(smallSourceHub);
            if (small.isTainted()) {
                propagateTaint(large);
            } else {
                solver.addPFGEdge(new ShortcutEdge(smallSourceHub, largeSourceHub));
            }
        }
    }

    /**
     * taint a host, if host is MAP_ALL placeholder, taint
     * MAP_K/MAP_V/MAP_E actual hosts.
     * propagate taint to all reachable hosts on dependency graph
     */
    public void markTaint(Host host) {
        if (host.getKind() == RetrieveKind.MAP_ALL) {
            mapDerivedKinds.forEach(derivedKind -> {
                Host derivedHost = hostManager.getMapKVEHost(host, derivedKind);
                propagateTaint(derivedHost);
            });
        } else {
            propagateTaint(host);
        }
    }

    private void propagateTaint(Host host) {
        if (!host.isTainted()) {
            host.setTaint();
            //re-add return edge for this host's exit call-site
            containerAccessHandler.undoHostRelatedExits(host);
            //trigger related pipeline Strm's unsound flag.
            if (streamAnalysis != null) {
                host2Strm.get(host).forEach(streamAnalysis::markUnsound);
            }
            if (containerLambdaAnalysis != null) {
                host2Function.get(host).forEach(containerLambdaAnalysis::triggerFunctionUnsound);
            }
            //propagate taint through out-edges
            host.getSuccessors().forEach(succ -> {
                if (!succ.isTainted()) {
                    propagateTaint(succ);
                }
            });
        }
    }

    /**
     * Decide if it is legal to add dependency for two host kinds
     */
    private static boolean isLegalDependency(RetrieveKind largeKind, RetrieveKind smallKind) {
        if (largeKind == smallKind) {
            return true;
        } else if ((smallKind == RetrieveKind.COL_ITEM && largeKind != RetrieveKind.MAP_ALL)
                || (largeKind == RetrieveKind.COL_ITEM && smallKind != RetrieveKind.MAP_ALL)) {
            return true;
        } else if (smallKind == RetrieveKind.MAP_ALL && largeKind != RetrieveKind.COL_ITEM) {
            return true;
        }
        return false;
    }

    void recordHostInnerSource(Type hostType, CSVar source, RetrieveKind kind) {
        hostInnerSources.put(hostType, new Pair<>(source, kind));
    }

    public void addHost2Strm(Host h, Strm l) {
        this.host2Strm.put(h, l);
    }

    public void addHost2Function(Host h, CSVar function) {
        this.host2Function.put(h, function);
    }
}
