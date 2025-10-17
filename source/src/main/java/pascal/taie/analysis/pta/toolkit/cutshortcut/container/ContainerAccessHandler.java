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

import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.core.solver.Transfer;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ArrayReplicationAnalysis;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.analysis.pta.toolkit.cutshortcut.stream.Strm;
import pascal.taie.analysis.pta.toolkit.cutshortcut.stream.StreamAnalysis;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.ClassMember;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.SomeType;
import pascal.taie.language.type.TopType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.collection.MultiMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * handle container access pattern for cut-shortcut.
 */
public class ContainerAccessHandler implements Plugin {

    /**
     * utils
     */
    private final Solver solver;
    private final CSManager csManager;
    private final ContainerConfiger containerConfiger;
    private final HeapModel heapModel;
    final ClassHierarchy hierarchy;
    private final HostManager hostManager;
    private final CSCHelper helper;
    private final ContainerAccessAnalysis containerAccessAnalysis;
    private final ContainerLambdaAnalysis containerLambdaAnalysis;
    private final ArrayReplicationAnalysis arrayReplicate;
    StreamAnalysis streamAnalysis = null;
    private Set<JClass> streamScope;
    private Set<JMethod> streamExits = Sets.newSet();

    /**
     * core data structure for cut and undo cut edges
     */
    private final Set<Var> cutReturns = Sets.newHybridSet();
    private final MultiMap<Var, Var> undoCuts = Maps.newMultiMap();
    private final MultiMap<Host, CSCallSite> hostRelatedExitCallSites = Maps.newMultiMap();
    private final Set<CSCallSite> undoCutCallSites = Sets.newHybridSet();
    private final MultiMap<CSCallSite, CSMethod> undoCallEdge = Maps.newMultiMap();

    /**
     * records supporting onNewPointsToSet(...) handling.
     */
    record EntranceCall(CSVar source, RetrieveKind kind) {
    }
    record ExitCall(CSVar result, RetrieveKind kind, CSCallSite callSite) {
    }
    record TransferCall(CSVar to, RetrieveKind fromKind, RetrieveKind toKind) {
    }
    record InnerSourceCall(CSVar source, RetrieveKind kind) {
    }
    record ArrayReplicationInfo(boolean replicable, JMethod inmethod, Context context, CSVar arraySource) {
    }

    /**
     * for later arrived container objs
     */
    private final MultiMap<CSVar, ExitCall> cont2exit = Maps.newMultiMap();
    private final MultiMap<CSVar, EntranceCall> cont2entrance = Maps.newMultiMap();
    private final MultiMap<CSVar, TransferCall> cont2transfer = Maps.newMultiMap();
    private final MultiMap<CSVar, InnerSourceCall> cont2inners = Maps.newMultiMap();
    // cont -> arraySourceMockVar
    private final MultiMap<CSVar, CSVar> contArraySources = Maps.newMultiMap();
    // array -> arraySourceMockVar
    private final Map<CSVar, ArrayReplicationInfo> arrayMockSources = Maps.newHybridMap();
    // smaller -> collection for collection.batchEntrance(smaller)
    private final MultiMap<CSVar, CSVar> colBatchEntrance = Maps.newMultiMap();
    // soundness control
    final Set<CSVar> unsoundBases = Sets.newSet();
    final Set<CSVar> contSPConstructorLHS = Sets.newSet();
    private final Set<JMethod> thisHubHostMethods = Sets.newSet();

    /**
     * for later arrived abstract stream pipeline
     */
    final MultiMap<CSVar, Host> stmToHost = Maps.newMultiMap();
    final Set<CSVar> lhs2StmAffectedCont = Sets.newSet();

    /**
     * mock host attributes
     */
    private static final Descriptor UTIL_PRIVATE_CONTAINER = () -> "util_private_container";
    static final Descriptor PIPELINE_OUT = () -> "Pipeline_output";

    /**
     * for statistics
     */
    private final Set<Type> customContType = Sets.newSet();
    private final boolean INVOLVED;
    private final Set<JMethod> involvedMethods = Sets.newSet();

    public ContainerAccessHandler(Solver solver, boolean preciseLambda,
                                  CSCHelper helper, LambdaAnalysis lambdaAnalysis,
                                  ArrayReplicationAnalysis arrayReplicate,
                                  boolean involved) {
        this.solver = solver;
        this.helper = helper;
        this.csManager = solver.getCSManager();
        this.heapModel = solver.getHeapModel();
        this.hierarchy = solver.getHierarchy();
        this.arrayReplicate = arrayReplicate;
        INVOLVED = involved;
        this.containerConfiger = new ContainerConfiger(
                solver.getHierarchy(), solver.getTypeSystem(),
                World.get().getOptions().getJavaVersion());
        this.hostManager = new HostManager(
                containerConfiger.getHostRelatedPointerType());
        this.containerAccessAnalysis = new ContainerAccessAnalysis(
                solver, csManager, hierarchy, hostManager,
                containerConfiger, this);
        this.containerLambdaAnalysis = new ContainerLambdaAnalysis(
                preciseLambda, solver,
                csManager, heapModel,
                solver.getTypeSystem(),
                hostManager, lambdaAnalysis,
                this, containerConfiger,
                containerAccessAnalysis, helper);
        this.hostManager.setAnalysis(containerAccessAnalysis);
        this.containerAccessAnalysis
                .setContainerLambdaAnalysis(containerLambdaAnalysis);
    }

    /**
     * identify curReturns
     * @param method new reachable method
     */
    @Override
    public void onNewMethod(JMethod method) {
        // for each invocation to a configured exit
        // the return edge should be cut.
        if (containerConfiger.isExit(method)) {
            cutReturns.addAll(method.getIR().getReturnVars());
        }
        containerLambdaAnalysis.onNewMethod(method);
    }

    /**
     * for each New stmt, add a new HostObj into LHS variable's pts.
     * @param csMethod new reachable context-sensitive method
     */
    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        method.getIR().forEach(stmt -> {
            if (stmt instanceof New newStmt) {
                Obj obj = heapModel.getObj(newStmt);
                Var lhs = newStmt.getLValue();
                // generate host
                generateHost(csMethod, lhs, obj);
                //handle map entry allocation site
                if (containerConfiger.isMapEntryObj(obj)
                        && containerConfiger.isConfiguredRelatedObj(obj)) {
                    processMapEntryAllocation(csMethod, lhs);
                }
            }
        });
    }

    /**
     * undo certain call sites
     * @param edge new call graph edge
     */
    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        JMethod callee = edge.getCallee().getMethod();
        CSCallSite csCallSite = edge.getCallSite();
        Invoke invoke = csCallSite.getCallSite();
        JClass calleeDeclaring = callee.getDeclaringClass();
        // entrance
        if (containerConfiger.isEntrance(callee)
                && !containerConfiger.isReentrancy(invoke)) {
            processEntrance(edge);
        }
        // exit
        if (containerConfiger.isExit(callee)) {
            processExit(edge);
        }
        // transfer
        if (containerConfiger.isTransfer(callee)) {
            processTransfer(edge);
        }
        // batch entrance
        if (containerConfiger.isBatchEntrance(callee)) {
            processBatchEntrance(edge);
        }
        // array entrance
        if (containerConfiger.isArrayEntrance(callee)) {
            processArrayEntrance(edge);
        }
        // batch array entrance
        if (containerConfiger.isBatchArrayEn(callee)) {
            processBatchArrayEn(edge);
        }
        // alloc for util class private container
        // e.g., set = Collections.checkedSet(c);
        if (containerConfiger.isUtilPrivateContAlloc(callee)) {
            processUtilPrivateContAlloc(edge);
        }
        // inner source
        if (containerConfiger.isInnerSourceInMethod(callee)) {
            processInnerSource(edge);
        }
        // unsound operations
        if (containerConfiger.isUnsoundOp(callee)) {
            processUnsoundOp(edge);
        }
        // support streamAPI
        // e.g., itr = stm.iterator()
        if (containerConfiger.isStmSimple2Cont(callee)) {
            processSimpleStm2Cont(edge);
        }
        // other call-edge to container <init>
        if (edge.getKind() == CallKind.OTHER
                && callee.isConstructor()) {
            processSpecialHostGeneration(edge);
        }
        // add thisHost to related instance
        if (containerConfiger.isRelatedClass(calleeDeclaring)) {
            processThisHost(edge);
        }
        containerLambdaAnalysis.onNewCallEdge(edge);
    }

    /**
     * if new hostObj added into a base var
     * @param csVar variable whose points-to set changes
     * @param pts   set of new objects
     */
    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        Context callerContext = csVar.getContext();
        // handling new host
        if (containerAccessAnalysis.smallToLargeContainer.containsKey(csVar)
                || containerAccessAnalysis.largeToSmallContainer.containsKey(csVar)
                || cont2entrance.containsKey(csVar)
                || cont2exit.containsKey(csVar)
                || cont2transfer.containsKey(csVar)
                || contArraySources.containsKey(csVar)
                || colBatchEntrance.containsKey(csVar)
                || unsoundBases.contains(csVar)
                || cont2inners.containsKey(csVar)
                || lhs2StmAffectedCont.contains(csVar)) {
            pts.forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    // small to large
                    if (containerAccessAnalysis.smallToLargeContainer.containsKey(csVar)) {
                        containerAccessAnalysis.onNewHostOfVarSmallToLarge(csVar, host);
                    }
                    // large to small
                    if (containerAccessAnalysis.largeToSmallContainer.containsKey(csVar)) {
                        containerAccessAnalysis.onNewHostOfVarLargeToSmall(csVar, host);
                    }
                    // entrance
                    if (cont2entrance.containsKey(csVar)) {
                        cont2entrance.get(csVar)
                                .forEach(entranceCall -> addHostSrcHelper(
                                        host, entranceCall.source, entranceCall.kind));
                    }
                    // exit
                    if (cont2exit.containsKey(csVar)) {
                        cont2exit.get(csVar)
                                .forEach(exitCall -> processExitHelper(host, exitCall));
                    }
                    // transfer
                    if (cont2transfer.containsKey(csVar)) {
                        cont2transfer.get(csVar)
                                .forEach(transferCall -> processTransferHelper(host, transferCall));
                    }
                    // array entrance
                    if (contArraySources.containsKey(csVar)) {
                        contArraySources.get(csVar)
                                .forEach(source -> addHostArraySource(host, source));
                    }
                    // transfer MAP_ALL host at collection batch entrance call-site
                    // e.g., set.addAll(entrySet); where entrySet has MAP-ALL
                    if (colBatchEntrance.containsKey(csVar)
                            && host.getKind() == RetrieveKind.MAP_ALL) {
                        colBatchEntrance.get(csVar).forEach(c -> {
                            solver.addVarPointsTo(callerContext, c.getVar(), host.getMockObj());
                        });
                    }
                    // unsound base
                    if (unsoundBases.contains(csVar)) {
                        containerAccessAnalysis.markTaint(host);
                    }
                    // inner-source in method
                    if (cont2inners.containsKey(csVar)) {
                        cont2inners.get(csVar).forEach(innerSourceCall ->
                                addHostSrcHelper(host, innerSourceCall.source, innerSourceCall.kind));
                    }
                }
            });
        }
        // handling other new objects
        if (arrayMockSources.containsKey(csVar)
                || contSPConstructorLHS.contains(csVar)
                || stmToHost.containsKey(csVar)) {
            pts.forEach(csObj -> {
                // for new array obj of recorded array var
                // link arrayIndex to mock source
                if (arrayMockSources.containsKey(csVar)) {
                    ArrayReplicationInfo info = arrayMockSources.get(csVar);
                    ArrayIndex arrayIndex = csManager.getArrayIndex(csObj);
                    if (info.replicable && arrayReplicate != null) {
                        ArrayIndex replicated = arrayReplicate.replicateArrayIndex(
                                arrayIndex, info.inmethod, info.context);
                        solver.addPFGEdge(new ShortcutEdge(
                                replicated, info.arraySource));
                    } else {
                        solver.addPFGEdge(new ShortcutEdge(arrayIndex, info.arraySource));
                    }
                }
                // for contSpConstructor
                if (contSPConstructorLHS.contains(csVar)) {
                    CSMethod inMethod = csManager.getCSMethod(
                            csVar.getContext(), csVar.getVar().getMethod());
                    generateHost(inMethod, csVar.getVar(), csObj.getObject());
                }
                // new abstract stream pipeline
                if (stmToHost.containsKey(csVar)) {
                    if (helper.isStrm(csObj.getObject())) {
                        Strm l = (Strm) csObj.getObject().getAllocation();
                        stmToHost.get(csVar).forEach(host -> {
                            addHostSrcHelper(host, l.getSourceHub(), RetrieveKind.COL_ITEM);
                            streamAnalysis.addStrm2Host(l, host);
                            // if l is unsound, mark host as taint.
                            if (l.isConserv()) {
                                containerAccessAnalysis.markTaint(host);
                            }
                        });
                    }
                }
            });
        }
        // let containerLambdaAnalysis handle the rest.
        if (containerLambdaAnalysis.lambdaPrecise) {
            containerLambdaAnalysis.onNewPointsToSet(csVar, pts);
        }
    }

    /**
     * return edge whose source is in cutReturns should not be added.
     * @param edge the pointer flow edge
     * @return if the edge should be added into PFG
     */
    @Override
    public boolean shouldAdd(PointerFlowEdge edge) {
        boolean isCutEdge =
                edge.kind() == FlowKind.RETURN
                && edge.source() instanceof CSVar csVar
                && cutReturns.contains(csVar.getVar())
                && edge.target() instanceof CSVar;
        boolean isUndoCutEdge =
                edge.kind() == FlowKind.RETURN
                && edge.source() instanceof CSVar csRet
                && edge.target() instanceof CSVar csLHS
                && undoCuts.get(csRet.getVar()).contains(csLHS.getVar());
        return  (!isCutEdge) || (isUndoCutEdge);
    }

    @Override
    public void onNewArrayStoreEdge(CSVar from, ArrayIndex arrayIndex) {
        if (arrayReplicate != null) {
            // judge if the store happens out of scope
            boolean reflecting = true;
            JMethod inMethod = from.getVar().getMethod();
            if (inMethod != null
                    && inMethod.getDeclaringClass() != null
                    && isArrayReplicationInScope(inMethod.getDeclaringClass())) {
                reflecting = false;
            }
            // if true, the store should also be seen to
            // replicated arrayObj of arrayObj on arrayIndex
            if (reflecting) {
                arrayReplicate.reflectStoring(arrayIndex, from);
            }
        }
    }

    /**
     * @return the transfer function that do not let host
     * pass through a return edge if callee is a transfer.
     */
    @Override
    public Transfer getRetEdgeTransfer() {
        return ((edge, input) -> {
            if (edge.kind() == FlowKind.RETURN
                    && edge.source() instanceof CSVar ret
                    && containerConfiger.isRetEdgeTransferCallee(
                            ret.getVar().getMethod())) {
                PointsToSet result = solver.makePointsToSet();
                for (CSObj csObj : input) {
                    if (!(HostManager.isHost(csObj.getObject()))) {
                        result.addObject(csObj);
                    }
                }
                return result;
            } else {
                return input;
            }
        });
    }

    @Override
    public Transfer getPassEdgeTransfer() {
        return containerLambdaAnalysis.getPassEdgeTransfer();
    }


    /**
     * undo cut edge from a callee's return var to callsite.
     */
    private void undoCutEdge(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite csCallSite = edge.getCallSite();
        CSMethod csCallee = edge.getCallee();
        if (undoCallEdge.put(csCallSite, csCallee)) {
            JMethod callee = csCallee.getMethod();
            Set<CSVar> csLHSs = getLHSS(edge);
            for (CSVar csLHS : csLHSs) {
                for (Var ret : callee.getIR().getReturnVars()) {
                    CSVar csRet = csManager.getCSVar(csCallee.getContext(), ret);
                    PointerFlowEdge retEdge = new PointerFlowEdge(FlowKind.RETURN, csRet, csLHS);
                    this.undoCuts.put(ret, csLHS.getVar());
                    solver.addPFGEdge(retEdge, solver.getRetEdgeTransfer());
                }
            }
        }
    }

    /**
     * undo cut return edges for all call-sites with base containing a certain host
     */
    void undoHostRelatedExits(Host host) {
        hostRelatedExitCallSites.get(host).forEach(this::undoCallSite);
    }

    /**
     * undo cut return edges for a given call-site.
     */
    private void undoCallSite(CSCallSite csCallSite) {
        undoCutCallSites.add(csCallSite);
        solver.getCallGraph().edgesOutOf(csCallSite).forEach(this::undoCutEdge);
    }

    /**
     * for each heap allocation, decide if we should
     * generate new host (and add a new Host Obj into pts of var)
     * @param inMethod the container method
     * @param var the var whose pts hold the heap obj
     * @param obj the newly allocated heap obj
     */
    void generateHost(CSMethod inMethod, Var var, Obj obj) {
        if (obj.getType() instanceof SomeType) {
            return;
        }
        JClass objClass = hierarchy.getClass(obj.getType().getName());
        Context heapContext = solver.getContextSelector().selectHeapContext(inMethod, obj);
        if (containerConfiger.isConfiguredContainerObj(obj)) {
            //generate host for configured container class.
            ContainerKind containerKind = containerConfiger.getContainerKind(objClass);
            RetrieveKind retrieveKind = null;
            if (containerKind == ContainerKind.COLLECTION) {
                retrieveKind = RetrieveKind.COL_ITEM;
            } else if (containerKind == ContainerKind.MAP) {
                retrieveKind = RetrieveKind.MAP_ALL;
            }
            Host host = hostManager.getHost(obj, retrieveKind);
            solver.addVarPointsTo(inMethod.getContext(),
                    var, heapContext, host.getMockObj());
        } else if ((containerConfiger.isContainerClass(objClass)
                && !containerConfiger.isConfiguredRelatedClass(objClass))) {
            //generate host for un-configured class.
            customContType.add(obj.getType());
            Host customHost = null;
            ContainerKind k = containerConfiger.getContainerCollectionOrMap(objClass);
            switch (k) {
                case COLLECTION -> customHost = hostManager.getHost(obj, RetrieveKind.COL_ITEM);
                case MAP -> customHost = hostManager.getHost(obj, RetrieveKind.MAP_ALL);
            }
            solver.addVarPointsTo(inMethod.getContext(),
                    var, heapContext, customHost.getMockObj());
            containerAccessAnalysis.markTaint(customHost);
        }
    }

    /**
     * for Map Entry allocations, e.g., lhs = new Map.Entry(..)
     * add and record lhs as inner source for all map classes which
     * may use this Map.Entry obj as its entry.
     */
    private void processMapEntryAllocation(CSMethod csMethod, Var lhs) {
        CSVar csLHS = csManager.getCSVar(csMethod.getContext(), lhs);
        JClass allocationInClass = csMethod.getMethod().getDeclaringClass();
        Set<JClass> allocationInMapClass = containerConfiger.getOuterMapClass(allocationInClass, Sets.newSet());
        for (JClass m : allocationInMapClass) {
            containerConfiger.getAllConfiguredSubClass(m).forEach(mapClass -> {
                // record inner entry source for future map hosts
                containerAccessAnalysis.recordHostInnerSource(mapClass.getType(), csLHS, RetrieveKind.MAP_E);
                // add entry source to all exist map hosts
                hostManager.getDominatingHostByObjType(mapClass.getType())
                        .forEach(host -> addHostSrcHelper(host, csLHS, RetrieveKind.MAP_E));
            });
        }
    }

    /**
     * process an invocation to configured entrance
     * e.g., set.add(a) | listIterator.add(a) | map.put(k,v)
     */
    private void processEntrance(Edge<CSCallSite, CSMethod> edge) {
        JMethod entrance = edge.getCallee().getMethod();
        Set<ContainerConfiger.EntranceInfo> infos = containerConfiger.getEntranceInfo(entrance);
        infos.forEach(info -> {
            CSVar cont = getArg(edge, info.contIdx());
            CSVar csArg = getArg(edge, info.sourceIdx());
            if (cont != null && csArg != null) {
                cont2entrance.put(cont, new EntranceCall(csArg, info.kind()));
                solver.getPointsToSetOf(cont).forEach(csObj -> {
                    Obj obj = csObj.getObject();
                    if (HostManager.isHost(obj)) {
                        Host host = (Host) obj.getAllocation();
                        addHostSrcHelper(host, csArg, info.kind());
                    }
                });
            }
        });
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(entrance);
        }
    }

    /**
     * process an invocation to configured exit
     * e.g., result = list.get(i); | result = iterator.next(); | result = entry.getValue();
     */
    private void processExit(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Invoke invoke = edge.getCallSite().getCallSite();
        JMethod exit = edge.getCallee().getMethod();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && invoke.getResult() != null) {
            Set<CSVar> csLHSs = getLHSS(edge);
            csLHSs.forEach(csLHS -> {
                //one exit can have multiple retrieve kinds, e.g, Hashtable$Enumerator
                containerConfiger.getExitRetrieveKind(exit).forEach(exitKind -> {
                    ExitCall exitCall = new ExitCall(csLHS, exitKind, callSite);
                    cont2exit.put(csBase, exitCall);
                    solver.getPointsToSetOf(csBase).forEach(csObj -> {
                        Obj obj = csObj.getObject();
                        if (HostManager.isHost(obj)) {
                            Host host = (Host) obj.getAllocation();
                            processExitHelper(host, exitCall);
                        }
                    });
                });
            });
        }
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(exit);
        }
    }

    private void processExitHelper(Host host, ExitCall exitCall) {
        CSCallSite callSite = exitCall.callSite;
        RetrieveKind exitKind = exitCall.kind;
        CSVar csLHS = exitCall.result;
        if (!undoCutCallSites.contains(callSite)) {
            Host actualHost = hostManager.addHostTarget(host, csLHS, exitKind);
            if (actualHost != null) {
                if (!actualHost.isTainted()) {
                    containerAccessAnalysis.onNewHostTarget(actualHost, csLHS);
                    hostRelatedExitCallSites.put(actualHost, callSite);
                } else {
                    undoCallSite(callSite);
                }
            }
        } else {
            undoCallSite(callSite);
        }
        //transfer MAP_ALL to lhs for collection.exit()
        if (exitKind == RetrieveKind.COL_ITEM
                && host.getKind() == RetrieveKind.MAP_ALL) {
            solver.addVarPointsTo(callSite.getContext(),
                    csLHS.getVar(), host.getMockObj());
        }
    }

    /**
     * process an invocation to configured transfers
     * e.g., itr = set.iterator(); | keySet = map.keySet();
     */
    private void processTransfer(Edge<CSCallSite, CSMethod> edge) {
        JMethod transfer = edge.getCallee().getMethod();
        containerConfiger.getTransferInfo(transfer).forEach(transferInfo -> {
            CSVar csFrom = getArg(edge, transferInfo.fromIdx());
            CSVar csTo = getArg(edge, transferInfo.toIdx());
            if (csFrom != null && csTo != null) {
                TransferCall transferCall = new TransferCall(
                        csTo, transferInfo.fromKind(), transferInfo.toKind());
                cont2transfer.put(csFrom, transferCall);
                solver.getPointsToSetOf(csFrom).forEach(csObj -> {
                    Obj obj = csObj.getObject();
                    if (HostManager.isHost(obj)) {
                        Host oldHost = (Host) obj.getAllocation();
                        processTransferHelper(oldHost, transferCall);
                    }
                });
            }
        });
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(transfer);
        }
    }

    private void processTransferHelper(Host oldHost, TransferCall transferCall) {
        RetrieveKind newKind = hostManager.getTransferredKind(oldHost,
                transferCall.fromKind(), transferCall.toKind());
        if (newKind != null) {
            // get the host after transferring (may have different kinds)
            Host newHost = hostManager.getHost(oldHost.getObj(), newKind);
            // add the transferred host into lhs's pts.
            solver.addVarPointsTo(transferCall.to.getContext(),
                    transferCall.to.getVar(), newHost.getMockObj());
        }
    }

    /**
     * process an invocation to configured batch-entrance
     * e.g., largeSet.addAll(smallSet)
     */
    private void processBatchEntrance(Edge<CSCallSite, CSMethod> edge) {
        JMethod batchEntrance = edge.getCallee().getMethod();
        ContainerConfiger.BatchEnInfo batchEnInfo = containerConfiger.getBatchEntrance(batchEntrance);
        int smallContainerIndex = batchEnInfo.fromIdx();
        int largeContainerIndex = batchEnInfo.toIdx();
        // add host dependency for hosts on base and smallContainer.
        CSVar csSmallContainer = getArg(edge, smallContainerIndex);
        CSVar csLargeContainer = getArg(edge, largeContainerIndex);
        if (csSmallContainer != null && csLargeContainer != null) {
            containerAccessAnalysis.addContainerVarDependency(csSmallContainer, csLargeContainer);
            // [for soundness of map_entry.K/V-entrance()]
            // for batch entrance of form base.batchEntrance(c), transfer MAP_ALL host on c to base.
            if (batchEnInfo.kind() == ContainerKind.COLLECTION) {
                colBatchEntrance.put(csSmallContainer, csLargeContainer);
                solver.getPointsToSetOf(csSmallContainer).forEach(csObj -> {
                    if (HostManager.isHost(csObj.getObject())) {
                        Host host = (Host) csObj.getObject().getAllocation();
                        if (host.getKind() == RetrieveKind.MAP_ALL) {
                            solver.addVarPointsTo(csLargeContainer.getContext(),
                                    csLargeContainer.getVar(), host.getMockObj());
                        }
                    }
                });
            }
        }
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(batchEntrance);
        }
    }

    /**
     * process an invocation to configured array entrance
     * e.g., new Set(array);
     */
    private void processArrayEntrance(Edge<CSCallSite, CSMethod> edge) {
        JMethod caller = edge.getCallSite().getCallSite().getContainer();
        Context context = edge.getCallSite().getContext();
        JMethod arrayEntrance = edge.getCallee().getMethod();
        ContainerConfiger.ArrayEnInfo arrayEnInfo = containerConfiger.getArrayEntrance(arrayEntrance);
        CSVar csArray = getArg(edge, arrayEnInfo.arrayIdx());
        CSVar csCont = getArg(edge, arrayEnInfo.contIdx());
        boolean unmodified = arrayEnInfo.unmodified();
        if (csArray != null && csCont != null) {
            // generate mock source var (for all arrayIndex)
            Var arraySource = new Var(caller,
                    "Container-Array-Source" + csArray.getVar(), TopType.Top, -1);
            CSVar csArraySource = csManager.getCSVar(context, arraySource);
            arrayMockSources.put(csArray, new ArrayReplicationInfo(unmodified, caller, context, csArraySource));
            contArraySources.put(csCont, csArraySource);
            // add edge from all ArrayIndex to mock source
            solver.getPointsToSetOf(csArray).forEach(arrayObj -> {
                ArrayIndex arrayIndex = csManager.getArrayIndex(arrayObj);
                if (unmodified && arrayReplicate != null) {
                    ArrayIndex replicatedArrayIndex =
                            arrayReplicate.replicateArrayIndex(arrayIndex, caller, context);
                    solver.addPFGEdge(new ShortcutEdge(replicatedArrayIndex, csArraySource));
                } else {
                    solver.addPFGEdge(new ShortcutEdge(arrayIndex, csArraySource));
                }
            });
            // add mock source as source for all hosts at base
            solver.getPointsToSetOf(csCont).forEach(csObj -> {
                if (HostManager.isHost(csObj.getObject())) {
                    Host host = (Host) csObj.getObject().getAllocation();
                    addHostArraySource(host, csArraySource);
                }
            });
        }
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(arrayEntrance);
        }
    }

    /**
     * helper method to add sources from array to a host.
     */
    private void addHostArraySource(Host host, CSVar arraySource) {
        if (host.getKind() == RetrieveKind.COL_ITEM) {
            addHostSrcHelper(host, arraySource, RetrieveKind.COL_ITEM);
        } else if (host.getKind() == RetrieveKind.MAP_ALL) {
            addHostSrcHelper(host, arraySource, RetrieveKind.MAP_K);
            addHostSrcHelper(host, arraySource, RetrieveKind.MAP_V);
        }
    }

    /**
     * hosts in pt(arrayIndex) are added to cont as predecessor
     * e.g., a = new MultiUIDefaults(UIDefaults);
     */
    private void processBatchArrayEn(Edge<CSCallSite, CSMethod> edge) {
        JMethod caller = edge.getCallSite().getCallSite().getContainer();
        Context context = edge.getCallSite().getContext();
        JMethod batchArrayEn = edge.getCallee().getMethod();
        ContainerConfiger.BatchArrayEnInfo batchArrayEnInfo = containerConfiger.getBatchArrayEn(batchArrayEn);
        CSVar csArray = getArg(edge, batchArrayEnInfo.arrayIdx());
        CSVar csCont = getArg(edge, batchArrayEnInfo.contIdx());
        if (csArray != null && csCont != null) {
            // generate mock array source var (whose pts contains objects in each arrayIndex)
            Var arraySource = new Var(caller,
                    "Container-Array-Source" + csArray.getVar(), TopType.Top, -1);
            CSVar csArraySource = csManager.getCSVar(context, arraySource);
            arrayMockSources.put(csArray, new ArrayReplicationInfo(false, caller, context, csArraySource));
            // add edge from all ArrayIndex to mock source
            solver.getPointsToSetOf(csArray).forEach(arrayObj -> {
                ArrayIndex arrayIndex = csManager.getArrayIndex(arrayObj);
                solver.addPFGEdge(new ShortcutEdge(arrayIndex, csArraySource));
            });
            // add dependency between mock array souce and cont.
            containerAccessAnalysis.addContainerVarDependency(csCont, csArraySource);
        }
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(batchArrayEn);
        }
    }


    /**
     * generate host for private container in util class
     * e.g., set = Collections.checkedSet(c);
     */
    private void processUtilPrivateContAlloc(Edge<CSCallSite, CSMethod> edge) {
        JMethod callee = edge.getCallee().getMethod();
        CSCallSite callSite = edge.getCallSite();
        JMethod caller = callSite.getCallSite().getContainer();
        Set<CSVar> csConts = getLHSS(edge);
        JClass contClass = containerConfiger.getUtilPrivateContAllocClass(callee);
        // generate new host
        ContainerKind containerKind = containerConfiger.getUtilPrivateContKind(contClass);
        RetrieveKind retrieveKind = null;
        if (containerKind == ContainerKind.COLLECTION) {
            retrieveKind = RetrieveKind.COL_ITEM;
        } else if (containerKind == ContainerKind.MAP) {
            retrieveKind = RetrieveKind.MAP_ALL;
        }
        // add host to container Var
        Type contType = containerConfiger.getPrivateMapAllocationType(callee);
        MockObj utilPrivateContObj = new MockObj(UTIL_PRIVATE_CONTAINER,
                callSite, contType, caller, false);
        Host host = hostManager.getHost(utilPrivateContObj, retrieveKind);
        csConts.forEach(csCont -> {
            solver.addVarPointsTo(callSite.getContext(), csCont.getVar(),
                    callSite.getContext(), host.getMockObj());
        });
        // record involved methods
        if (INVOLVED) {
            involvedMethods.add(callee);
        }
    }

    /**
     * process an invocation to configured inner-source inMethod
     */
    private void processInnerSource(Edge<CSCallSite, CSMethod> edge) {
        JMethod inMethod = edge.getCallee().getMethod();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null) {
            containerConfiger.getInnerSource(inMethod).forEach(info -> {
                RetrieveKind kind = info.kind();
                JMethod invoSig = info.sig();
                int index = info.index();
                inMethod.getIR().forEach(stmt -> {
                    if (stmt instanceof Invoke invoke) {
                        // invoke is inside the inner-source inMethod
                        // e.g., e = function.apply(a, b);
                        JMethod ref = invoke.getInvokeExp().getMethodRef().resolve();
                        if (invoSig.equals(ref)) {
                            Var source = index == InvokeUtils.RESULT ? invoke.getResult() :
                                    invoke.getInvokeExp().getArg(index);
                            Context context = edge.getCallee().getContext();
                            CSVar csSource = csManager.getCSVar(context, source);
                            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                                Obj obj = csObj.getObject();
                                if (HostManager.isHost(obj)) {
                                    Host host = (Host) obj.getAllocation();
                                    cont2inners.put(csBase,
                                            new InnerSourceCall(csSource, kind));
                                    addHostSrcHelper(host, csSource, kind);
                                }
                            });
                        }
                    }
                });
            });
        }
    }
    /**
     * the called method will change the content of the container
     * thus, we mark all related hosts as unsound.
     */
    private void processUnsoundOp(Edge<CSCallSite, CSMethod> edge) {
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null) {
            triggerContVarUnsound(csBase);
        }
    }

    private void processThisHost(Edge<CSCallSite, CSMethod> edge) {
        JMethod callee = edge.getCallee().getMethod();
        Var calleeThis = callee.getIR().getThis();
        if (calleeThis != null) {
            CSVar csCalleeThis = csManager.getCSVar(edge.getCallee().getContext(), calleeThis);
            if (!thisHubHostMethods.contains(callee)) {
                thisHubHostMethods.add(callee);
                containerAccessAnalysis.getThisHubHosts().forEach(hub -> {
                    solver.addPointsTo(csCalleeThis, hub.getMockObj());
                });
            }
        }
    }

    /**
     * when used together with StreamHandler, handle pipeline output to containers.
     * e.g., itr = stm.iterator(); | spliterator = stm.spliteraror()
     */
    private void processSimpleStm2Cont(Edge<CSCallSite, CSMethod> edge) {
        Invoke invoke = edge.getCallSite().getCallSite();
        JMethod caller = invoke.getContainer();
        CSVar stm = getArg(edge, InvokeUtils.BASE);
        Set<CSVar> csLHSs = getLHSS(edge);
        if (stm != null && invoke.getResult() != null) {
            // 1. generate host H at invocation site
            MockObj pipeOutObj = new MockObj(PIPELINE_OUT, invoke,
                                             NullType.NULL, caller, false);
            Host pipeOutHost = hostManager.getHost(pipeOutObj, RetrieveKind.COL_ITEM);
            // if do not use stream pattern
            if (streamAnalysis == null) {
                containerAccessAnalysis.markTaint(pipeOutHost);
                return;
            }
            // if use stream pattern
            stmToHost.put(stm, pipeOutHost);
            // 2. add H to pts(set)
            csLHSs.forEach(csContainer -> {
                solver.addPointsTo(csContainer, pipeOutHost.getMockObj());
            });
            // 3. link strm (of stm) 's sourceHub to H's sourceHub
            solver.getPointsToSetOf(stm).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    addHostSrcHelper(pipeOutHost, l.getSourceHub(), RetrieveKind.COL_ITEM);
                    streamAnalysis.addStrm2Host(l, pipeOutHost);
                    // if l is unsound, mark host as unsound.
                    if (l.isConserv()) {
                        containerAccessAnalysis.markTaint(pipeOutHost);
                    }
                }
            });
        }
    }

    private void processSpecialHostGeneration(Edge<CSCallSite, CSMethod> edge) {
        // new reflective/lambda constructed collection
        JMethod callee = edge.getCallee().getMethod();
        JClass initClass = callee.getDeclaringClass();
        if (containerConfiger.isRelatedClass(initClass)) {
            Set<CSVar> csLhss = getLHSS(edge);
            contSPConstructorLHS.addAll(csLhss);
        }
    }

    /**
     * attempt to add source to a host given RetrieveKind kind.
     * see #addHostSource in {@link ContainerAccessAnalysis}
     * invoke onNewHostSource if successfully added.
     */
    void addHostSrcHelper(Host host, CSVar source, RetrieveKind kind) {
        Host actualHost = hostManager.addHostSource(host, source, kind);
        if (actualHost != null && !actualHost.isTainted()) {
            containerAccessAnalysis.onNewHostSource(actualHost, source);
        }
    }

    /**
     * if a csVar which may contains host is unsound,
     * all host (existing or may propagated into) is unsound
     */
    void triggerContVarUnsound(CSVar csCont) {
        unsoundBases.add(csCont);
        solver.getPointsToSetOf(csCont).forEach(csObj -> {
            Obj obj = csObj.getObject();
            if (HostManager.isHost(obj)) {
                Host host = (Host) obj.getAllocation();
                if (!host.isTainted()) {
                    containerAccessAnalysis.markTaint(host);
                }
            }
        });
    }

    /**
     * use helper to get arg/return at different kinds of call-edge
     */
    private CSVar getArg(Edge<CSCallSite, CSMethod> edge, int index) {
        return helper.getCallSiteArg(edge, index);
    }

    private Set<CSVar> getLHSS(Edge<CSCallSite, CSMethod> edge) {
        return helper.getPotentialLHS(edge);
    }

    boolean isInScope(JClass c) {
        if (this.streamScope == null) {
            return containerConfiger.isInScopeClass(c);
        } else {
            return containerConfiger.isInScopeClass(c) || this.streamScope.contains(c);
        }
    }

    boolean isArrayReplicationInScope(JClass c) {
        if (this.streamScope == null) {
            return containerConfiger.isArrayReplicationInScope(c);
        } else {
            return containerConfiger.isArrayReplicationInScope(c) || this.streamScope.contains(c);
        }
    }

    /**
     * methods to collaborate with other csc patterns
     */

    public void setStreamPatternInfo(StreamAnalysis analysis,
                                     Set<JClass> streamScope, Set<JMethod> streamExits) {
        this.streamAnalysis = analysis;
        this.streamScope = streamScope;
        this.streamExits = streamExits;
        containerAccessAnalysis.setStreamAnalysis(analysis);
    }

    public ContainerAccessAnalysis getAnalysis() {
        return containerAccessAnalysis;
    }

    public Set<JMethod> getExits() {
        return this.containerConfiger.getExits();
    }

    public Set<JClass> getContainerConfigScope() {
        return containerConfiger.getInScopeClass();
    }

    public Set<JClass> getArrayRepScope() {
        return containerConfiger.getArrayRepInScopeClass();
    }

    @Override
    public void onFinish() {
        Plugin.super.onFinish();
        if (streamAnalysis != null) {
            recordStreamRelatedStatistics();
        }
        if (INVOLVED) {
            dumpInvolvedMethods();
        }
    }

    private void recordStreamRelatedStatistics() {
        // find all cast var of target
        Set<Var> targets = Sets.newSet();
        containerAccessAnalysis.containerTargets.forEach((h, v) -> {
            if (h.getObj() instanceof MockObj mockObj
                    && mockObj.getDescriptor().equals(PIPELINE_OUT)) {
                targets.addAll(helper.findCastVar(v.getVar()));
            }
        });

        // record all tainted targets
        Set<Var> taintedTargets = Sets.newSet();
        containerAccessAnalysis.containerTargets.forEach((h, v) -> {
            if (h.isTainted()) {
                taintedTargets.addAll(helper.findCastVar(v.getVar()));
            }
            if (h.getObj() instanceof MockObj mockObj
                    && (!mockObj.getDescriptor().equals(PIPELINE_OUT))) {
                taintedTargets.addAll(helper.findCastVar(v.getVar()));
            }
        });

        // dump all container exit lhs var name
        String contTargetFile = "stm-related-container-target.txt";
        File outFile = new File(World.get().getOptions().getOutputDir(), contTargetFile);
        try (PrintStream out = new PrintStream(new FileOutputStream(outFile))) {
            targets.stream()
                    .filter(v -> v.getMethod().isApplication())
                    .filter(v -> !taintedTargets.contains(v))
                    .sorted(Comparator.comparing(Var::toString))
                    .forEach(v -> {
                        if (containerLambdaAnalysis.lamArgHubLMockArgCpt.containsKey(v)) {
                            // output lambda mock arg
                            Set<Var> mockArgs = containerLambdaAnalysis.lamArgHubLMockArgCpt.get(v);
                            mockArgs.forEach(mockArg -> {
                                out.print(v.getMethod().toString() + '/' + v.getName() + " <=> ");
                                out.println(mockArg.getMethod().toString() + '/' + mockArg);
                            });
                        } else {
                            out.println(v.getMethod().toString() + '/' + v.getName());
                        }
                    });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void dumpInvolvedMethods() {
        File outputDir = World.get().getOptions().getOutputDir();
        String filename = "csc-c-involved-methods.txt";
        try (PrintStream out =
                     new PrintStream(new FileOutputStream(new File(outputDir, filename)))) {
            involvedMethods.stream()
                    .map(ClassMember::toString)
                    .sorted()
                    .forEach(out::println);
        } catch (FileNotFoundException e) {
            e.fillInStackTrace();
        }
    }
}