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

import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.core.solver.Transfer;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaCallEdge;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaInfo;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.analysis.pta.toolkit.cutshortcut.stream.Strm;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.TopType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;
import pascal.taie.util.collection.Sets;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static pascal.taie.analysis.pta.toolkit.cutshortcut.container.ContainerAccessHandler.PIPELINE_OUT;

public class ContainerLambdaAnalysis {

    /**
     * utils
     */
    private final Solver solver;
    private final CSManager csManager;
    private final TypeSystem typeSystem;
    private final HeapModel heapModel;
    private final HostManager hostManager;
    private final LambdaAnalysis lambdaAnalysis;
    private final ContainerAccessAnalysis containerAccessAnalysis;
    private final ContainerAccessHandler handler;
    private final ContainerConfiger containerConfiger;
    private final CSCHelper helper;

    /**
     * mark if the lambda need precise modeling
     */
    final boolean lambdaPrecise;

    /**
     * records
     */
    record Src2LamPrcArgInfo(boolean isBinary, int idx, CSVar csSrcHub) {
    }
    record HostSrc2LamArgHubInfo(CSVar srcHub, RetrieveKind kind, CSVar funtion) {
    }

    /**
     * for later arrived functional Obj
     */
    private final MultiMap<CSVar, Src2LamPrcArgInfo> function2LamSrcHub = Maps.newMultiMap();
    private final Set<CSVar> functions = Sets.newSet();
    private final Set<CSVar> unsoundLambdas = Sets.newSet();
    private final MultiMap<CSVar, CSVar> function2RetTarget = Maps.newMultiMap();
    private final MultiMap<CSVar, CSVar> function2Unsound = Maps.newMultiMap();

    /**
     * for later arrived host.
     */
    private final MultiMap<CSVar, HostSrc2LamArgHubInfo> cont2LamSrcHub = Maps.newMultiMap();
    private final MultiMap<CSVar, Pair<CSVar, RetrieveKind>> con2LamRet = Maps.newMultiMap();
    private final MultiMap<CSVar, CSVar> base2MayNotCont = Maps.newMultiMap();

    /**
     * for collectors handling.
     */
    private final Set<Obj> soundCollCollectors = Sets.newSet();
    private final Type collectorImpl;
    private final MultiMap<CSVar, Pair<CSMethod, CSVar>> collector2PipeOut = Maps.newMultiMap();

    /**
     * for soundness control of Map-Entries when lambdaPrecise is false;
     */
    private final Type mapEntryType;
    private static final Descriptor LAM_MAPE_PARAM_UNSOUND = () -> "lambda_mapEntry_param_unsound";

    /**
     * statistics
     */
    final MultiMap<Var, Var> lamArgHubLMockArgCpt = Maps.newMultiMap();

    ContainerLambdaAnalysis(boolean lambdaPrecise, Solver solver, CSManager csManager,
                            HeapModel heapModel, TypeSystem typeSystem, HostManager hostManager,
                            LambdaAnalysis lambdaAnalysis, ContainerAccessHandler handler,
                            ContainerConfiger configer, ContainerAccessAnalysis containerAccessAnalysis,
                            CSCHelper helper) {
        this.lambdaPrecise = lambdaPrecise;
        this.solver = solver;
        this.csManager = csManager;
        this.typeSystem = typeSystem;
        this.heapModel = heapModel;
        this.hostManager = hostManager;
        this.lambdaAnalysis = lambdaAnalysis;
        this.handler = handler;
        this.containerConfiger = configer;
        this.containerAccessAnalysis = containerAccessAnalysis;
        this.helper = helper;
        this.collectorImpl = typeSystem.getType(
                "java.util.stream.Collectors$CollectorImpl");
        this.mapEntryType = typeSystem.getType("java.util.Map$Entry");
    }

    /**
     * find the sound CollectorImpl obj allocated in configured methods.
     * add them into sound Collector set.
     */
    public void onNewMethod(JMethod method) {
        if (containerConfiger.isCollectorCollCreation(method)) {
            method.getIR().forEach(stmt -> {
                if (stmt instanceof New newStmt) {
                    Obj obj = heapModel.getObj(newStmt);
                    Var lhs = newStmt.getLValue();
                    if (lhs.getType().equals(collectorImpl)) {
                        soundCollCollectors.add(obj);
                    }
                }
            });
        }
    }

    void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {

        JMethod callee = edge.getCallee().getMethod();
        // collection forEach
        if (containerConfiger.isCollForEach(callee)) {
            processCollForEach(edge);
        }
        // map forEach
        if (containerConfiger.isMapForEach(callee)) {
            processMapForEach(edge);
        }
        // list replaceAll
        if (containerConfiger.isListReplAll(callee)) {
            processListReplaceAll(edge);
        }
        // map replaceAll
        if (containerConfiger.isMapReplAll(callee)) {
            processMapReplaceAll(edge);
        }
        // map merge
        if (containerConfiger.isMapMerge(callee)) {
            processMapMerge(edge);
        }
        // map computeIfPresent
        if (containerConfiger.isMapComputeIfPresent(callee)) {
            processMapComputeIfPresent(edge);
        }
        // map computeIfAbsent
        if (containerConfiger.isMapComputeIfAbsent(callee)) {
            processMapComputeIfAbsent(edge);
        }
        // stm collector collect.
        if (containerConfiger.isStmCollectorCollect(callee)) {
            processStmCollectorCollect(edge);
        }
        if (edge instanceof LambdaCallEdge lambdaCallEdge) {
            processMapEntryParam(lambdaCallEdge);
        }
    }

    void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (!lambdaPrecise) {
            return;
        }
        // handle new host
        if (cont2LamSrcHub.containsKey(csVar)
                || con2LamRet.containsKey(csVar)) {
            pts.forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    //forEach with lambda expression
                    cont2LamSrcHub.get(csVar).forEach(info ->
                            addHost2LamArgHubHelper(host, info.srcHub,
                                    info.kind, info.funtion));
                    //replace all
                    con2LamRet.get(csVar).forEach(pair -> {
                        handler.addHostSrcHelper(host, pair.first(), pair.second());
                    });
                }
            });
        }
        // handle lambda obj
        if (functions.contains(csVar)
                || function2LamSrcHub.containsKey(csVar)
                || function2RetTarget.containsKey(csVar)
                || unsoundLambdas.contains(csVar)) {
            pts.forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (!lambdaInfo.isPrecise) {
                        // generate mirrored obj
                        if (functions.contains(csVar)) {
                            Obj mirrored = lambdaAnalysis
                                    .getMirroredLambda((MockObj) obj);
                            CSObj csMirrored = csManager.getCSObj(
                                    csObj.getContext(), mirrored);
                            solver.addPointsTo(csVar, csMirrored);
                        }
                    } else {
                        // mirrored precise lambda obj
                        // link lamSrcHub 2 lambda precise arg
                        function2LamSrcHub.get(csVar).forEach(info ->
                                addSrcHub2LamPrcArg(info.isBinary, info.idx,
                                        csObj, info.csSrcHub));
                        // link lambdaObj ret to target
                        function2RetTarget.get(csVar)
                                .forEach(to -> addLambdaRet(csObj, to));
                        // new unsound lambda obj
                        if (lambdaInfo.getSoundness()
                                && unsoundLambdas.contains(csVar)) {
                            lambdaInfo.markUnsound(lambdaAnalysis);
                        }
                    }
                }
            });
        }

        // handle other objs
        if (function2Unsound.containsKey(csVar)
                || base2MayNotCont.containsKey(csVar)
                || collector2PipeOut.containsKey(csVar)) {
            pts.forEach(csObj -> {
                Obj obj = csObj.getObject();
                // handle newObj instance of functional types
                if (function2Unsound.containsKey(csVar)) {
                    if (!helper.isLambdaObj(obj)) {
                        function2Unsound.get(csVar)
                                .forEach(handler::triggerContVarUnsound);
                    }
                }
                // may be other Iterable type,
                // should not handle Iterable.forEach
                if (base2MayNotCont.containsKey(csVar)
                        && obj.isFunctional()
                        && typeSystem.isSubtype(
                        containerConfiger.collectionType, obj.getType())) {
                    base2MayNotCont.get(csVar)
                            .forEach(this::triggerFunctionUnsound);
                }
                // new collector obj
                if (collector2PipeOut.containsKey(csVar)
                        && !soundCollCollectors.contains(obj)) {
                    collector2PipeOut.get(csVar).forEach(pair -> {
                        CSMethod callee = pair.first();
                        CSVar lhs = pair.second();
                        callee.getMethod().getIR().getReturnVars().forEach(ret -> {
                            CSVar csRet = csManager.getCSVar(callee.getContext(), ret);
                            addBackFilteredRetEdgeObj(csRet, lhs);
                        });
                    });
                }
            });
        }
    }

    Transfer getPassEdgeTransfer() {
        return ((edge, input) -> {
            if (lambdaPrecise
                    && edge.kind() == FlowKind.PARAMETER_PASSING
                    && edge.target() instanceof CSVar param
                    && containerConfiger.isPassEdgeTransferCallee(
                            param.getVar().getMethod())
                    && edge.source() instanceof CSVar arg
                    && !handler.isInScope(arg.getVar().getMethod().getDeclaringClass())) {
                PointsToSet result = solver.makePointsToSet();
                for (CSObj csObj : input) {
                    Obj obj = csObj.getObject();
                    if (helper.isLambdaObj(obj)) {
                        LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                        if (lambdaInfo.isPrecise) {
                            result.addObject(csObj);
                        }
                    } else {
                        // wrapped lambda obj
                        result.addObject(csObj);
                    }
                }
                return result;
            } else {
                return input;
            }
        });
    }

    /**
     * list.forEach(x -> fun(x));
     * H.src -> lamPrcArg.0
     */
    private void processCollForEach(Edge<CSCallSite, CSMethod> edge) {
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        Invoke invoke = edge.getCallSite().getCallSite();
        Context callerCtx = edge.getCallee().getContext();
        JMethod callee = edge.getCallee().getMethod();
        if (csBase != null && lambdaPrecise) {
            CSVar csConsumer = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csConsumer);
            // generate and record lambda host src hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[List-ForEach-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);

            // get host on base, link host src to lambda src Hub
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csSrcHub, RetrieveKind.COL_ITEM, csConsumer));
            base2MayNotCont.put(csBase, csConsumer);
            solver.getPointsToSetOf(csBase).forEach(csBaseObj -> {
                if (HostManager.isHost(csBaseObj.getObject())) {
                    Host host = (Host) csBaseObj.getObject().getAllocation();
                    addHost2LamArgHubHelper(host, csSrcHub, RetrieveKind.COL_ITEM, csConsumer);
                } else if (csBaseObj.getObject().isFunctional()) {
                    // Iterable.forEach can be accessed by non-Collection type,
                    // which cannot find precise args for lambda.
                    Obj obj = csBaseObj.getObject();
                    if (containerConfiger.isIterableForEach(callee)
                            && !typeSystem.isSubtype(containerConfiger.collectionType, obj.getType())) {
                        triggerFunctionUnsound(csConsumer);
                    }
                }
            });

            function2LamSrcHub.put(csConsumer, new Src2LamPrcArgInfo(false, 0, csSrcHub));
            solver.getPointsToSetOf(csConsumer).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link host src hub to lambda precise args.
                        addSrcHub2LamPrcArg(false, 0, csObj, csSrcHub);
                    }
                }
            });
        }
    }

    /**
     * map.forEach((k, v) -> fun(k, v));
     * map.k -> lamPrcArg.0
     * map.v -> lamPrcArg.1
     */
    private void processMapForEach(Edge<CSCallSite, CSMethod> edge) {
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        Invoke invoke = edge.getCallSite().getCallSite();
        Context callerCtx = edge.getCallSite().getContext();
        if (csBase != null && lambdaPrecise) {
            CSVar csBiConsumer = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csBiConsumer);
            // generate and record lambda host src hub.
            Var kSrcHub = new Var(invoke.getContainer(),
                    "[MapK-ForEach-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            Var vSrcHub = new Var(invoke.getContainer(),
                    "[MapV-ForEach-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csKSrcHub = csManager.getCSVar(callerCtx, kSrcHub);
            CSVar csVSrcHub = csManager.getCSVar(callerCtx, vSrcHub);

            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csKSrcHub, RetrieveKind.MAP_K, csBiConsumer));
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csVSrcHub, RetrieveKind.MAP_V, csBiConsumer));
            // get host on base, link host source to lambda src hub
            solver.getPointsToSetOf(csBase).forEach(csBaseObj -> {
                if (HostManager.isHost(csBaseObj.getObject())) {
                    Host host = (Host) csBaseObj.getObject().getAllocation();
                    if (host.getKind() == RetrieveKind.MAP_ALL) {
                        addHost2LamArgHubHelper(host, csKSrcHub, RetrieveKind.MAP_K, csBiConsumer);
                        addHost2LamArgHubHelper(host, csVSrcHub, RetrieveKind.MAP_V, csBiConsumer);
                    }
                }
            });

            function2LamSrcHub.put(csBiConsumer, new Src2LamPrcArgInfo(true, 0, csKSrcHub));
            function2LamSrcHub.put(csBiConsumer, new Src2LamPrcArgInfo(true, 1, csVSrcHub));
            solver.getPointsToSetOf(csBiConsumer).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link host src hub to lambda precise args.
                        addSrcHub2LamPrcArg(true, 0, csObj, csKSrcHub);
                        addSrcHub2LamPrcArg(true, 1, csObj, csVSrcHub);
                    }
                }
            });
        }
    }

    /**
     * e.g., list.replaceAll(x - > x.inner()); // H on list
     * H.src -> unaryOp.prcArg.0
     * unaryOp.ret -> H.src
     */
    private void processListReplaceAll(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && lambdaPrecise) {
            CSVar unaryOp = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(unaryOp);
            // generate lambda host source Hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[List-Repl-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);
            // generate lambda ret hub.
            Var retHub = new Var(invoke.getContainer(),
                    "[List-Repl-LamRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csRetHub = csManager.getCSVar(callerCtx, retHub);

            // get host on base
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csSrcHub, RetrieveKind.COL_ITEM, unaryOp));
            con2LamRet.put(csBase, new Pair<>(csRetHub, RetrieveKind.COL_ITEM));
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    // link host source to srcHub
                    addHost2LamArgHubHelper(host, csSrcHub, RetrieveKind.COL_ITEM, unaryOp);
                    // link ret hub to host src
                    handler.addHostSrcHelper(host, csRetHub, RetrieveKind.COL_ITEM);
                }
            });

            // get lambdaObj on unaryOp.
            function2LamSrcHub.put(unaryOp, new Src2LamPrcArgInfo(false, 0, csSrcHub));
            function2RetTarget.put(unaryOp, csRetHub);
            function2Unsound.put(unaryOp, csBase);
            solver.getPointsToSetOf(unaryOp).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link host src hub to lambda precise arg
                        addSrcHub2LamPrcArg(false, 0, csObj, csSrcHub);
                        // link lambda ret to ret hub
                        addLambdaRet(csObj, csRetHub);
                    }
                } else {
                    handler.triggerContVarUnsound(csBase);
                }
            });
        }
        // if do not use precise lambda modeling
        if (csBase != null && !lambdaPrecise) {
            handler.triggerContVarUnsound(csBase);
        }
    }

    /**
     * e.g., map.replaceAll((k, v) -> v.inner()); // H on list
     * H.KSrc -> biFun.prcArg.0
     * H.VSrc -> biFun.prcArg.1
     * biFun.ret -> H.VSrc
     */
    private void processMapReplaceAll(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && lambdaPrecise) {
            CSVar biFunction = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(biFunction);
            // generate lambda host source Hub.
            Var kSrcHub = new Var(invoke.getContainer(),
                    "[MapK-Repl-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csKSrcHub = csManager.getCSVar(callerCtx, kSrcHub);
            Var vSrcHub = new Var(invoke.getContainer(),
                    "[MapV-Repl-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csVSrcHub = csManager.getCSVar(callerCtx, vSrcHub);
            // generate lambda ret hub.
            Var retHub = new Var(invoke.getContainer(),
                    "[Map-Repl-LamRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csRetHub = csManager.getCSVar(callerCtx, retHub);

            // get host on base
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csKSrcHub, RetrieveKind.MAP_K, biFunction));
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csVSrcHub, RetrieveKind.MAP_V, biFunction));
            con2LamRet.put(csBase, new Pair<>(csRetHub, RetrieveKind.MAP_V));
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    // link host source to srcHub
                    addHost2LamArgHubHelper(host, csKSrcHub, RetrieveKind.MAP_K, biFunction);
                    addHost2LamArgHubHelper(host, csVSrcHub, RetrieveKind.MAP_V, biFunction);
                    // link ret hub to host src
                    handler.addHostSrcHelper(host, csRetHub, RetrieveKind.MAP_V);
                }
            });

            // get lambdaObj on biFunction.
            function2LamSrcHub.put(biFunction, new Src2LamPrcArgInfo(true, 0, csKSrcHub));
            function2LamSrcHub.put(biFunction, new Src2LamPrcArgInfo(true, 1, csVSrcHub));
            function2RetTarget.put(biFunction, csRetHub);
            function2Unsound.put(biFunction, csBase);
            solver.getPointsToSetOf(biFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link host src hub to lambda precise arg
                        addSrcHub2LamPrcArg(true, 0, csObj, csKSrcHub);
                        addSrcHub2LamPrcArg(true, 1, csObj, csVSrcHub);
                        // link lambda ret to ret hub
                        addLambdaRet(csObj, csRetHub);
                    }
                } else {
                    handler.triggerContVarUnsound(csBase);
                }
            });
        }
        // if do not use precise lambda modeling
        if (csBase != null && !lambdaPrecise) {
            handler.triggerContVarUnsound(csBase);
        }
    }

    /**
     * e.g., map.merge(k, v, (oldV, newV) -> return fun(oldV, newV)); // H on map
     * v -> biFun.prcArg.1
     * H.VSrc -> biFun.prcArg.0
     * biFun.ret -> H.VSrc
     * Also, k and v are added into H.src by processEntrance.
     * Also, ret var of merge is handled by processExit.
     */
    private void processMapMerge(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && lambdaPrecise) {
            CSVar biFunction = getArg(edge, 2);
            CSVar v = getArg(edge, 1);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(biFunction);
            // generate lambda host source hub
            Var vSrcHub = new Var(invoke.getContainer(),
                    "[MapV-Merge-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csVSrcHub = csManager.getCSVar(callerCtx, vSrcHub);
            // generate lambda ret hub.
            Var retHub = new Var(invoke.getContainer(),
                    "[Map-Merge-LamRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csRetHub = csManager.getCSVar(callerCtx, retHub);

            // get host on base
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csVSrcHub, RetrieveKind.MAP_V, biFunction));
            con2LamRet.put(csBase, new Pair<>(csRetHub, RetrieveKind.MAP_V));
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    // link host source to srcHub
                    addHost2LamArgHubHelper(host, csVSrcHub, RetrieveKind.MAP_V, biFunction);
                    // link ret hub to host src
                    handler.addHostSrcHelper(host, csRetHub, RetrieveKind.MAP_V);
                }
            });

            // get lambdaObj on biFunction
            function2LamSrcHub.put(biFunction, new Src2LamPrcArgInfo(true, 0, csVSrcHub));
            function2LamSrcHub.put(biFunction, new Src2LamPrcArgInfo(true, 1, v));
            function2RetTarget.put(biFunction, csRetHub);
            function2Unsound.put(biFunction, csBase);
            solver.getPointsToSetOf(biFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link host src hub to lambda precise arg
                        addSrcHub2LamPrcArg(true, 0, csObj, csVSrcHub);
                        addSrcHub2LamPrcArg(true, 1, csObj, v);
                        // link lambda ret to ret hub
                        addLambdaRet(csObj, csRetHub);
                    }
                } else {
                    handler.triggerContVarUnsound(csBase);
                }
            });
        }
        // if do not use precise lambda modeling
        if (csBase != null && !lambdaPrecise) {
            handler.triggerContVarUnsound(csBase);
        }
    }

    /**
     * e.g., map.computeIfPresent(key, (k, v) -> return fun(k, v)); // H on map
     *     | map.compute(key, (k, v) -> return fun(k, v)); // H on map
     * key -> biFun.prcArg.0
     * H.VSrc -> biFun.prcArg.1
     * biFun.ret -> H.VSrc.
     * Also, key are added into H.KSrc by processEntrance
     * Also, ret var of compute is handled by processExit
     */
    private void processMapComputeIfPresent(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && lambdaPrecise) {
            CSVar biFunction = getArg(edge, 1);
            CSVar key = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(biFunction);
            // generate lambda host source hub
            Var vSrcHub = new Var(invoke.getContainer(),
                    "[MapV-Merge-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csVSrcHub = csManager.getCSVar(callerCtx, vSrcHub);
            // generate lambda ret hub.
            Var retHub = new Var(invoke.getContainer(),
                    "[Map-Merge-LamRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csRetHub = csManager.getCSVar(callerCtx, retHub);

            // get host on base
            cont2LamSrcHub.put(csBase, new HostSrc2LamArgHubInfo(csVSrcHub, RetrieveKind.MAP_V, biFunction));
            con2LamRet.put(csBase, new Pair<>(csRetHub, RetrieveKind.MAP_V));
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    // link host source to srcHub
                    addHost2LamArgHubHelper(host, csVSrcHub, RetrieveKind.MAP_V, biFunction);
                    // link ret hub to host src
                    handler.addHostSrcHelper(host, csRetHub, RetrieveKind.MAP_V);
                }
            });

            function2LamSrcHub.put(biFunction, new Src2LamPrcArgInfo(true, 1, csVSrcHub));
            function2LamSrcHub.put(biFunction, new Src2LamPrcArgInfo(true, 0, key));
            function2RetTarget.put(biFunction, csRetHub);
            function2Unsound.put(biFunction, csBase);
            // get lambdaObj on biFunction
            solver.getPointsToSetOf(biFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link host src hub to lambda precise arg
                        addSrcHub2LamPrcArg(true, 1, csObj, csVSrcHub);
                        addSrcHub2LamPrcArg(true, 0, csObj, key);
                        // link lambda ret to ret hub
                        addLambdaRet(csObj, csRetHub);
                    }
                }  else {
                    handler.triggerContVarUnsound(csBase);
                }
            });
        }
        // if do not use precise lambda modeling
        if (csBase != null && !lambdaPrecise) {
            handler.triggerContVarUnsound(csBase);
        }
    }


    /**
     * e.g., map.computeIfAbsent(key, k -> return fun(k)); // H on map
     * key -> fun.prcArg.0
     * fun.ret -> H.VSrc.
     * Also, key are added into H.KSrc by processEntrance
     * Also, ret var of computeIfAbsent is handled by processExit.
     */
    private void processMapComputeIfAbsent(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && lambdaPrecise) {
            CSVar function = getArg(edge, 1);
            CSVar key = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(function);
            // generate lambda ret hub.
            Var retHub = new Var(invoke.getContainer(),
                    "[Map-Merge-LamRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csRetHub = csManager.getCSVar(callerCtx, retHub);

            // get host on base
            con2LamRet.put(csBase, new Pair<>(csRetHub, RetrieveKind.MAP_V));
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    Host host = (Host) obj.getAllocation();
                    // link ret hub to host src
                    handler.addHostSrcHelper(host, csRetHub, RetrieveKind.MAP_V);
                }
            });

            function2LamSrcHub.put(function, new Src2LamPrcArgInfo(false, 0, key));
            function2RetTarget.put(function, csRetHub);
            function2Unsound.put(function, csBase);
            // get lambdaObj on biFunction
            solver.getPointsToSetOf(function).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link src hub to lambda precise arg
                        addSrcHub2LamPrcArg(false, 0, csObj, key);
                        // link lambda ret to ret hub
                        addLambdaRet(csObj, csRetHub);
                    }
                } else {
                    handler.triggerContVarUnsound(csBase);
                }
            });
        }
        // if do not use precise lambda modeling
        if (csBase != null && !lambdaPrecise) {
            handler.triggerContVarUnsound(csBase);
        }
    }

    /**
     * handle cont = stm.collect(Collector)
     * if obj in Collector is marked as sound, generate COLLECTION_ITEM host.
     */
    private void processStmCollectorCollect(Edge<CSCallSite, CSMethod> edge) {
        Invoke invoke = edge.getCallSite().getCallSite();
        JMethod caller = invoke.getContainer();
        CSVar stm = getArg(edge, InvokeUtils.BASE);
        Set<CSVar> csLHSs = getLHSS(edge);
        CSMethod callee = edge.getCallee();
        if (stm != null && invoke.getResult() != null) {
            MockObj pipeOutObj = new MockObj(PIPELINE_OUT, invoke,
                                             NullType.NULL, caller, false);
            // if not use stream pattern, add back filtered host.
            if (handler.streamAnalysis == null) {
                callee.getMethod().getIR().getReturnVars().forEach(ret -> {
                    CSVar csRet = csManager.getCSVar(callee.getContext(), ret);
                    csLHSs.forEach(lhs -> {
                        addBackFilteredRetEdgeObj(csRet, lhs);
                    });
                });
                return;
            }

            // if use stream pattern, generating COL_ITEM host
            Host pipeOutHost = hostManager.getHost(pipeOutObj, RetrieveKind.COL_ITEM);
            // add host to lhs–
            csLHSs.forEach(lhs -> solver.addPointsTo(lhs, pipeOutHost.getMockObj()));

            // find label on stream
            handler.stmToHost.put(stm, pipeOutHost);
            solver.getPointsToSetOf(stm).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    handler.addHostSrcHelper(
                            pipeOutHost, l.getSourceHub(), RetrieveKind.COL_ITEM);
                    handler.streamAnalysis.addStrm2Host(l, pipeOutHost);
                    // if l is unsound, mark host as unsound.
                    if (l.isConserv()) {
                        containerAccessAnalysis.markTaint(pipeOutHost);
                    }
                }
            });

            // find collector obj in Collector
            CSVar collector = getArg(edge, 0);
            csLHSs.forEach(lhs -> collector2PipeOut.put(
                    collector, new Pair<>(callee, lhs)));
            solver.getPointsToSetOf(collector).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (!soundCollCollectors.contains(obj)) {
                    callee.getMethod().getIR().getReturnVars().forEach(ret -> {
                        CSVar csRet = csManager.getCSVar(callee.getContext(), ret);
                        csLHSs.forEach(lhs -> {
                            addBackFilteredRetEdgeObj(csRet, lhs);
                        });
                    });
                }
            });
        }
    }

    private void addBackFilteredRetEdgeObj(CSVar ret, CSVar lhs) {
        solver.addPFGEdge(new ShortcutEdge(ret, lhs), unFunctionalTransfer());
    }

    private Transfer unFunctionalTransfer() {
        return (edge, input) -> {
            PointsToSet result = solver.makePointsToSet();
            for (CSObj csObj : input) {
                if (!csObj.getObject().isFunctional()) {
                    result.addObject(csObj);
                }
            }
            return result;
        };
    }

    /**
     * if is lambda-call-edge, and not use lambdaPrecise, and param is of type Map.Entry
     * add a Mock Taint Host on it.
     * @param edge
     */
    private void processMapEntryParam(LambdaCallEdge edge) {
        if (!lambdaPrecise) {
            Context calleeCtx = edge.getCallee().getContext();
            JMethod callee = edge.getCallee().getMethod();
            callee.getIR().getParams().forEach(param -> {
                if (typeSystem.isSubtype(mapEntryType, param.getType())) {
                    CSVar csParam = csManager.getCSVar(calleeCtx, param);
                    addMapEntryParamUnsoundHost(csParam, edge);
                }
            });
        }
    }

    private void addMapEntryParamUnsoundHost(CSVar param, LambdaCallEdge edge) {
        JMethod method = param.getVar().getMethod();
        MockObj customContainerObj = new MockObj(LAM_MAPE_PARAM_UNSOUND,
                edge, NullType.NULL, method, false);
        EnumSet.of(RetrieveKind.MAP_K, RetrieveKind.MAP_V, RetrieveKind.MAP_E,
                RetrieveKind.MAP_ALL, RetrieveKind.COL_ITEM).forEach(kind -> {
            Host customHost = hostManager.getHost(customContainerObj, kind);
            solver.addPointsTo(param, param.getContext(), customHost.getMockObj());
            containerAccessAnalysis.markTaint(customHost);
        });
    }

    private void generateMirroredLambdaObj(CSVar csFunction) {
        functions.add(csFunction);
        Set<CSObj> mirroredObjs = Sets.newSet();
        solver.getPointsToSetOf(csFunction).forEach(csLambdaObj -> {
            Context lambdaCtx = csLambdaObj.getContext();
            Obj obj = csLambdaObj.getObject();
            if (helper.isLambdaObj(obj)) {
                LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                if (!lambdaInfo.isPrecise) {
                    Obj mirroredObj = lambdaAnalysis.getMirroredLambda((MockObj) obj);
                    CSObj csMirroredObj = csManager.getCSObj(lambdaCtx, mirroredObj);
                    mirroredObjs.add(csMirroredObj);
                }
            }
        });
        // add mirrored lambdaObj into csFuntion
        mirroredObjs.forEach(csMirroredObj -> {
            solver.addPointsTo(csFunction, csMirroredObj);
        });
    }

    private void addHost2LamArgHubHelper(Host host, CSVar csSrcHub,
                                         RetrieveKind forEachKind, CSVar csFunction) {
        Host actualHost = hostManager.addHostTarget(host, csSrcHub, forEachKind);
        if (actualHost != null) {
            if (!actualHost.isTainted()) {
                containerAccessAnalysis.onNewHostTarget(actualHost, csSrcHub);
                containerAccessAnalysis.addHost2Function(actualHost, csFunction);
            } else {
                triggerFunctionUnsound(csFunction);
            }
        }
        //transfer MAP_ALL to argHub
        if (forEachKind == RetrieveKind.COL_ITEM
                && host.getKind() == RetrieveKind.MAP_ALL) {
            solver.addPointsTo(csSrcHub, host.getMockObj());
        }
    }

    private void addSrcHub2LamPrcArg(boolean isBinary, int idx,
                                     CSObj csLambdaObj, CSVar csSrcHub) {
        Obj obj = csLambdaObj.getObject();
        LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
        List<Var> preciseArgs = lambdaAnalysis
                .getLambdaPreciseArgs(lambdaInfo, isBinary);
        CSVar csPreciseArg = csManager.getCSVar(
                csLambdaObj.getContext(), preciseArgs.get(idx));
        Var mockArg = lambdaInfo.getMockArg(idx);
        this.lamArgHubLMockArgCpt.put(csSrcHub.getVar(), mockArg);
        solver.addPFGEdge(new ShortcutEdge(csSrcHub, csPreciseArg), csPreciseArg.getType());
    }

    private void addLambdaRet(CSObj csLambdaObj, CSVar to) {
        Context lambdaContext = csLambdaObj.getContext();
        MockObj lambdaObj = (MockObj) csLambdaObj.getObject();
        LambdaInfo lambdaInfo = (LambdaInfo) lambdaObj.getAllocation();
        // add lambda ret to new label's source
        Var lambdaRet = lambdaAnalysis.getLambdaRet(lambdaInfo);
        CSVar csLambdaRet = csManager.getCSVar(lambdaContext, lambdaRet);
        solver.addPFGEdge(new ShortcutEdge(csLambdaRet, to), to.getType());
    }

    void triggerFunctionUnsound(CSVar csFunction) {
        unsoundLambdas.add(csFunction);
        solver.getPointsToSetOf(csFunction).forEach(csObj -> {
            Obj obj = csObj.getObject();
            if (helper.isLambdaObj(obj)) {
                LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                if (lambdaInfo.isPrecise && lambdaInfo.getSoundness()) {
                    lambdaInfo.markUnsound(lambdaAnalysis);
                }
            }
        });
    }

    /**
     * use helper to get arg/return at different kinds of call-edge
     */
    private Set<CSVar> getLHSS(Edge<CSCallSite, CSMethod> edge) {
        return helper.getPotentialLHS(edge);
    }
    private CSVar getArg(Edge<CSCallSite, CSMethod> edge, int index) {
        return helper.getCallSiteArg(edge, index);
    }

}
