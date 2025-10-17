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

package pascal.taie.analysis.pta.toolkit.cutshortcut;

import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.CompositePlugin;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.invokedynamic.LambdaAnalysis;
import pascal.taie.analysis.pta.toolkit.cutshortcut.container.ContainerAccessHandler;
import pascal.taie.analysis.pta.toolkit.cutshortcut.field.LoadHandler;
import pascal.taie.analysis.pta.toolkit.cutshortcut.field.StoreHandler;
import pascal.taie.analysis.pta.toolkit.cutshortcut.local.LocalFlowHandler;
import pascal.taie.analysis.pta.toolkit.cutshortcut.stream.StreamHandler;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.Sets;

import java.util.Objects;
import java.util.Set;

public class CutShortcut extends CompositePlugin {
    /**
     * flags for each pattern along
     */
    private boolean LOCAL_FLOW = false;
    private boolean STORE = false;
    private boolean LOAD = false;
    private boolean CONTAINER = false;
    private boolean STREAM = false;
    private boolean PRECISE_LAMBDA = false;

    /**
     * if use array replication strategy in container access & stream handler
     */
    private boolean ARRAY_REPLICATE = false;

    /**
     * if record involved methods for each pattern.
     */
    private boolean RECORD_INVOLVED = false;

    private final LambdaAnalysis lambdaAnalysis;

    private boolean  DISTINGUISH_STR_CONS = true;

    public CutShortcut(String arg, LambdaAnalysis lambdaAnalysis, String distinguishStrCons) {
        this.lambdaAnalysis = lambdaAnalysis;
        if (!Objects.equals(distinguishStrCons, "all")) {
            DISTINGUISH_STR_CONS = false;
        }

        switch (arg) {
            case "cut-shortcut-S" -> {
                LOCAL_FLOW = true;
                STORE = true;
                LOAD = true;
                CONTAINER = true;
                STREAM = true;
                ARRAY_REPLICATE = true;
                PRECISE_LAMBDA = true;
            }
            case "cut-shortcut-involved" -> {
                LOCAL_FLOW = true;
                STORE = true;
                LOAD = true;
                CONTAINER = true;
                RECORD_INVOLVED = true;
            }
            case "cut-shortcut" -> {
                LOCAL_FLOW = true;
                STORE = true;
                LOAD = true;
                CONTAINER = true;
            }
            default -> {
                String patterns = arg.split("-")[2];
                if (patterns.isEmpty() || patterns.length() > 4) {
                    throw new IllegalArgumentException("Illegal Cut-Shortcut argument: " + arg);
                }
                for (int i = 0; i < patterns.length(); i++) {
                    char pattern = patterns.charAt(i);
                    switch (pattern) {
                        case 'c' -> CONTAINER = true;
                        case 'f' -> {
                            STORE = true;
                            LOAD = true;
                        }
                        case 'l' -> LOCAL_FLOW = true;
                        case 's' -> {
                            STREAM = true;
                            ARRAY_REPLICATE = true;
                            PRECISE_LAMBDA = true;
                        }
                        default -> throw new IllegalArgumentException("Illegal Cut-Shortcut argument: " + arg);
                    }
                }
            }
        }
    }

    @Override
    public void setSolver(Solver solver) {
        // initialize cutshortcut helper
        CSCHelper cscHelper = new CSCHelper(solver.getCSManager(), lambdaAnalysis);

        // to avoid the same method handled by less-precise handlers
        Set<JMethod> excludeMethods = Sets.newSet();

        // initialize ArrayReplicationAnalysis
        ArrayReplicationAnalysis arrayReplicationAnalysis = null;
        if (ARRAY_REPLICATE) {
            arrayReplicationAnalysis = new ArrayReplicationAnalysis(solver);
        }

        // initialize container access pattern handler
        ContainerAccessHandler containerAccessHandler = null;
        if (CONTAINER) {
            containerAccessHandler = new ContainerAccessHandler(solver,
                                     PRECISE_LAMBDA, cscHelper, lambdaAnalysis,
                                     arrayReplicationAnalysis, RECORD_INVOLVED);
            excludeMethods.addAll(containerAccessHandler.getExits());
        }

        // initialize stream access pattern handler
        StreamHandler streamHandler = null;
        if (STREAM) {
            streamHandler = new StreamHandler(
                            solver, PRECISE_LAMBDA,
                            cscHelper, lambdaAnalysis,
                            arrayReplicationAnalysis);
            streamHandler.setContainerAccessInfo(
                    CONTAINER ? containerAccessHandler.getAnalysis() : null,
                    CONTAINER ? containerAccessHandler.getContainerConfigScope() : null,
                    CONTAINER ? containerAccessHandler.getArrayRepScope() : null);
            if (CONTAINER) {
                containerAccessHandler.setStreamPatternInfo(
                        streamHandler.getAnalysis(),
                        streamHandler.getStreamConfigInScope(),
                        streamHandler.getExits());
            }
            excludeMethods.addAll(streamHandler.getExits());
        }

        // initialize local flow pattern handler
        Plugin localFlowHandler = null;
        if (LOCAL_FLOW) {
            localFlowHandler = new LocalFlowHandler(solver, excludeMethods,
                               cscHelper, RECORD_INVOLVED, DISTINGUISH_STR_CONS);
        }

        // initialize store pattern handler
        StoreHandler storeHandler = null;
        if (STORE) {
            storeHandler = new StoreHandler(solver, cscHelper,
                           solver.getTypeSystem(), RECORD_INVOLVED, DISTINGUISH_STR_CONS);
        }

        // initialize load pattern handler
        Plugin loadHandler = null;
        if (LOAD) {
            loadHandler = new LoadHandler(solver, excludeMethods,
                          cscHelper, RECORD_INVOLVED, DISTINGUISH_STR_CONS);
        }

        // compose
        if (STORE) {
            addPlugin(storeHandler);
        }
        if (LOAD) {
            addPlugin(loadHandler);
        }
        if (LOCAL_FLOW) {
            addPlugin(localFlowHandler);
        }
        if (CONTAINER) {
            addPlugin(containerAccessHandler);
        }
        if (STREAM) {
            addPlugin(streamHandler);
        }
    }
}