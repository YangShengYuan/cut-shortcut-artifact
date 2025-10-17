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

import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.core.solver.Transfer;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaInfo;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.TopType;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.util.List;
import java.util.Set;

public class StreamLambdaAnalysis {

    /**
     * utils
     */
    private final Solver solver;
    private final CSManager csManager;
    private final LambdaAnalysis lambdaAnalysis;
    private final StreamAnalysis streamAnalysis;
    private final StreamHandler handler;
    private final CSCHelper helper;
    private final StreamConfiger streamConfiger;
    final MultiMap<Var, Var> lamArgHubLMockArgCpt = Maps.newMultiMap();

    /**
     * mark if the lambda need precise modeling
     */
    final boolean lambdaPrecise;

    /**
     * records
     */
    record Src2LamPrcArgInfo(boolean isBinary, int idx, CSVar csSrcHub) {
    }

    /**
     * for later arrived strms 
     */
    // e.g. stm.forEach(consumer); record stm -> argHub
    // e.g., [i]: r = stm.reduce(start, BiFunction); record stm -> i
    private final MultiMap<CSVar, CSCallSite> stm2FunctionExitInvo = Maps.newMultiMap();
    // stm -> lambda strm source hub
    private final MultiMap<CSVar, CSVar> stm2LamStrmSrcHub = Maps.newMultiMap();
    // soundness control for lambda Obj.
    private final MultiMap<CSVar, CSVar> stm2Function = Maps.newMultiMap();
    private final MultiMap<CSVar, CSVar> stm2LambdaRet = Maps.newMultiMap();

    /**
     * for later arrived functional Obj
     */
    // record consumer -> argHub
    private final MultiMap<CSVar, Src2LamPrcArgInfo> function2LamSrcHub = Maps.newMultiMap();
    // e.g. temp = stm.map(mapper); record mapper -> new L's source
    private final MultiMap<CSVar, Object> function2RetTarget = Maps.newMultiMap();
    // e.g. temp = stm.flatMap(function); record function -> new L
    private final MultiMap<CSVar, Strm> function2SuccL = Maps.newMultiMap();
    // functions may contain instance of functional types
    private final MultiMap<CSVar, Object> function2Unsound = Maps.newMultiMap();
    // record functions need to generate mirrored obj
    private final Set<CSVar> functions = Sets.newSet();
    // functions marked as unsound
    private final Set<CSVar> unsoundLambdas = Sets.newSet();

    /**
     * statistics
     */
    final Set<Var> funcOutputLHS = Sets.newSet();

    StreamLambdaAnalysis(boolean lambdaPrecise, Solver solver,
                         CSManager csManager, LambdaAnalysis lambdaAnalysis,
                         StreamAnalysis streamAnalysis, StreamHandler handler,
                         StreamConfiger configer, CSCHelper helper) {
        this.lambdaPrecise = lambdaPrecise;
        this.solver = solver;
        this.csManager = csManager;
        this.lambdaAnalysis = lambdaAnalysis;
        this.streamAnalysis = streamAnalysis;
        this.handler = handler;
        this.streamConfiger = configer;
        this.helper = helper;
    }

    void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        JMethod callee = edge.getCallee().getMethod();
        // primitive creation
        if (streamConfiger.isPrimCreation(callee)) {
            handlePrimCreation(edge);
        }
        // map
        if (streamConfiger.isMap(callee)) {
            handleMap(edge);
        }
        // flatMap
        if (streamConfiger.isFlatMap(callee)) {
            handleFlatMap(edge);
        }
        // forEach
        if (streamConfiger.isForEach(callee)) {
            handleForEach(edge);
        }
        // optional.OrElseGet()
        if (streamConfiger.isOrElse(callee)) {
            handleOrElse(edge);
        }
        // collect
        if (streamConfiger.isCollect(callee)) {
            handleCollect(edge);
        }
        // simpleReduce
        if (streamConfiger.isSimpleReduce(callee)) {
            handleSimpleReduce(edge);
        }
        // reduce out
        if (streamConfiger.isReduceOut(callee)) {
            handleReduceOut(edge);
        }
        // reduce out parallel
        if (streamConfiger.isReduceOutPrl(callee)) {
            handleReduceOutPrl(edge);
        }
        // iterate
        if (streamConfiger.isIterate(callee)) {
            handleIterate(edge);
        }
        // generate
        if (streamConfiger.isGenerate(callee)) {
            handleGenerate(edge);
        }
    }

    void onNewPointsToCSObj(CSVar csVar, PointsToSet pts) {
        if (!lambdaPrecise) {
            return;
        }
        if (stm2FunctionExitInvo.containsKey(csVar)
                || stm2LamStrmSrcHub.containsKey(csVar)
                || stm2Function.containsKey(csVar)
                || stm2LambdaRet.containsKey(csVar)
                || functions.contains(csVar)
                || function2LamSrcHub.containsKey(csVar)
                || function2RetTarget.containsKey(csVar)
                || function2SuccL.containsKey(csVar)
                || unsoundLambdas.contains(csVar)
                || function2Unsound.containsKey(csVar)) {
            pts.forEach(csObj -> {
                Obj obj = csObj.getObject();
                // new strm
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    // functional exit
                    stm2FunctionExitInvo.get(csVar).forEach(invo -> {
                        if (l.isConserv()) {
                            handler.undoCallSite(invo);
                        } else {
                            handler.pipeRelatedOutputCallSites.put(l, invo);
                        }
                    });
                    // forEach
                    stm2LamStrmSrcHub.get(csVar).forEach(argHub ->
                            streamAnalysis.addStrmTarget(l, argHub));
                    // soundness control for lambda Obj
                    stm2Function.get(csVar).forEach(function -> {
                        streamAnalysis.addStrm2Function(l, function);
                        if (l.isConserv()) {
                            triggerFunctionUnsound(function);
                        }
                    });
                    // for lambda ret hub
                    stm2LambdaRet.get(csVar).forEach(retHub -> {
                        streamAnalysis.addStrmSource(l, retHub);
                    });
                }
                // new lambda expression obj
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (!lambdaInfo.isPrecise) {
                        // generate mirrored obj
                        if (functions.contains(csVar)) {
                            Obj mirroredObj = lambdaAnalysis.getMirroredLambda((MockObj) obj);
                            CSObj csMirroredObj = csManager.getCSObj(csObj.getContext(), mirroredObj);
                            solver.addPointsTo(csVar, csMirroredObj);
                        }
                    } else { // mirrored precise lambdaObj
                        // link invoArgHub 2 lambda precise arg
                        function2LamSrcHub.get(csVar).forEach(info -> {
                            addSrcHub2LamPrcArg(
                                    info.isBinary, info.idx, csObj, info.csSrcHub);
                        });
                        // link lambdaObj ret to target
                        function2RetTarget.get(csVar).forEach(o -> {
                            addLambdaRet(csObj, o);
                        });
                        // add LambdaObj ret as strm predecessor
                        function2SuccL.get(csVar).forEach(succL -> {
                            addLambdaRetAsPred(csObj, succL);
                        });
                        // new unsound lambda obj
                        if (lambdaInfo.getSoundness()
                                && unsoundLambdas.contains(csVar)) {
                            lambdaInfo.markUnsound(lambdaAnalysis);
                        }
                    }
                }
                // newObj instance of functional types
                if (!helper.isLambdaObj(obj)) {
                    function2Unsound.get(csVar).forEach(o -> {
                        if (o instanceof Strm l) {
                            streamAnalysis.markUnsound(l);
                        } else if (o instanceof CSCallSite invoke) {
                            handler.undoCallSite(invoke);
                        } else if (o instanceof CSVar function) {
                            triggerFunctionUnsound(function);
                        }
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
                    && streamConfiger.isPassEdgeTransferCallee(
                            param.getVar().getMethod())
                    && edge.source() instanceof CSVar arg
                    && !handler.isInScope(
                            arg.getVar().getMethod().getDeclaringClass())) {
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
     * stm = Stream.iterate(seed, unaryOp)
     * seed -> new L.src
     * seed -> unaryOp.prcArg
     * unaryOp.ret -> new L.sec
     * unaryOp.ret -> unaryOp.prcArg
     */
    private void handleIterate(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        // generate new pipeline strm
        Strm newL = handler.generatePipelineStrm(edge);
        // mark LHS with generated strm
        handler.addStrmToLhsHelper(edge, newL);
        if (invoke.getResult() != null && lambdaPrecise) {
            CSVar seed = getArg(edge, 0);
            CSVar csUnaryOp = getArg(edge, 1);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csUnaryOp);
            // generate lambda source hub
            Var opRetHub = new Var(invoke.getContainer(),
                    "[Stream-Iterate-OpRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csOpRetHub = csManager.getCSVar(callerCtx, opRetHub);
            // seed -> new L.src
            streamAnalysis.addStrmSource(newL, seed);
            // unaryOp.ret -> new L.sec
            streamAnalysis.addStrmSource(newL, csOpRetHub);

            function2LamSrcHub.put(csUnaryOp, new Src2LamPrcArgInfo(false, 0, seed));
            function2LamSrcHub.put(csUnaryOp, new Src2LamPrcArgInfo(false, 0, csOpRetHub));
            function2RetTarget.put(csUnaryOp, csOpRetHub);
            function2Unsound.put(csUnaryOp, newL);
            function2Unsound.put(csUnaryOp, csUnaryOp);
            solver.getPointsToSetOf(csUnaryOp).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // unaryOp.ret -> opRetHub
                        addLambdaRet(csObj, csOpRetHub);
                        // seed -> unaryOp.prcArg
                        addSrcHub2LamPrcArg(false, 0, csObj, seed);
                        // opRetHub -> unaryOp.prcArg
                        addSrcHub2LamPrcArg(false, 0, csObj, csOpRetHub);
                    }
                } else {
                    streamAnalysis.markUnsound(newL);
                    triggerFunctionUnsound(csUnaryOp);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            streamAnalysis.markUnsound(newL);
        }
    }

    /**
     * stm = Stream.generate(supplier)
     * supplier.ret -> new L.src
     */
    private void handleGenerate(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Invoke invoke = callSite.getCallSite();
        // generate new pipeline strm
        Strm newL = handler.generatePipelineStrm(edge);
        // mark LHS with the generated strm
        handler.addStrmToLhsHelper(edge, newL);
        if (invoke.getResult() != null && lambdaPrecise) {
            // get supplier
            CSVar csSupplier = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csSupplier);
            function2RetTarget.put(csSupplier, newL);
            function2Unsound.put(csSupplier, newL);
            solver.getPointsToSetOf(csSupplier).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link lambdaRet to new L.src
                        addLambdaRet(csObj, newL);
                    }
                } else {
                    streamAnalysis.markUnsound(newL);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            streamAnalysis.markUnsound(newL);
        }
    }


    /**
     * in addition to stream elements, result should also
     * contain the return value of lambda expression
     * e.g., r = optional.orElseGet(() -> new X());
     * supp.ret -> r
     */
    private void handleOrElse(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        CSVar csStm = getArg(edge, InvokeUtils.BASE);
        if (csStm != null && lambdaPrecise) {
            CSVar csSupplier = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csSupplier);
            // generate new pipeline strm
            Set<CSVar> csTargets = getLHSS(edge);
            csTargets.forEach(target -> {
                function2RetTarget.put(csSupplier, target);
                function2Unsound.put(csSupplier, callSite);
                solver.getPointsToSetOf(csSupplier).forEach(csObj -> {
                    Obj obj = csObj.getObject();
                    if (helper.isLambdaObj(obj)) {
                        LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                        if (lambdaInfo.isPrecise) {
                            // add strms at lambda-ret to result of invocation.
                            addLambdaRet(csObj, target);
                        }
                    } else {
                        handler.undoCallSite(callSite);
                    }
                });
            });
        }
        // if do not use precise lambda modeling
        if (csStm != null && !lambdaPrecise) {
            handler.undoCallSite(callSite);
        }
    }

    /**
     * objStm = intStm.mapToObj(Integer::valueOf);
     * new L on objStm
     * function.ret -> L's src
     */
    private void handlePrimCreation(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        // generate new pipeline strm
        Strm newL = handler.generatePipelineStrm(edge);
        // mark LHS with generated strm
        handler.addStrmToLhsHelper(edge, newL);
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            // get csFunction
            CSVar csFunction = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csFunction);
            // mark the mirrored lambdaObj as unsound.
            // since we cannot find the preciseArg for it.
            triggerFunctionUnsound(csFunction);

            function2RetTarget.put(csFunction, newL);
            function2Unsound.put(csFunction, newL);
            // add lambda ret to new strm's source
            solver.getPointsToSetOf(csFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        addLambdaRet(csObj, newL);
                    }
                } else {
                    streamAnalysis.markUnsound(newL);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            streamAnalysis.markUnsound(newL);
        }
    }

    /**
     *  e.g., temp = stm.map(o->o.getField()); // L on stm.
     *  temp <- new L'
     *  L.src -> lamPrcArg.0
     *  lamRet -> L'.src
     */
    private void handleMap(Edge<CSCallSite,  CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        // generate new pipeline strm
        Strm newL = handler.generatePipelineStrm(edge);
        // mark LHS with generated strm
        handler.addStrmToLhsHelper(edge, newL);
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            CSVar csFunction = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csFunction);
            // generate lambda strm source Hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-Map-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);

            // get old strm on base, link old strm's src to strm src hub
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            stm2Function.put(csBase, csFunction);
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm oldL = (Strm) obj.getAllocation();
                    streamAnalysis.addStrmTarget(oldL, csSrcHub);
                    // record old strm -> function
                    streamAnalysis.addStrm2Function(oldL, csFunction);
                    if (oldL.isConserv()) {
                        triggerFunctionUnsound(csFunction);
                    }
                }
            });

            function2LamSrcHub.put(csFunction,
                    new Src2LamPrcArgInfo(false, 0, csSrcHub));
            function2RetTarget.put(csFunction, newL);
            function2Unsound.put(csFunction, newL);
            solver.getPointsToSetOf(csFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link strm src hub to lambda precise args
                        addSrcHub2LamPrcArg(false, 0, csObj, csSrcHub);
                        // link lambdaRet to new strm's source
                        addLambdaRet(csObj, newL);
                    }
                } else {
                    streamAnalysis.markUnsound(newL);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            streamAnalysis.markUnsound(newL);
        }
    }

    /**
     * e.g., temp = stm.flatMap(l -> l.stream()); // L on stm
     * temp <- new L'
     * L.src -> lamPrcArg.0
     * lamRet -> L'.pred
     */
    private void handleFlatMap(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerContext = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        // generate new pipeline strm
        Strm newL = handler.generatePipelineStrm(edge);
        // mark LHS with generated strm
        handler.addStrmToLhsHelper(edge, newL);
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            CSVar csFunction = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csFunction);
            // generate and record strm src hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-FlatMap-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerContext, srcHub);

            stm2Function.put(csBase, csFunction);
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            // get old strm on base, link old strm's src to strm src hub.
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm oldL = (Strm) obj.getAllocation();
                    streamAnalysis.addStrmTarget(oldL, csSrcHub);
                    // record old strm -> function
                    streamAnalysis.addStrm2Function(oldL, csFunction);
                    if (oldL.isConserv()) {
                        triggerFunctionUnsound(csFunction);
                    }
                }
            });

            function2LamSrcHub.put(csFunction,
                    new Src2LamPrcArgInfo(false, 0, csSrcHub));
            function2SuccL.put(csFunction, newL);
            function2Unsound.put(csFunction, newL);
            solver.getPointsToSetOf(csFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link strm src hub to lambda precise args
                        addSrcHub2LamPrcArg(false, 0, csObj, csSrcHub);
                        // add strms at lambda-ret as predecessors to new L.
                        addLambdaRetAsPred(csObj, newL);
                    }
                } else {
                    streamAnalysis.markUnsound(newL);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            streamAnalysis.markUnsound(newL);
        }
    }

    /**
     * e.g., stm.forEach(consumer) // L on stm
     * L.src -> lamPrcArg.0
     */
    private void handleForEach(Edge<CSCallSite, CSMethod> edge) {
        Invoke invoke = edge.getCallSite().getCallSite(); // [cal-site 2]
        Context callerContext = edge.getCallSite().getContext();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null && lambdaPrecise) {
            CSVar csConsumer = getArg(edge, 0);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csConsumer);
            // generate and record strm src hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-ForEach-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerContext, srcHub);

            stm2Function.put(csBase, csConsumer);
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            // get strm on base, link src to strm src hub.
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    streamAnalysis.addStrmTarget(l, csSrcHub);
                    // record l -> consumer
                    streamAnalysis.addStrm2Function(l, csConsumer);
                    if (l.isConserv()) {
                        triggerFunctionUnsound(csConsumer);
                    }
                }
            });

            function2LamSrcHub.put(csConsumer,
                    new Src2LamPrcArgInfo(false, 0, csSrcHub));
            solver.getPointsToSetOf(csConsumer).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link strm src hub to lambda precise args
                        addSrcHub2LamPrcArg(false, 0, csObj, csSrcHub);
                    }
                }
            });
        }
    }

    /**
     * e.g., temp = stm.reduce((a,b) -> (a < b) ? a : b); // L on stm
     * temp <- new L';
     * L.src -> L'.src | lamPrcArg.0 | lamPrc.Arg.1
     * lamRet -> L'.src | lamPrc.Arg.0
     */
    private void handleSimpleReduce(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        // generate new pipeline strm
        Strm newL = handler.generatePipelineStrm(edge);
        // mark LHS with generated strm
        handler.addStrmToLhsHelper(edge, newL);
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            CSVar csBiFunction = getArg(edge, 0);
            // generate mirrored lambdaObj;
            generateMirroredLambdaObj(csBiFunction);
            // generate and record strm source Hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-SimpleReduce-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);
            // generate lambda ret hub
            Var retHub = new Var(invoke.getContainer(),
                    "[Stream-SimpleReduce-LamRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csRetHub = csManager.getCSVar(callerCtx, retHub);
            // link return hub to strm source.
            streamAnalysis.addStrmSource(newL, csRetHub);

            // old strm's src -> new strm's src
            handler.var2PipeInput.put(csBase, newL);
            handler.addStrmPredVar(newL, csBase);

            // get old strm on base, link old strm's src to strm src hub
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            stm2Function.put(csBase, csBiFunction);
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm oldL = (Strm) obj.getAllocation();
                    streamAnalysis.addStrmTarget(oldL, csSrcHub);
                    // record old strm -> function
                    streamAnalysis.addStrm2Function(oldL, csBiFunction);
                    if (oldL.isConserv()) {
                        triggerFunctionUnsound(csBiFunction);
                    }
                }
            });

            function2LamSrcHub.put(csBiFunction, new Src2LamPrcArgInfo(true, 0, csSrcHub));
            function2LamSrcHub.put(csBiFunction, new Src2LamPrcArgInfo(true, 1, csSrcHub));
            function2LamSrcHub.put(csBiFunction, new Src2LamPrcArgInfo(true, 0, csRetHub));
            function2RetTarget.put(csBiFunction, csRetHub);
            function2Unsound.put(csBiFunction, newL);
            function2Unsound.put(csBiFunction, csBiFunction);
            solver.getPointsToSetOf(csBiFunction).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // link strm src hub to lambda prcArgs
                        addSrcHub2LamPrcArg(true, 0, csObj, csSrcHub);
                        addSrcHub2LamPrcArg(true, 1, csObj, csSrcHub);
                        // link retHub to lambda prcArgs
                        addSrcHub2LamPrcArg(true, 0, csObj, csRetHub);
                        // link lambdaRet to retHub
                        addLambdaRet(csObj, csRetHub);
                    }
                } else {
                    streamAnalysis.markUnsound(newL);
                    triggerFunctionUnsound(csBiFunction);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            streamAnalysis.markUnsound(newL);
        }
    }

    /**
     * Object r = stm.reduce(start, accu) // L on stm
     * L.src -> accu's lamPrcArg.1
     * start -> r | accu's lamPrcArg.0
     * accu's lamRet -> r | accu's lamPrcArg.0
     */
    private void handleReduceOut(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            funcOutputLHS.add(invoke.getResult());
            // get invoke related csVars
            CSVar csStart = getArg(edge, 0);
            CSVar csAccu = getArg(edge, 1);
            Set<CSVar> csLHSs = getLHSS(edge);
            // generate mirrored lambdaObj
            generateMirroredLambdaObj(csAccu);
            // generate strm src hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-Reduce-Out-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);
            // generate accuArg0Hub
            Var accuRetHub = new Var(invoke.getContainer(),
                    "[Stream-Reduce-Out-AccuRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csAccuRetHub = csManager.getCSVar(callerCtx, accuRetHub);
            // get result to r.
            csLHSs.forEach(csLHS -> {
                // retHub -> r
                solver.addPFGEdge(new ShortcutEdge(csAccuRetHub, csLHS));
                // start -> r
                solver.addPFGEdge(new ShortcutEdge(csStart, csLHS));
            });

            stm2FunctionExitInvo.put(csBase, callSite);
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            stm2Function.put(csBase, csAccu);
            // get strm on base, link source to strm src hub.
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    // record l -> function
                    streamAnalysis.addStrm2Function(l, csAccu);
                    // soundness control
                    if (l.isConserv()) {
                        // for cut edge
                        handler.undoCallSite(callSite);
                        // for lambda preciseArgs
                        triggerFunctionUnsound(csAccu);
                    } else {
                        handler.pipeRelatedOutputCallSites.put(l, callSite);
                    }
                    streamAnalysis.addStrmTarget(l, csSrcHub);
                }
            });

            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 1, csSrcHub));
            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 0, csStart));
            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 0, csAccuRetHub));
            function2RetTarget.put(csAccu, csAccuRetHub);
            function2Unsound.put(csAccu, callSite);
            function2Unsound.put(csAccu, csAccu);
            solver.getPointsToSetOf(csAccu).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // L.src -> accu's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 1, csObj, csSrcHub);
                        // start -> accu's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csStart);
                        // accuRetHub to accu's lambda prcArgs.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csAccuRetHub);
                        // accu's lamRet -> AccuRetHub
                        addLambdaRet(csObj, csAccuRetHub);
                    }
                } else { // undo cut return edge for this call-site
                    handler.undoCallSite(callSite);
                    triggerFunctionUnsound(csAccu);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            handler.undoCallSite(callSite);
        }
    }

    /**
     * Object r = stm.reduce(start, accu, comb) // L on stm
     * L.src -> accu's lamPrcArg.1
     * start -> r | accu's lamPrcArg.0 | comb's lamPrcArg.0 | comb's lamPrcArg.1
     * accu's lamRet -> r | accu's lamPrcArg.0 | comb's lamPrcArg.0 | comb's lamPrcArg.1
     * comb's lamRet -> r | accu's lamPrcArg.0 | comb's lamPrcArg.0 | comb's lamPrcArg.1
     */
    private void handleReduceOutPrl(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Context callerCtx = callSite.getContext();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            funcOutputLHS.add(invoke.getResult());
            // get invoke related csVars
            CSVar csStart = getArg(edge, 0);
            CSVar csAccu = getArg(edge, 1);
            CSVar csComb = getArg(edge, 2);
            Set<CSVar> csLHSs = getLHSS(edge);
            //generate mirrored lambdaObj
            generateMirroredLambdaObj(csAccu);
            generateMirroredLambdaObj(csComb);
            // generate strm src hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-Reduce-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);
            // generate accu/comb arg hubs
            Var accuRetHub = new Var(invoke.getContainer(),
                    "[Stream-Reduce-Out-Paral-AccuRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csAccuRetHub = csManager.getCSVar(callerCtx, accuRetHub);
            Var combRetHub = new Var(invoke.getContainer(),
                    "[Stream-Reduce-Out-Paral-CombRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csCombRetHub = csManager.getCSVar(callerCtx, combRetHub);

            // get result at lhs
            csLHSs.forEach(lhs -> {
                solver.addPFGEdge(new ShortcutEdge(csStart, lhs));
                solver.addPFGEdge(new ShortcutEdge(csAccuRetHub, lhs));
                solver.addPFGEdge(new ShortcutEdge(csCombRetHub, lhs));
            });

            stm2FunctionExitInvo.put(csBase, callSite);
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            stm2Function.put(csBase, csAccu);
            // get strm on base, link source to strm src hub.
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    // record l -> function
                    streamAnalysis.addStrm2Function(l, csAccu);
                    // soundness control
                    if (l.isConserv()) {
                        // for cut edge
                        handler.undoCallSite(callSite);
                        // for lambda preciseArgs
                        triggerFunctionUnsound(csAccu);
                    } else {
                        handler.pipeRelatedOutputCallSites.put(l, callSite);
                    }
                    streamAnalysis.addStrmTarget(l, csSrcHub);
                }
            });

            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 1, csSrcHub));
            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 0, csStart));
            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 0, csAccuRetHub));
            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 0, csCombRetHub));
            function2RetTarget.put(csAccu, csAccuRetHub);
            function2Unsound.put(csAccu, callSite);
            function2Unsound.put(csAccu, csAccu);
            function2Unsound.put(csAccu, csComb);
            solver.getPointsToSetOf(csAccu).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // L.src -> accu's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 1, csObj, csSrcHub);
                        // start -> accu's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csStart);
                        // accu's retHub -> accu's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csAccuRetHub);
                        // comb's retHub -> accu's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csCombRetHub);
                        // accu's lambdaRet -> retHub
                        addLambdaRet(csObj, csAccuRetHub);
                    }
                } else { // undo cut return edge for this call-site
                    handler.undoCallSite(callSite);
                    triggerFunctionUnsound(csAccu);
                    triggerFunctionUnsound(csComb);
                }
            });

            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 0, csStart));
            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 1, csStart));
            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 0, csAccuRetHub));
            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 1, csAccuRetHub));
            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 0, csCombRetHub));
            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 1, csCombRetHub));
            function2RetTarget.put(csComb, csCombRetHub);
            function2Unsound.put(csComb, callSite);
            function2Unsound.put(csComb, csComb);
            function2Unsound.put(csComb, csAccu);
            solver.getPointsToSetOf(csComb).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // start -> comb's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csStart);
                        // start -> comb's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 1, csObj, csStart);
                        // accu's rethub -> comb's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csAccuRetHub);
                        // accu's rethub -> comb's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 1, csObj, csAccuRetHub);
                        // comb's rethub -> comb's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csCombRetHub);
                        // comb's rethub -> comb's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 1, csObj, csCombRetHub);
                        // comb's lamRet -> rethub
                        addLambdaRet(csObj, csCombRetHub);
                    }
                } else { // undo cut return edge for this call-site
                    handler.undoCallSite(callSite);
                    triggerFunctionUnsound(csAccu);
                    triggerFunctionUnsound(csComb);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            handler.undoCallSite(callSite);
        }
    }

    /**
     * r = stm.collect(supp, accu, comb); // L on stm
     * L.src -> accu's lamPrcArg.1
     * supp's lamRet -> r | accu's lamPrcArg.0 | comb's lamPrcArg.0 | comb's lamPrcArg.1
     */
    private void handleCollect(Edge<CSCallSite, CSMethod> edge) {
        CSCallSite callSite = edge.getCallSite();
        Invoke invoke = callSite.getCallSite();
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        Context callerCtx = callSite.getContext();
        if (csBase != null
                && invoke.getResult() != null
                && lambdaPrecise) {
            // record func op out
            funcOutputLHS.add(invoke.getResult());
            // get invoke related csVars
            CSVar csSupp = getArg(edge, 0);
            CSVar csAccu = getArg(edge, 1);
            CSVar csComb = getArg(edge, 2);
            Set<CSVar> csLHSs = getLHSS(edge);
            //generate mirrored lambdaObj
            generateMirroredLambdaObj(csSupp);
            generateMirroredLambdaObj(csAccu);
            generateMirroredLambdaObj(csComb);
            // generate strm src hub.
            Var srcHub = new Var(invoke.getContainer(),
                    "[Stream-Collect-SrcHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSrcHub = csManager.getCSVar(callerCtx, srcHub);
            // generate accu/comb arg hubs
            Var suppRetHub = new Var(invoke.getContainer(),
                    "[Stream-Collect-SuppRetHub]-{" + invoke + "}", TopType.Top, -1);
            CSVar csSuppRetHub = csManager.getCSVar(callerCtx, suppRetHub);
            csLHSs.forEach(lhs -> solver.addPFGEdge(new ShortcutEdge(csSuppRetHub, lhs)));

            stm2FunctionExitInvo.put(csBase, callSite);
            stm2LamStrmSrcHub.put(csBase, csSrcHub);
            stm2Function.put(csBase, csAccu);
            // get strm on base, link source to strm src hub.
            solver.getPointsToSetOf(csBase).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isStrm(obj)) {
                    Strm l = (Strm) obj.getAllocation();
                    // record l -> function
                    streamAnalysis.addStrm2Function(l, csAccu);
                    // soundness control
                    if (l.isConserv()) {
                        // for cut edge
                        handler.undoCallSite(callSite);
                        // for lambda preciseArgs
                        triggerFunctionUnsound(csAccu);
                    } else {
                        handler.pipeRelatedOutputCallSites.put(l, callSite);
                    }
                    streamAnalysis.addStrmTarget(l, csSrcHub);
                }
            });

            function2RetTarget.put(csSupp, csSuppRetHub);
            function2Unsound.put(csSupp, callSite);
            function2Unsound.put(csSupp, csAccu);
            function2Unsound.put(csSupp, csComb);
            solver.getPointsToSetOf(csSupp).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // supp's lamRet -> supp ret hub
                        addLambdaRet(csObj, csSuppRetHub);
                    }
                } else {
                    handler.undoCallSite(callSite);
                    triggerFunctionUnsound(csAccu);
                    triggerFunctionUnsound(csComb);
                }
            });

            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 1, csSrcHub));
            function2LamSrcHub.put(csAccu, new Src2LamPrcArgInfo(true, 0, csSuppRetHub));
            function2Unsound.put(csAccu, callSite);
            solver.getPointsToSetOf(csAccu).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // L.src -> accu's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 1, csObj, csSrcHub);
                        // supp's lamRet -> accu's lamPrcArg.0
                        addSrcHub2LamPrcArg(true, 0, csObj, csSuppRetHub);
                    }
                } else {
                    handler.undoCallSite(callSite);
                }
            });

            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 0, csSuppRetHub));
            function2LamSrcHub.put(csComb, new Src2LamPrcArgInfo(true, 1, csSuppRetHub));
            function2Unsound.put(csComb, callSite);
            solver.getPointsToSetOf(csComb).forEach(csObj -> {
                Obj obj = csObj.getObject();
                if (helper.isLambdaObj(obj)) {
                    LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
                    if (lambdaInfo.isPrecise) {
                        // supp's lamRet -> comb's lamPrcArg.0 | comb's lamPrcArg.1
                        addSrcHub2LamPrcArg(true, 0, csObj, csSuppRetHub);
                        addSrcHub2LamPrcArg(true, 1, csObj, csSuppRetHub);
                    }
                } else {
                    handler.undoCallSite(callSite);
                }
            });
        }
        // if do not use precise lambda modeling
        if (!lambdaPrecise) {
            handler.undoCallSite(callSite);
        }
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

    private void addSrcHub2LamPrcArg(boolean isBinary, int idx,
                                     CSObj csLambdaObj, CSVar csSrcHub) {
        Obj obj = csLambdaObj.getObject();
        LambdaInfo lambdaInfo = (LambdaInfo) obj.getAllocation();
        List<Var> preciseArgs = lambdaAnalysis
                .getLambdaPreciseArgs(lambdaInfo, isBinary);
        assert preciseArgs != null;
        CSVar csPreciseArg = csManager.getCSVar(
                csLambdaObj.getContext(), preciseArgs.get(idx));
        // record lambda mock arg
        Var mockArg = lambdaInfo.getMockArg(idx);
        this.lamArgHubLMockArgCpt.put(csSrcHub.getVar(), mockArg);
        solver.addPFGEdge(new ShortcutEdge(csSrcHub, csPreciseArg), csPreciseArg.getType());
    }

    private void addLambdaRet(CSObj csLambdaObj, Object o) {
        Context lambdaContext = csLambdaObj.getContext();
        MockObj lambdaObj = (MockObj) csLambdaObj.getObject();
        LambdaInfo lambdaInfo = (LambdaInfo) lambdaObj.getAllocation();
        // add lambda ret to new strm's source
        Var lambdaRet = lambdaAnalysis.getLambdaRet(lambdaInfo);
        CSVar csLambdaRet = csManager.getCSVar(lambdaContext, lambdaRet);
        if (o instanceof Strm l) {
            streamAnalysis.addStrmSource(l, csLambdaRet);
        }
        if (o instanceof CSVar to) {
            solver.addPFGEdge(new ShortcutEdge(csLambdaRet, to), to.getType());
        }
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

    private void addLambdaRetAsPred(CSObj csLambdaObj, Strm l) {
        Context lambdaContext = csLambdaObj.getContext();
        MockObj lambdaObj = (MockObj) csLambdaObj.getObject();
        LambdaInfo lambdaInfo = (LambdaInfo) lambdaObj.getAllocation();
        Var lambdaRet = lambdaAnalysis.getLambdaRet(lambdaInfo);
        CSVar csLambdaRet = csManager.getCSVar(lambdaContext, lambdaRet);
        handler.var2PipeInput.put(csLambdaRet, l);
        handler.addStrmPredVar(l, csLambdaRet);
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
