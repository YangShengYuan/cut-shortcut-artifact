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

package pascal.taie.analysis.pta.toolkit.cutshortcut.local;

import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.flowgraph.FlowKind;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.toolkit.cutshortcut.CSCHelper;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.HostManager;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassMember;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Set;

/**
 * handle local flow pattern for cut-shortcut.
 */
public class LocalFlowHandler implements Plugin {

    private final Solver solver;

    private final LocalFlowAnalysis analysis;

    private final CSCHelper helper;

    /**
     * Set of return variables that should be cut (from flowing to
     * LHS of the call site).
     */
    private final Set<Var> cutReturns = Sets.newSet();

    /**
     * Set of methods whose receiver object (pointed to by base variable)
     * may flow to its return variables.
     */
    private final Set<JMethod> shortcutBases = Sets.newSet();

    private final MultiMap<CSVar, Edge<CSCallSite, CSMethod>> shortcutBaseEdges = Maps.newMultiMap();

    /**
     * Map from a method to set of indexes for its arguments
     * whose values may flow to its return variables.
     */
    private final MultiMap<JMethod, Integer> shortcutArgs = Maps.newMultiMap();

    private final Set<JMethod> conflictExits;

    /**
     * statistics
     */
    private final boolean INVOLVED;
    private final Set<JMethod> involvedMethods = Sets.newSet();

    public LocalFlowHandler(Solver solver, Set<JMethod> conflictExits,
                            CSCHelper helper, boolean involved, boolean distinguishStrCons) {
        this.solver = solver;
        this.analysis = new LocalFlowAnalysis(solver.getTypeSystem(),
                solver.getPropagateTypes(), helper, distinguishStrCons);
        this.helper = helper;
        this.conflictExits = conflictExits;
        INVOLVED = involved;
    }

    @Override
    public void onNewMethod(JMethod method) {
        // do not handle container configured exit.
        if (conflictExits.contains(method)) {
            return;
        }
        Result result = analysis.analyze(method);
        if (!result.cutReturns().isEmpty()) {
            cutReturns.addAll(result.cutReturns());
            result.shortcuts().forEach(index -> {
                if (index == InvokeUtils.BASE) {
                    shortcutBases.add(method);
                } else {
                    shortcutArgs.put(method, index);
                }
            });
            // record involved methods
            if (INVOLVED) {
                involvedMethods.add(method);
            }
        }
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        if (shortcutBaseEdges.containsKey(csVar)) {
            shortcutBaseEdges.get(csVar).forEach(edge -> triggerBase2LHS(edge, pts));
        }
    }

    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        Invoke invoke = edge.getCallSite().getCallSite();
        if (invoke.getResult() != null) {
            JMethod callee = edge.getCallee().getMethod();
            Set<CSVar> csLHSS = getLHSS(edge);
            if (shortcutBases.contains(callee)) {
                CSVar csBase = getArg(edge, InvokeUtils.BASE);
                csLHSS.forEach(csLHS -> {
                    if (edge.getKind() != CallKind.OTHER) {
                        shortcutBaseEdges.put(csBase, edge);
                        triggerBase2LHS(edge, solver.getPointsToSetOf(csBase));
                    } else {
                        solver.addPFGEdge(new ShortcutEdge(csBase, csLHS), csLHS.getType());
                    }
                });
            }
            if (shortcutArgs.containsKey(callee)) {
                csLHSS.forEach(csLHS -> {
                    shortcutArgs.get(callee).forEach(index -> {
                        CSVar csArg = getArg(edge, index);
                        solver.addPFGEdge(new ShortcutEdge(csArg, csLHS), csLHS.getType());
                    });
                });
            }
        }
    }

    private void triggerBase2LHS(Edge<CSCallSite, CSMethod> edge, PointsToSet pts) {
        Invoke callsite = edge.getCallSite().getCallSite();
        JMethod callee = edge.getCallee().getMethod();
        Set<CSVar> csLHSS = getLHSS(edge);
        CSVar csBase = getArg(edge, InvokeUtils.BASE);
        csLHSS.forEach(csLHS -> {
            PointsToSet result = solver.makePointsToSet();
            for (CSObj csObj : pts) {
                Obj obj = csObj.getObject();
                if (HostManager.isHost(obj)) {
                    result.addObject(csObj);
                } else if (obj.isFunctional()) {
                    JMethod baseResolvedCallee = CallGraphs.resolveCallee(
                            csObj.getObject().getType(), callsite);
                    if (callee.equals(baseResolvedCallee)) {
                        result.addObject(csObj);
                    }
                }
            }
            solver.addPointsTo(csLHS, result);
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

    @Override
    public boolean shouldAdd(PointerFlowEdge edge) {
        return !(edge.kind() == FlowKind.RETURN
                && edge.source() instanceof CSVar csVar
                && cutReturns.contains(csVar.getVar()));
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
        String filename = "csc-l-involved-methods.txt";
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
