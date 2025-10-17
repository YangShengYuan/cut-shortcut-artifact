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

package pascal.taie.analysis.pta.toolkit.cutshortcut.stream;

import pascal.taie.World;
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
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaCallEdge;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ArrayReplicationAnalysis;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.ContainerAccessAnalysis;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.Host;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.HostManager;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.RetrieveKind;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.New;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.SomeType;
import pascal.taie.language.type.TopType;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;
import pascal.taie.util.collection.Sets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StreamHandler implements Plugin {

    /**
     * utils
     */
    private final Solver solver;
    private final CSManager csManager;
    private final HeapModel heapModel;
    private final TypeSystem typeSystem;
    private final StreamAnalysis streamAnalysis;
    private final StreamLambdaAnalysis streamLambdaAnalysis;
    private ContainerAccessAnalysis containerAnalysis = null;
    private final ArrayReplicationAnalysis arrayAnalysis;
    private final CSCHelper helper;
    private final StreamConfiger streamConfiger;
    private Set<JClass> containerScope = null;
    private Set<JClass> containerArrayRepScope = null;

    /**
     *  abstract stream pipeline attributes
     */
    public static final Descriptor STRM_OBJ_DESC = () -> "Pipeline-Strm";
    private final SomeType strmType;
    private final Map<Stmt, Strm> abstractPipelines;

    /**
     * core data structure for cut and undo cut edges.
     */
    private final Set<Var> cutReturns = Sets.newHybridSet();
    final MultiMap<Strm, CSCallSite> pipeRelatedOutputCallSites = Maps.newMultiMap();
    private final Set<CSCallSite> undoCutCallSites = Sets.newHybridSet();
    private final MultiMap<CSCallSite, CSMethod> undoCallEdge = Maps.newMultiMap();
    private final MultiMap<Var, Var> undoCuts = Maps.newMultiMap();

    /**
     * records supporting onNewPointsToSet(...) handling.
     */
    record ArrayReplicationInfo(boolean replicable, JMethod inmethod, Context context, CSVar arraySource) {
    }

    /**
     * for later arrived stream objs
     */
    // record arrayVar to mock source
    private final Map<CSVar, ArrayReplicationInfo> arrayMockSources = Maps.newHybridMap();
    // record stm to source
    private final MultiMap<CSVar, CSVar> stm2Source = Maps.newMultiMap();
    // e.g. temp = stm.filter(..); record stm -> temp
    private final MultiMap<CSVar, CSVar> stm2Process = Maps.newMultiMap();
    // e.g. temp = opt.get(); record opt -> call-site
    private final MultiMap<CSVar, Edge<CSCallSite, CSMethod>> stm2Output = Maps.newMultiMap();
    // e.g. conceited = Stream.concat(stream1, stream2);
    // generate L and record stream1 -> L / stream2 -> L.
    final MultiMap<CSVar, Strm> var2PipeInput = Maps.newMultiMap();
    // unsound stm base.
    private final Set<CSVar> unsoundStm = Sets.newSet();

    /**
     * for later arrived container Hosts.
     */
    // e.g. stm = list.stream(); generate L and record list -> L
    private final MultiMap<CSVar, Strm> cont2Pipe = Maps.newMultiMap();

    /**
     * statistics
     */
    static final boolean STM_STATISTICS = true;

    public StreamHandler(Solver solver, boolean preciseLambda,
                         CSCHelper helper, LambdaAnalysis lambdaAnalysis,
                         ArrayReplicationAnalysis arrayAnalysis) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
        this.typeSystem = solver.getTypeSystem();
        this.heapModel = solver.getHeapModel();
        this.arrayAnalysis = arrayAnalysis;
        this.helper = helper;
        this.streamAnalysis = new StreamAnalysis(
                solver, csManager, this);
        this.streamConfiger = new StreamConfiger(
                solver.getHierarchy(),
                solver.getTypeSystem());
        this.streamLambdaAnalysis = new StreamLambdaAnalysis(
                preciseLambda,
                solver, csManager,
                lambdaAnalysis,
                streamAnalysis, this,
                streamConfiger, helper);
        this.streamAnalysis.setStreamLambdaAnalysis(streamLambdaAnalysis);
        this.strmType = new SomeType(streamConfiger.getStrmPossibleTypes());
        this.abstractPipelines = Maps.newMap();
    }

    /**
     * identify cutReturns for Stream Output stage
     * @param method new reachable method
     */
    @Override
    public void onNewMethod(JMethod method) {
        // cut is defined by CALLEE, not SIG.
        // for each invocation to a stream output API
        // the return edge should be cut.
        if (streamConfiger.isExit(method)) {
             cutReturns.addAll(method.getIR().getReturnVars());
        }
    }

    /**
     * for each custom New Stream stmt, add a new unsound strm
     * @param csMethod new reachable context-sensitive method
     */
    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        method.getIR().forEach(stmt -> {
            if (stmt instanceof New newStmt) {
                Obj obj = heapModel.getObj(newStmt);
                JClass declaring = csMethod.getMethod().getDeclaringClass();
                if (!isInScope(declaring)
                        && obj.isFunctional()
                        && typeSystem.isSubtype(streamConfiger.streamType, obj.getType())) {
                    Var lhs = newStmt.getLValue();
                    Strm l = generateCustomStreamStrm(newStmt);
                    Context heapContext = solver.getContextSelector().selectHeapContext(csMethod, obj);
                    solver.addVarPointsTo(csMethod.getContext(), lhs, heapContext, l.getMockObj());
                }
            }
        });
    }

    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        JMethod callee = edge.getCallee().getMethod();
        // creation
        if (streamConfiger.isCreation(callee)) {
            handleCreation(edge);
        }
        // container creation
        if (streamConfiger.isContCreation(callee)) {
            handleContCreation(edge);
        }
        // array creation
        if (streamConfiger.isArrayCreation(callee)) {
            handleArrayCreation(edge);
        }
        // entrance
        if (streamConfiger.isEntrance(callee)) {
            handleEntrance(edge);
        }
        // process
        if (streamConfiger.isProcess(callee)) {
            handleProcess(edge);
        }
        // output
        if (streamConfiger.isOutput(callee)) {
            handleOutput(edge);
        }
        // conservModeled
        if (streamConfiger.isConservModeledOp(callee)) {
            handleConservativelyModeledOp(edge);
        }
        // Stream.concat()
        if (streamConfiger.isConcat(callee)) {
            handleStreamConcat(edge);
        }
        // let stream lambda analysis handle the rest.
        streamLambdaAnalysis.onNewCallEdge(edge);
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (stm2Source.containsKey(csVar)
                || stm2Process.containsKey(csVar)
                || stm2Output.containsKey(csVar)
                || var2PipeInput.containsKey(csVar)
                || cont2Pipe.containsKey(csVar)
                || arrayMockSources.containsKey(csVar)) {
            pts.forEach(csObj -> {
                Obj obj = csObj.getObject();
                // new strm
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    // entrance
                    stm2Source.get(csVar).forEach(source -> {
                        streamAnalysis.addStrmSource(l, source);
                    });
                    // process
                    stm2Process.get(csVar).forEach(lhs -> {
                        solver.addPointsTo(lhs, obj);
                    });
                    // output
                    stm2Output.get(csVar).forEach(edge -> {
                        handleOutputHelper(l, edge);
                    });
                    // concat
                    var2PipeInput.get(csVar).forEach(succ -> {
                        streamAnalysis.addStrmSuccessor(succ, l);
                    });
                }
                // new container host
                if (HostManager.isHost(obj)) {
                    Host h = (Host) obj.getAllocation();
                    // stm = container.stream();
                    cont2Pipe.get(csVar).forEach(l -> {
                        // if host is MAP_ALL, transfer it into L source
                        if (h.getKind() == RetrieveKind.MAP_ALL) {
                            streamAnalysis.addStrmObj(l, csObj);
                        } else if (h.isTainted()) { // if h is taint, mark l as unsound.
                            streamAnalysis.markUnsound(l);
                        } else {
                            streamAnalysis.addStrmSource(l, h.getSourceHub());
                            containerAnalysis.addHost2Strm(h, l);
                        }
                    });
                }
                // for new array obj of recorded array var
                // link arrayIndex to mock source
                if (arrayMockSources.get(csVar) != null) {
                    ArrayReplicationInfo info = arrayMockSources.get(csVar);
                    ArrayIndex arrayIndex = csManager.getArrayIndex(csObj);
                    if (info.replicable && arrayAnalysis != null) {
                        ArrayIndex replicated = arrayAnalysis.replicateArrayIndex(
                                arrayIndex, info.inmethod, info.context);
                        solver.addPFGEdge(new ShortcutEdge(
                                replicated, info.arraySource));
                    } else {
                        solver.addPFGEdge(new ShortcutEdge(
                                arrayIndex, info.arraySource));
                    }
                }
            });
        }
        // let stream lambda analysis handle the rest
        streamLambdaAnalysis.onNewPointsToCSObj(csVar, pts);
    }

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
        boolean isInScopeExitEdge =
                edge.kind() == FlowKind.RETURN
                && edge.target() instanceof CSVar csLHS
                && isInScope(csLHS.getVar().getMethod().getDeclaringClass());
        return (!isCutEdge) || (isUndoCutEdge) || (isInScopeExitEdge);
    }

    @Override
    public void onNewArrayStoreEdge(CSVar from, ArrayIndex arrayIndex) {
        if (arrayAnalysis != null) {
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
                arrayAnalysis.reflectStoring(arrayIndex, from);
            }
        }
    }

    @Override
    public Transfer getRetEdgeTransfer() {
        return ((edge, input) -> {
            if (edge.kind() == FlowKind.RETURN
                    && edge.source() instanceof CSVar ret) {
                JMethod m = ret.getVar().getMethod();
                if (streamConfiger.isRetEdgeTransferCallee(m)) {
                    PointsToSet result = solver.makePointsToSet();
                    for (CSObj csObj : input) {
                        if (!helper.isStrm(csObj.getObject())) {
                            result.addObject(csObj);
                        }
                    }
                    return result;
                } else {
                    return input;
                }
            } else {
                return input;
            }
        });
    }

    @Override
    public Transfer getPassEdgeTransfer() {
        return streamLambdaAnalysis.getPassEdgeTransfer();
    }


    /**
     * handle call edge for creation stage. e.g.,
     * s = Stream.of();
     */
    private void handleCreation(Edge<CSCallSite, CSMethod> edge) {
        // 1. generate strm for pipeline.
        Strm l = generatePipelineStrm(edge);
        // 2. mark the stream variable with generated strm
        addStrmToLhsHelper(edge, l);
    }

    /**
     * handle stream creation from container. e.g.,
     * s = set.stream();
     * specially, we need to transfer MAP_ALL at cont to L.source.
     */
    private void handleContCreation(Edge<CSCallSite, CSMethod> edge) {
        Invoke invoke = edge.getCallSite().getCallSite();
        JMethod callee = edge.getCallee().getMethod();
        int index = streamConfiger.getContCreationContIndex(callee);
        // 1. generate strm L for pipeline.
        Strm l = generatePipelineStrm(edge);
        // 2. mark the stream variable with generated strm
        addStrmToLhsHelper(edge, l);
        CSVar csCont = getArg(edge, index);
        if (csCont != null) {
            // if do not use container access pattern
            if (containerAnalysis == null) {
                streamAnalysis.markUnsound(l);
                return;
            }
            // 3. if call-site in container config box, mark as unsound
            JClass declaring = invoke.getContainer().getDeclaringClass();
            if (this.containerScope.contains(declaring)) {
                streamAnalysis.markUnsound(l);
            } else {
                // 4. handle relation between host on container and L
                cont2Pipe.put(csCont, l);
                solver.getPointsToSetOf(csCont).forEach(csObj -> {
                    Obj obj = csObj.getObject();
                    if (HostManager.isHost(obj)) {
                        Host h = (Host) obj.getAllocation();
                        // if host is MAP_ALL, transfer it into L source
                        if (h.getKind() == RetrieveKind.MAP_ALL) {
                            streamAnalysis.addStrmObj(l, csObj);
                        } else if (h.isTainted()) { // when host is changed to taint, trigger l's unsound
                            streamAnalysis.markUnsound(l);
                        } else {
                            streamAnalysis.addStrmSource(l, h.getSourceHub());
                            containerAnalysis.addHost2Strm(h, l);
                        }
                    }
                });
            }
        } else {
            streamAnalysis.markUnsound(l);
        }
    }

    /**
     * handle call edge for calling creation stage which uses array as argument. e.g.,
     * s = Stream.of(java.lang.Object[]);
     */
    private void handleArrayCreation(Edge<CSCallSite, CSMethod> edge) {
        Context context = edge.getCallSite().getContext();
        JMethod callee = edge.getCallee().getMethod();
        JMethod caller = edge.getCallSite().getCallSite().getContainer();
        Pair<Integer, Boolean> record = streamConfiger.getArrayCreationArrayIndex(callee);
        int index = record.first();
        boolean unmodified = record.second();
        // 1. generate strm for pipeline.
        Strm l = generatePipelineStrm(edge);
        // 2. mark the stream variable with generated strm
        addStrmToLhsHelper(edge, l);
        // 3. link input pointer for generated strm
        // generate mock array var (for all arrayIndex)
        CSVar csArray = getArg(edge, index);
        if (csArray != null) {
            Var arraySource = new Var(caller, "Stream-Array-Source" + csArray.getVar(), TopType.Top, -1);
            CSVar csArraySource = csManager.getCSVar(context, arraySource);
            arrayMockSources.put(csArray, new ArrayReplicationInfo(unmodified, caller, context, csArraySource));
            // add edge from all ArrayIndex to mock source
            solver.getPointsToSetOf(csArray).forEach(arrayObj -> {
                ArrayIndex arrayIndex = csManager.getArrayIndex(arrayObj);
                if (unmodified && arrayAnalysis != null) {
                    ArrayIndex replicatedArrayIndex =
                            arrayAnalysis.replicateArrayIndex(arrayIndex, caller, context);
                    solver.addPFGEdge(new ShortcutEdge(replicatedArrayIndex, csArraySource));
                } else {
                    solver.addPFGEdge(new ShortcutEdge(arrayIndex, csArraySource));
                }
            });
            // link mock source as input pointer
            streamAnalysis.addStrmSource(l, csArraySource);
        }
    }

    /**
     * handle entrance for stream, e.g,
     * builder = builder.add(e)
     */
    private void handleEntrance(Edge<CSCallSite, CSMethod> edge) {
        JMethod callee = edge.getCallee().getMethod();
        // link input pointer for generated strm
        Pair<Integer, Integer> info = streamConfiger.getEntranceInfo(callee);
        int sourceIdx = info.first();
        int stmIdx = info.second();
        CSVar csArg = getArg(edge, sourceIdx);
        CSVar csStm = getArg(edge, stmIdx);
        if (csArg != null && csStm != null) {
            this.stm2Source.put(csStm, csArg);
            solver.getPointsToSetOf(csStm).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    streamAnalysis.addStrmSource(l, csArg);
                }
            });
        }
    }

    /**
     * handle call edge for calling process stage. e.g.,
     * temp = stream.filter(java.util.function.Predicate);
     * r = stream.findFirst();
     */
    private void handleProcess(Edge<CSCallSite, CSMethod> edge) {
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        // get lhs
        Set<CSVar> csLHSs = getLHSS(edge);
        csLHSs.forEach(csLHS -> {
            if (csBase != null) {
                stm2Process.put(csBase, csLHS);
                //transfer strm from receiver variable to LHS variable
                solver.getPointsToSetOf(csBase).forEach(csObj -> {
                    Obj obj = csObj.getObject();
                    if (helper.isStrm(obj)) {
                        solver.addPointsTo(csLHS, obj);
                    }
                });
            }
        });
    }

    /**
     * handle call edge for calling output stage. e.g.,
     * r = optional.orElse(java.lang.Object);
     */
    private void handleOutput(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && invoke.getResult() != null
                && !undoCutCallSites.contains(callSite)) {
            stm2Output.put(csBase, edge);
            // add target to strm
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    handleOutputHelper(l, edge);
                }
            });
        }
        if (undoCutCallSites.contains(callSite)) {
            undoCallSite(callSite);
        }
    }

    private void handleOutputHelper(Strm l, Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Set<CSVar> csTargets = getLHSS(edge);
        csTargets.forEach(target -> streamAnalysis.streamTargets.put(l, target));
        if (l.isConserv()) {
            undoCallSite(callSite);
        } else {
            csTargets.forEach(target -> streamAnalysis.addStrmTarget(l, target));
            pipeRelatedOutputCallSites.put(l, callSite);
        }
    }

    private void handleConservativelyModeledOp(Edge<CSCallSite, CSMethod> edge) {
        // generate new strm
        Strm newL = generatePipelineStrm(edge);
        // add strm to lhs
        addStrmToLhsHelper(edge, newL);
        // mark the strm unsound.
        streamAnalysis.markUnsound(newL);
    }

    /**
     * handle call edge for Stream.concat(...). e.g.,
     * r = Stream.concat(s1, s2);
     */
    private void handleStreamConcat(Edge<CSCallSite, CSMethod> edge) {
        // 1. generate strm L for pipeline
        Strm l = generatePipelineStrm(edge);
        // 2. mark the stream variable with generated strm
        addStrmToLhsHelper(edge, l);
        // 3. handle relation between L and s1, s2
        // record s1 -> L and s2 -> L.
        CSVar s1 = getArg(edge, 0);
        CSVar s2 = getArg(edge, 1);
        // add strm at s1 and s2 as predecessors for L.
        if (s1 != null) {
            var2PipeInput.put(s1, l);
            addStrmPredVar(l, s1);
        }
        if (s2 != null) {
            var2PipeInput.put(s2, l);
            addStrmPredVar(l, s2);
        }
    }

    void addStrmPredVar(Strm l, CSVar stm) {
        solver.getPointsToSetOf(stm).forEach(csObj -> {
            Obj obj = csObj.getObject();
            if (helper.isStrm(obj)) {
                Strm pred = (Strm) obj.getAllocation();
                streamAnalysis.addStrmSuccessor(l, pred);
            }
        });
    }

    Strm generatePipelineStrm(Edge<CSCallSite, CSMethod> edge) {
        Invoke invoke = edge.getCallSite().getCallSite();
        if (edge instanceof LambdaCallEdge lambdaCallEdge) {
            invoke = lambdaCallEdge.getIndyInvoke();
        }
        if (abstractPipelines.containsKey(invoke)) {
            return abstractPipelines.get(invoke);
        } else {
            Strm l = new Strm(invoke);
            MockObj m = new MockObj(STRM_OBJ_DESC, l, strmType, null, false);
            l.setMockObj(m);
            streamAnalysis.onNewStrm(l);
            abstractPipelines.put(invoke, l);
            return l;
        }
    }

    private Strm generateCustomStreamStrm(Stmt newStmt) {
        Strm l;
        if (abstractPipelines.containsKey(newStmt)) {
            l = abstractPipelines.get(newStmt);
        } else {
            l = new Strm(newStmt);
            MockObj m = new MockObj(STRM_OBJ_DESC, l, strmType, null, false);
            l.setMockObj(m);
            streamAnalysis.onNewStrm(l);
            abstractPipelines.put(newStmt, l);
        }
        streamAnalysis.markUnsound(l);
        return l;
    }

    void addStrmToLhsHelper(Edge<CSCallSite, CSMethod> edge, Strm l) {
        Set<CSVar> csLHSS = getLHSS(edge);
        csLHSS.forEach(csLHS -> {
            solver.addPointsTo(csLHS, l.getMockObj());
        });
    }

    void undoStrmRelatedOutput(Strm l) {
        pipeRelatedOutputCallSites.get(l).forEach(this::undoCallSite);
    }

    void undoCallSite(CSCallSite csCallSite) {
        undoCutCallSites.add(csCallSite);
        solver.getCallGraph().edgesOutOf(csCallSite).forEach(this::undoCutEdge);
    }

    private void undoCutEdge(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite csCallSite = edge.getCallSite();
        CSMethod csCallee = edge.getCallee();
        if (undoCallEdge.put(csCallSite, csCallee)) {
            Set<CSVar> csLHSs = getLHSS(edge);
            JMethod callee = csCallee.getMethod();
            csLHSs.forEach(csLHS -> {
                callee.getIR().getReturnVars().forEach(ret -> {
                    CSVar csRet = csManager.getCSVar(csCallee.getContext(), ret);
                    PointerFlowEdge retEdge = new PointerFlowEdge(FlowKind.RETURN, csRet, csLHS);
                    this.undoCuts.put(ret, csLHS.getVar());
                    solver.addPFGEdge(retEdge, solver.getRetEdgeTransfer());
                });
            });
        }
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
        if (containerScope == null) {
            return streamConfiger.isInScopeClass(c);
        } else {
            return streamConfiger.isInScopeClass(c) || this.containerScope.contains(c);
        }
    }

    boolean isArrayReplicationInScope(JClass c) {
        if (containerScope == null) {
            return streamConfiger.isInScopeClass(c);
        } else {
            return streamConfiger.isInScopeClass(c) || this.containerArrayRepScope.contains(c);
        }
    }

    /**
     * methods to collaborate with other csc patterns
     */

    public void setContainerAccessInfo(ContainerAccessAnalysis analysis,
                                       Set<JClass> containerScope, Set<JClass> containerArrayRepScope) {
        this.containerAnalysis = analysis;
        this.containerScope = containerScope;
        this.containerArrayRepScope = containerArrayRepScope;
        streamAnalysis.setContainerAnalysis(analysis);
    }

    public StreamAnalysis getAnalysis() {
        return this.streamAnalysis;
    }

    public Set<JClass> getStreamConfigInScope() {
        return streamConfiger.getInScopeClass();
    }

    public Set<JMethod> getExits() {
        return streamConfiger.getExits();
    }

    @Override
    public void onFinish() {
        Plugin.super.onFinish();
        if (STM_STATISTICS) {
            recordStreamStatistics();
        }
    }

    private void recordStreamStatistics() {

        // print affected stm count
        System.out.println("affected stm count: " +
                abstractPipelines.values().stream().filter(l -> !l.isConserv()).collect(Collectors.toSet()).size());

        // find all cast var of target
        Set<Var> targets = Sets.newSet();
        streamAnalysis.streamTargets.forEach((l, v) -> {
                targets.addAll(helper.findCastVar(v.getVar()));
        });

        // find all cast var of stream reduce-out/collect-out, dump
        streamLambdaAnalysis.funcOutputLHS.forEach(lhs -> {
            targets.addAll(helper.findCastVar(lhs));
        });

        // dump all container exit lhs var name
        String stmTargetFile = "stream-target.txt";
        File outFile = new File(World.get().getOptions().getOutputDir(), stmTargetFile);
        try (PrintStream out = new PrintStream(new FileOutputStream(outFile))) {
            targets.stream()
                    .filter(v -> v.getMethod().isApplication())
                    .sorted(Comparator.comparing(Var::toString))
                    .forEach(v -> {
                        if (streamLambdaAnalysis.lamArgHubLMockArgCpt.containsKey(v)) {
                            // output lambda mock Arg
                            Set<Var> mockArgs = streamLambdaAnalysis.lamArgHubLMockArgCpt.get(v);
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
}