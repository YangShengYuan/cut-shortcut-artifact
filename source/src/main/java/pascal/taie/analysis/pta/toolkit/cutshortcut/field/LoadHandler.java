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
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.PropagateTypes;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.FieldRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassMember;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;
import pascal.taie.util.collection.Sets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Set;

/**
 * In contrast to {@link StoreHandler}, the implementation of {@link LoadInfo}
 * necessitates the removal of established PFG edges, particularly
 * those return edges that transmit loaded contents. This adds complexity
 * and reduces the controllability of the solver. Therefore, we have limited
 * the implementation to a one-level cut-shortcut for the load pattern.
 */
public class LoadHandler implements Plugin {

    private final Solver solver;

    private final CSManager csManager;

    private final TypeSystem typeSystem;

    private final LoadAnalysis loadAnalysis;

    private final CSCHelper utils;

    private final Set<Var> cutReturns = Sets.newSet();

    private record LoadInfo(int baseIndex, FieldRef fieldRef) {
    }

    private final MultiMap<JMethod, LoadInfo> loadInfos = Maps.newMultiMap();

    private final MultiMap<CSVar, Pair<JField, CSVar>> loadEdges = Maps.newMultiMap();

    private final Set<JMethod> conflictExits;
    /**
     * statistics
     */
    private final boolean INVOLVED;

    private final Set<JMethod> involvedMethods = Sets.newSet();

    public LoadHandler(Solver solver, Set<JMethod> conflictExits, CSCHelper utils,
                       boolean involved, boolean distinguishStrCons) {
        this.solver = solver;
        this.csManager = solver.getCSManager();
        this.typeSystem = solver.getTypeSystem();
        this.utils = utils;
        PropagateTypes propTypes = solver.getPropagateTypes();
        DefinedVars definedVars = new DefinedVars();
        this.loadAnalysis = new LoadAnalysis(propTypes, definedVars, utils,
                solver.getTypeSystem(), distinguishStrCons);
        this.conflictExits = conflictExits;
        INVOLVED = involved;
    }

    @Override
    public void onNewMethod(JMethod method) {
        // do not handle container configured exit.
        if (conflictExits.contains(method)) {
            return;
        }
        loadAnalysis.computeLoadFromParam(method);
        IR ir = method.getIR();
        ir.getReturnVars().forEach(ret -> {
            if (!loadAnalysis.getRetVarLoadSource(ret).isEmpty()) {
                loadAnalysis.getRetVarLoadSource(ret).forEach(pair -> {
                    cutReturns.add(ret);
                    loadInfos.put(method,
                            new LoadInfo(pair.first(), pair.second()));
                });
                // record involved methods
                if (INVOLVED) {
                    involvedMethods.add(method);
                }
            }
        });
    }

    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        Set<LoadInfo> loads = loadInfos.get(edge.getCallee().getMethod());
        if (!loads.isEmpty()) {
            // add load edge
            Invoke invoke = edge.getCallSite().getCallSite();
            if (invoke.getResult() != null) {
                Set<CSVar> csLHSS = getLHSS(edge);
                loads.forEach(loadInfo -> {
                    CSVar csBase = getArg(edge, loadInfo.baseIndex());
                    JField field = loadInfo.fieldRef().resolve();
                    csLHSS.forEach(csLHS -> {
                        if (csBase != null) {
                            if (loadEdges.put(csBase, new Pair<>(field, csLHS))) {
                                addLoadEdge(csLHS, csBase, field);
                            }
                        }
                    });
                });
            }
        }
    }

    private void addLoadEdge(CSVar to, CSVar base, JField field) {
        solver.getPointsToSetOf(base).forEach(baseObj -> {
            if (baseObj.getObject().isFunctional()) {
                Type fieldDeclarType = field.getDeclaringClass().getType();
                Type baseType = baseObj.getObject().getType();
                if (typeSystem.isSubtype(fieldDeclarType, baseType)) {
                    InstanceField instField = csManager.getInstanceField(baseObj, field);
                    solver.addPFGEdge(new ShortcutEdge(instField, to));
                }
            }
        });
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        loadEdges.get(csVar).forEach(pair -> {
            JField field = pair.first();
            CSVar to = pair.second();
            pts.forEach(baseObj -> {
                if (baseObj.getObject().isFunctional()) {
                    Type fieldDeclarType = field.getDeclaringClass().getType();
                    Type baseType = baseObj.getObject().getType();
                    if (typeSystem.isSubtype(fieldDeclarType, baseType)) {
                        InstanceField instField = csManager.getInstanceField(baseObj, field);
                        solver.addPFGEdge(new ShortcutEdge(instField, to));
                    }
                }
            });
        });
    }

    @Override
    public boolean shouldAdd(PointerFlowEdge edge) {
        if (edge.kind() == FlowKind.RETURN) {
            Var source = ((CSVar) edge.source()).getVar();
            return !cutReturns.contains(source);
        } else {
            return true;
        }
    }

    /**
     * use helper to get arg/return at different kinds of call-edge
     */
    private CSVar getArg(Edge<CSCallSite, CSMethod> edge, int index) {
        return utils.getCallSiteArg(edge, index);
    }

    private Set<CSVar> getLHSS(Edge<CSCallSite, CSMethod> edge) {
        return utils.getPotentialLHS(edge);
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
        String filename = "csc-load-involved-methods.txt";
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
