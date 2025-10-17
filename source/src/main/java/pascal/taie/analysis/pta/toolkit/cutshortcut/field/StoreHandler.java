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

package pascal.taie.analysis.pta.toolkit.cutshortcut.field;

import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraph;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.PropagateTypes;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.reflection.ReflectiveCallEdge;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InstanceFieldAccess;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.StoreField;
import pascal.taie.language.classes.ClassMember;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;


/**
 * handle store part of field access pattern for cut-shortcut.
 */
public class StoreHandler implements Plugin {

    private final Solver solver;
    private final PropagateTypes propTypes;
    private final CSManager csManager;
    private final CSCHelper helper;
    private final TypeSystem typeSystem;

    private CallGraph<CSCallSite, CSMethod> callGraph;

    private final DefinedVars definedVars = new DefinedVars();

    private final MultiMap<JField, Var> cutStores = Maps.newMultiMap();

    private record StoreInfo(int baseIndex, FieldRef fieldRef, int fromIndex) {
    }

    private record WrappedStoreInfo(StoreInfo storeInfo, Edge<CSCallSite, CSMethod> innerMostCallEdge, boolean baseIsReceiver) {
    }

    private final MultiMap<JMethod, StoreInfo> storeInfos = Maps.newMultiMap();

    private final MultiMap<CSMethod, WrappedStoreInfo> csStoreInfos = Maps.newMultiMap();

    private final MultiMap<CSVar, CalleeToCallerInfo> storeEdges = Maps.newMultiMap();

    private final MultiMap<CSVar, InstanceField> shortCutEdges = Maps.newMultiMap();

    private final Type stringType;
    private final boolean distinguishStrCons;

    record CalleeToCallerInfo(Edge<CSCallSite, CSMethod> edge, CSVar callerFrom, FieldRef fieldRef, boolean baseIsReceiver) {
    }

    /**
     * statistics
     */
    private final boolean INVOLVED;

    private final Set<JMethod> involvedMethods = Sets.newSet();

    public StoreHandler(Solver solver, CSCHelper helper, TypeSystem typeSystem,
                        boolean involved, boolean distinguishStrCons) {
        this.solver = solver;
        this.propTypes = solver.getPropagateTypes();
        this.csManager = solver.getCSManager();
        this.helper = helper;
        this.typeSystem = solver.getTypeSystem();
        this.stringType = typeSystem.getType("java.lang.String");
        this.distinguishStrCons = distinguishStrCons;
        INVOLVED = involved;
    }

    @Override
    public void onNewMethod(JMethod method) {
        // find cut stores
        IR ir = method.getIR();
        MultiMap<JField, Var> storesCanNotCut = Maps.newMultiMap();
        Set<StoreField> storeCandidate = Sets.newHybridSet();
        for (Stmt stmt : ir) {
            if (stmt instanceof StoreField store
                    && !store.isStatic()
                    && propTypes.isAllowed(store.getRValue())) {
                if (!distinguishStrCons) {
                    if (store.getRValue().getType().equals(stringType)) {
                        continue;
                    }
                }
                // base.f = from;
                Var base = ((InstanceFieldAccess) store.getLValue()).getBase();
                Var from = store.getRValue();
                if (ir.isThisOrParam(base) && ir.isThisOrParam(from)
                        && !definedVars.isDefined(base)
                        && !definedVars.isDefined(from)) {
                    storeCandidate.add(store);
                } else {
                    JField field = store.getFieldRef().resolve();
                    storesCanNotCut.put(field, from);
                }
            }
        }
        if (storeCandidate.size() > 10) {
            return;
        }
        for (StoreField store : storeCandidate) {
            Var from = store.getRValue();
            JField field = store.getFieldRef().resolve();
            if (!storesCanNotCut.contains(field, from)) {
                handleNewCutStore(store);
            }
        }
    }

    private void handleNewCutStore(StoreField store) {
        // record cut store
        FieldRef fieldRef = store.getFieldRef();
        JField field = fieldRef.resolve();
        Var from = store.getRValue();
        // record store info
        Var base = ((InstanceFieldAccess) store.getFieldAccess()).getBase();
        cutStores.put(field, from);
        IR ir = base.getMethod().getIR();
        storeInfos.put(base.getMethod(),
                new StoreInfo(helper.getParamIndex(ir, base), fieldRef, helper.getParamIndex(ir, from)));
        // record involved methods
        if (INVOLVED) {
            JMethod method = from.getMethod();
            involvedMethods.add(method);
        }
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        storeInfos.get(csMethod.getMethod()).forEach(storeInfo -> {
                    propagate1(csMethod, new WrappedStoreInfo(storeInfo, null,
                            storeInfo.baseIndex == InvokeUtils.BASE));
                });
    }

    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        ArrayList<WrappedStoreInfo> snapshot = new ArrayList<>(csStoreInfos.get(edge.getCallee()));
        for (WrappedStoreInfo wrapped : snapshot) {
            propagate2(edge, wrapped);
        }
    }

    private void propagate1(CSMethod callee, WrappedStoreInfo wrappedStoreInfo) {
        if (csStoreInfos.put(callee, wrappedStoreInfo)) {
            boolean[] hasCaller = {false};
            getCallGraph().edgesInTo(callee).forEach(edge -> {
                hasCaller[0] = true;
                if (wrappedStoreInfo.innerMostCallEdge == null) {
                    propagate2(edge, new WrappedStoreInfo(wrappedStoreInfo.storeInfo,
                            edge, wrappedStoreInfo.baseIsReceiver));
                } else {
                    propagate2(edge, wrappedStoreInfo);
                }
            });
            if (!hasCaller[0]) {
                // no caller, it means that callee is an entry method,
                // so we add store edge here
                StoreInfo storeInfo = wrappedStoreInfo.storeInfo;
                Var base = helper.getParam(callee.getMethod(), storeInfo.baseIndex());
                Var from = helper.getParam(callee.getMethod(), storeInfo.fromIndex());
                Context context = callee.getContext();
                addStoreEdge(wrappedStoreInfo.innerMostCallEdge, csManager.getCSVar(context, base),
                        storeInfo.fieldRef(), csManager.getCSVar(context, from),
                        wrappedStoreInfo.baseIsReceiver);
            }
        }
    }

    private void propagate2(Edge<CSCallSite, CSMethod> edge, WrappedStoreInfo wrappedStoreInfo) {
        // propagates store information from callee to caller
        StoreInfo storeInfo = wrappedStoreInfo.storeInfo;
        Invoke callSite = edge.getCallSite().getCallSite();
        StoreInfo callerInfo = calleeToCaller(edge, storeInfo);
        JMethod callee = edge.getCallee().getMethod();
        if (INVOLVED) {
            involvedMethods.add(callee);
        }
        if (callerInfo != null) {
            // store info in callee can be propagated to caller
            CSMethod caller = csManager.getCSMethod(
                    edge.getCallSite().getContext(), callSite.getContainer());
            propagate1(caller, new WrappedStoreInfo(callerInfo,
                    Objects.requireNonNullElse(wrappedStoreInfo.innerMostCallEdge, edge), wrappedStoreInfo.baseIsReceiver));
        } else {
            // otherwise, add store edge here
            CSVar csBase = getArg(edge, storeInfo.baseIndex());
            CSVar csFrom = getArg(edge, storeInfo.fromIndex());
            if (csFrom == null || csBase == null) {
                //e.g., clz.newInstance() -> <init>()
                //need to add edge in callee.
                Context calleeContext = edge.getCallee().getContext();
                csBase = csManager.getCSVar(calleeContext,
                        helper.getParam(callee, storeInfo.baseIndex()));
                csFrom = csManager.getCSVar(calleeContext,
                        helper.getParam(callee, storeInfo.fromIndex()));
            }
            addStoreEdge(Objects.requireNonNullElse(wrappedStoreInfo.innerMostCallEdge, edge),
                    csBase, storeInfo.fieldRef(), csFrom,
                    wrappedStoreInfo.baseIsReceiver);
        }
    }

    @Nullable
    private StoreInfo calleeToCaller(Edge<CSCallSite, CSMethod> edge, StoreInfo storeInfo) {
        CSVar csBase = getArg(edge, storeInfo.baseIndex());
        CSVar csFrom = getArg(edge, storeInfo.fromIndex());
        IR callerIR = edge.getCallSite().getCallSite().getContainer().getIR();
        if (csBase != null && csFrom != null) {
            Var base = csBase.getVar();
            Var from = csFrom.getVar();
            if (callerIR.isThisOrParam(base) && callerIR.isThisOrParam(from)
                    && !definedVars.isDefined(base) && !definedVars.isDefined(from)) {
                return new StoreInfo(helper.getParamIndex(callerIR, base),
                        storeInfo.fieldRef(), helper.getParamIndex(callerIR, from));
            }
        }
        // StoreInfo cannot be propagated to caller
        return null;
    }

    private void addStoreEdge(Edge<CSCallSite, CSMethod> edge, CSVar callerBase,
                              FieldRef fieldRef, CSVar callerFrom, boolean baseIsReceiver) {
        JField field = fieldRef.resolve();
        for (CSObj baseObj : solver.getPointsToSetOf(callerBase)) {
            if (baseObj.getObject().isFunctional()) {
                Type fieldDeclarType = field.getDeclaringClass().getType();
                Type baseType = baseObj.getObject().getType();
                if (typeSystem.isSubtype(fieldDeclarType, baseType)) {
                    if (edge !=null && !(edge instanceof ReflectiveCallEdge) && baseIsReceiver) {
                        Invoke callSite = edge.getCallSite().getCallSite();
                        JMethod callee = CallGraphs.resolveCallee(baseType, callSite);
                        if (!edge.getCallee().getMethod().equals(callee)) {
                            continue;
                        }
                    }
                    InstanceField instField = csManager.getInstanceField(baseObj, field);
                    if (shortCutEdges.put(callerFrom, instField)) {
                        solver.addPFGEdge(new ShortcutEdge(callerFrom, instField), instField.getType());
                    }
                }
            }
        }
        storeEdges.put(callerBase, new CalleeToCallerInfo(edge, callerFrom, fieldRef, baseIsReceiver));
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        storeEdges.get(csVar).forEach(info -> {
            Edge<CSCallSite, CSMethod> edge = info.edge;
            FieldRef fieldRef = info.fieldRef;
            JField field = fieldRef.resolve();
            CSVar from = info.callerFrom;
            boolean baseIsRec = info.baseIsReceiver;
            for (CSObj baseObj : pts) {
                if (baseObj.getObject().isFunctional()) {
                    Type fieldDeclarType = field.getDeclaringClass().getType();
                    Type baseType = baseObj.getObject().getType();
                    if (typeSystem.isSubtype(fieldDeclarType, baseType)) {
                        if (edge != null && !(edge instanceof ReflectiveCallEdge) && baseIsRec) {
                            Invoke callSite = edge.getCallSite().getCallSite();
                            JMethod callee = CallGraphs.resolveCallee(baseType, callSite);
                            if (!edge.getCallee().getMethod().equals(callee)) {
                                continue;
                            }
                        }
                        InstanceField instField = csManager.getInstanceField(baseObj, field);
                        if (shortCutEdges.put(from, instField)) {
                            solver.addPFGEdge(new ShortcutEdge(from, instField), instField.getType());
                        }
                    }
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

    private CallGraph<CSCallSite, CSMethod> getCallGraph() {
        var result = callGraph;
        if (result == null) {
            callGraph = result = solver.getCallGraph();
        }
        return result;
    }

    @Override
    public boolean shouldAdd(PointerFlowEdge edge) {
        if (edge.kind() == FlowKind.INSTANCE_STORE) {
            Var from = ((CSVar) edge.source()).getVar();
            JField field = ((InstanceField) edge.target()).getField();
            return !cutStores.contains(field, from);
        } else {
            return true;
        }
    }

    @Override
    public void onFinish() {
        Plugin.super.onFinish();
        // dump involved methods
        if (INVOLVED) {
            dumpInvolvedMethods();
        }
    }

    private void dumpInvolvedMethods() {
        File outputDir = World.get().getOptions().getOutputDir();
        String filename = "csc-store-involved-methods.txt";
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
