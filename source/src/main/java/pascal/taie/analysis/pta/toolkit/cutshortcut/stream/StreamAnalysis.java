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

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.toolkit.cutshortcut.ShortcutEdge;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.ContainerAccessAnalysis;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.Host;
import pascal.taie.ir.exp.Var;
import pascal.taie.language.type.TopType;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;

public class StreamAnalysis {

    Solver solver;
    CSManager csManager;
    StreamHandler streamHandler;
    private ContainerAccessAnalysis containerAnalysis;
    private StreamLambdaAnalysis streamLambdaAnalysis;

    /**
     * soundness control
     */
    private final MultiMap<Strm, Host> strm2Host;
    private final MultiMap<Strm, CSVar> strm2Function;


    /**
     * statistics
     */
    final MultiMap<Strm, CSVar> streamTargets;

    StreamAnalysis(Solver solver,
                   CSManager csManager,
                   StreamHandler streamHandler) {
        this.solver = solver;
        this.csManager = csManager;
        this.streamHandler = streamHandler;
        this.strm2Host = Maps.newMultiMap();
        this.strm2Function = Maps.newMultiMap();
        this.streamTargets = Maps.newMultiMap();
    }

    void setContainerAnalysis(ContainerAccessAnalysis analysis) {
        containerAnalysis = analysis;
    }

    void setStreamLambdaAnalysis(StreamLambdaAnalysis analysis) {
        this.streamLambdaAnalysis = analysis;
    }

    void onNewStrm(Strm strm) {
        // create source hub mock var and target hub mock var
        Var sourceHub = new Var(null, "[SourceHub]-{" + strm + "}", TopType.Top, -1);
        Context emptyContext = solver.getContextSelector().getEmptyContext();
        CSVar csSourceHub = csManager.getCSVar(emptyContext, sourceHub);
        strm.setSourceHub(csSourceHub);
    }

    void addStrmSource(Strm strm, CSVar source) {
        if (!strm.isConserv()) {
            strm.addSource(source);
            solver.addPFGEdge(new ShortcutEdge(source, strm.getSourceHub()));
        }
    }

    void addStrmObj(Strm strm, CSObj obj) {
        if (!strm.isConserv()) {
            solver.addPointsTo(strm.getSourceHub(), obj);
        }
    }

    void addStrmTarget(Strm strm, CSVar target) {
        streamTargets.put(strm, target);
        if (!strm.isConserv()) {
            strm.addTarget(target);
            solver.addPFGEdge(new ShortcutEdge(
                    strm.getSourceHub(), target), target.getType());
        }
    }

    void addStrmSuccessor(Strm succ, Strm pred) {
        pred.addSuccessor(succ);
        succ.addPredecessor(pred);
        onNewSuccessor(pred, succ);
    }

    void onNewSuccessor(Strm small, Strm large) {
        CSVar smallSourceHub = small.getSourceHub();
        CSVar largeSourceHub = large.getSourceHub();
        large.addSource(smallSourceHub);
        solver.addPFGEdge(new ShortcutEdge(smallSourceHub, largeSourceHub));
        if (small.isConserv()) {
            markUnsound(large);
        }
    }

    public void markUnsound(Strm strm) {
        propagateUnsound(strm);
    }

    private void propagateUnsound(Strm strm) {
        if (!strm.isConserv()) {
            strm.setUnsound();
            //re-add return edge for this strm's outputAPI call-site
            streamHandler.undoStrmRelatedOutput(strm);
            //trigger related host's taint flag.
            if (containerAnalysis != null) {
                strm2Host.get(strm).forEach(containerAnalysis::markTaint);
            }
            // trigger related lambdaObj's unsound flag.
            if (streamLambdaAnalysis != null) {
                strm2Function.get(strm).forEach(streamLambdaAnalysis::triggerFunctionUnsound);
            }
            // propagate unsoundness through outedges
            strm.getSuccessors().forEach(succ -> {
                if (!succ.isConserv()) {
                    propagateUnsound(succ);
                }
            });
        }
    }

    public void addStrm2Host(Strm l, Host h) {
        this.strm2Host.put(l, h);
    }

    public void addStrm2Function(Strm l, CSVar function) {
        this.strm2Function.put(l, function);
    }
}
