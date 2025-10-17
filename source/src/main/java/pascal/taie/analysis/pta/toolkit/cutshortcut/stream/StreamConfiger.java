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
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Pair;
import pascal.taie.util.collection.Sets;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

class StreamConfiger {
    /**
     * records for parsing config
     */
    record Creation(String sig, Integer index) {
    }
    record ArrayEn(String sig, Integer index, Integer unmodified) {
    }
    record Entrance(String sig, Integer source, Integer stream) {
    }
    record Unsound(String sig, Integer index) {
    }

    /**
     * stream related class and types
     */
    private final Set<JClass> configuredClass;
    private final Set<Type> potentialTypes;
    private static final Set<String> streamTypeNames = Sets.newSet(Arrays.asList(
            "java.util.stream.BaseStream", "java.util.stream.PipelineHelper",
            "java.util.stream.Stream$Builder", "java.util.Optional"));
    final Type streamType;

    /**
     * stream related methods
     */
    private final Set<JMethod> creations;
    private final Map<JMethod, Integer> contCreations;
    private final Map<JMethod, Pair<Integer, Boolean>> arrayCreations;
    private final Map<JMethod, Pair<Integer, Integer>> entrances;
    private final Set<JMethod> processes;
    private final Set<JMethod> outputs;
    private final Set<JMethod> primCreations;
    private final Set<JMethod> maps;
    private final Set<JMethod> flatMaps;
    private final Set<JMethod> forEaches;
    private final Set<JMethod> conservModeled;
    private JMethod concat;
    private JMethod orElse;
    private JMethod collect;
    private JMethod simpleReduce;
    private JMethod reduceOut;
    private JMethod reduceOutPrl;
    private JMethod iterate;
    private JMethod generate;
    private final Set<JMethod> retTransferCallee;
    private final Set<JMethod> passTransferCallee;
    private final Set<JMethod> exits;

    /**
     * utils
     */
    private final ClassHierarchy hierarchy;
    private final TypeSystem typeSystem;
    private static final String STREAM_CONFIG = "cut-shortcut/stream-config.yml";

    StreamConfiger(ClassHierarchy hierarchy, TypeSystem typeSystem) {
        this.configuredClass = Sets.newHybridSet();
        this.potentialTypes = Sets.newSet();
        this.creations = Sets.newSet();
        this.contCreations = Maps.newMap();
        this.arrayCreations = Maps.newMap();
        this.entrances = Maps.newMap();
        this.processes = Sets.newSet();
        this.outputs = Sets.newSet();
        this.primCreations = Sets.newSet();
        this.maps = Sets.newSet();
        this.flatMaps = Sets.newSet();
        this.forEaches = Sets.newSet();
        this.conservModeled = Sets.newSet();
        this.retTransferCallee = Sets.newSet();
        this.passTransferCallee = Sets.newSet();
        this.exits = Sets.newSet();
        this.hierarchy = hierarchy;
        this.typeSystem = typeSystem;
        this.streamType = typeSystem.getType("java.util.stream.Stream");
        readConfig();
        setType();
        completeConfig();
    }

    private void readConfig() {
        InputStream streamContent = StreamConfiger.class
                .getClassLoader().getResourceAsStream(STREAM_CONFIG);
        StreamConfig.parseConfigs(streamContent).forEach(this::processStreamConfig);
    }

    private void processStreamConfig(StreamConfig config) {
        // scopes
        List<String> scopeConfig = config.scope();
        scopeConfig.forEach(className -> {
            JClass inScope = hierarchy.getClass(className);
            if (inScope != null) {
                addAllInnerClass(inScope);
            }
        });
        // creations
        List<String> creationConfig = config.creation();
        creationConfig.forEach(sig -> {
            JMethod creation = hierarchy.getMethod(sig);
            if (creation != null) {
                creations.add(creation);
            }
        });
        // container creations
        List<Creation> containerCreationConfig = config.containerCreation();
        containerCreationConfig.forEach(record -> {
            JMethod contCreation = hierarchy.getMethod(record.sig);
            if (contCreation != null) {
                contCreations.put(contCreation, record.index);
            }
        });
        // array creations
        List<ArrayEn> arrayCreationConfig = config.arrayCreation();
        arrayCreationConfig.forEach(record -> {
            JMethod arrayCreation = hierarchy.getMethod(record.sig());
            if (arrayCreation != null) {
                arrayCreations.put(arrayCreation,
                        new Pair<>(record.index(), record.unmodified == 1));
            }
        });
        // entrances
        List<Entrance> entranceConfig = config.entrance();
        entranceConfig.forEach(record -> {
            JMethod entrance = hierarchy.getMethod(record.sig);
            if (entrance != null) {
                entrances.put(entrance, new Pair<>(record.source, record.stream));
            }
        });
        // processes
        List<String> processConfig = config.process();
        processConfig.forEach(sig -> {
            JMethod process = hierarchy.getMethod(sig);
            if (process != null) {
                processes.add(process);
            }
        });
        // outputs
        List<String> outputConfig = config.output();
        outputConfig.forEach(sig -> {
            JMethod output = hierarchy.getMethod(sig);
            if (output != null) {
                outputs.add(output);
            }
        });
        // primitive creations
        List<String> primitiveCreationConfig = config.primitiveCreation();
        primitiveCreationConfig.forEach(sig -> {
            JMethod primCreation = hierarchy.getMethod(sig);
            if (primCreation != null) {
                primCreations.add(primCreation);
            }
        });
        // maps
        List<String> mapConfig = config.map();
        mapConfig.forEach(sig -> {
            JMethod map = hierarchy.getMethod(sig);
            if (map != null) {
                maps.add(map);
            }
        });
        // flatMaps
        List<String> flatMapConfig = config.flatMap();
        flatMapConfig.forEach(sig -> {
            JMethod flatMap = hierarchy.getMethod(sig);
            if (flatMap != null) {
                flatMaps.add(flatMap);
            }
        });
        // forEaches
        List<String> forEachConfig = config.forEach();
        forEachConfig.forEach(sig -> {
            JMethod forEach = hierarchy.getMethod(sig);
            if (forEach != null) {
                forEaches.add(forEach);
            }
        });
    }

    private void addAllInnerClass(JClass jClass) {
        this.configuredClass.add(jClass);
        hierarchy.getDirectInnerClassesOf(jClass).forEach(this::addAllInnerClass);
    }

    private void setType() {
        streamTypeNames.forEach(typeName -> {
            this.potentialTypes.add(typeSystem.getType(typeName));
        });
    }

    private void completeConfig() {
        // record concat
        this.concat = hierarchy.getMethod(
                "<java.util.stream.Stream: java.util.stream.Stream concat(java.util.stream.Stream,java.util.stream.Stream)>");

        // record orElse
        this.orElse = hierarchy.getMethod(
                "<java.util.Optional: java.lang.Object orElseGet(java.util.function.Supplier)>");

        // record collect
        this.collect = hierarchy.getMethod(
                "<java.util.stream.ReferencePipeline: java.lang.Object collect(java.util.function.Supplier,java.util.function.BiConsumer,java.util.function.BiConsumer)>");

        // record reduces
        this.simpleReduce = hierarchy.getMethod(
                "<java.util.stream.ReferencePipeline: java.util.Optional reduce(java.util.function.BinaryOperator)>");
        this.reduceOut = hierarchy.getMethod(
                "<java.util.stream.ReferencePipeline: java.lang.Object reduce(java.lang.Object,java.util.function.BinaryOperator)>");
        this.reduceOutPrl = hierarchy.getMethod(
                "<java.util.stream.ReferencePipeline: java.lang.Object reduce(java.lang.Object,java.util.function.BiFunction,java.util.function.BinaryOperator)>");

        this.iterate = hierarchy.getMethod(
                "<java.util.stream.Stream: java.util.stream.Stream iterate(java.lang.Object,java.util.function.UnaryOperator)>");
        this.generate = hierarchy.getMethod(
                "<java.util.stream.Stream: java.util.stream.Stream generate(java.util.function.Supplier)>");

        // record conservModeled operations
        Set<JMethod> allModeledMethods = Sets.newSet();
        allModeledMethods.addAll(creations);
        allModeledMethods.addAll(contCreations.keySet());
        allModeledMethods.addAll(arrayCreations.keySet());
        allModeledMethods.addAll(entrances.keySet());
        allModeledMethods.addAll(processes);
        allModeledMethods.addAll(outputs);
        allModeledMethods.addAll(primCreations);
        allModeledMethods.addAll(maps);
        allModeledMethods.addAll(flatMaps);
        allModeledMethods.addAll(forEaches);
        allModeledMethods.add(concat);
        allModeledMethods.add(orElse);
        allModeledMethods.add(collect);
        allModeledMethods.add(simpleReduce);
        allModeledMethods.add(reduceOut);
        allModeledMethods.add(reduceOutPrl);
        allModeledMethods.add(iterate);
        allModeledMethods.add(generate);
        JClass streamClass = hierarchy.getClass("java.util.stream.Stream");
        JClass optionalClass = hierarchy.getClass("java.util.Optional");
        JClass builderClass = hierarchy.getClass("java.util.stream.Stream$Builder");
        JClass supportClass = hierarchy.getClass("java.util.stream.StreamSupport");
        Set<JClass> potentialC = Sets.newSet();
        potentialC.add(streamClass);
        potentialC.add(optionalClass);
        potentialC.add(builderClass);
        potentialC.add(supportClass);
        potentialC.forEach(sclass -> {
            hierarchy.getAllSubclassesOf(sclass).forEach(c -> {
                c.getDeclaredMethods().forEach(m -> {
                    boolean isStreamType = false;
                    for (JClass returnVClass : potentialC) {
                        if (typeSystem.isSubtype(returnVClass.getType(), m.getReturnType())) {
                            isStreamType = true;
                            break;
                        }
                    }
                    if (isStreamType && !allModeledMethods.contains(m) && !m.isAbstract()) {
                        conservModeled.add(m);
                    }
                });
            });
        });

        // record methods used as Edge Transfer filters
        this.retTransferCallee.addAll(creations);
        this.retTransferCallee.addAll(contCreations.keySet());
        this.retTransferCallee.addAll(arrayCreations.keySet());
        this.retTransferCallee.addAll(entrances.keySet());
        this.retTransferCallee.addAll(processes);
        this.retTransferCallee.addAll(primCreations);
        this.retTransferCallee.addAll(maps);
        this.retTransferCallee.addAll(flatMaps);
        this.retTransferCallee.add(concat);
        this.retTransferCallee.add(simpleReduce);
        this.retTransferCallee.add(iterate);
        this.retTransferCallee.add(generate);
        this.retTransferCallee.addAll(conservModeled);

        this.passTransferCallee.addAll(primCreations);
        this.passTransferCallee.addAll(maps);
        this.passTransferCallee.addAll(flatMaps);
        this.passTransferCallee.addAll(forEaches);
        this.passTransferCallee.add(simpleReduce);
        this.passTransferCallee.add(reduceOut);
        this.passTransferCallee.add(reduceOutPrl);
        this.passTransferCallee.add(collect);
        this.passTransferCallee.add(orElse);
        this.passTransferCallee.add(iterate);
        this.passTransferCallee.add(generate);

        this.exits.addAll(outputs);
        this.exits.add(collect);
        this.exits.add(reduceOut);
        this.exits.add(reduceOutPrl);
    }

    boolean isInScopeClass(JClass jClass) {
        return configuredClass.contains(jClass);
    }

    Set<JClass> getInScopeClass() {
        return configuredClass;
    }

    boolean isCreation(JMethod method) {
        return creations.contains(method);
    }

    boolean isContCreation(JMethod method) {
        return contCreations.containsKey(method);
    }

    Integer getContCreationContIndex(JMethod method) {
        return contCreations.get(method);
    }

    boolean isArrayCreation(JMethod method) {
        return arrayCreations.containsKey(method);
    }

    Pair<Integer, Boolean> getArrayCreationArrayIndex(JMethod method) {
        return arrayCreations.get(method);
    }

    boolean isEntrance(JMethod method) {
        return entrances.containsKey(method);
    }

    Pair<Integer, Integer> getEntranceInfo(JMethod method) {
        return entrances.get(method);
    }

    boolean isProcess(JMethod method) {
        return processes.contains(method);
    }

    boolean isOutput(JMethod method) {
        return outputs.contains(method);
    }

    boolean isPrimCreation(JMethod method) {
        return primCreations.contains(method);
    }

    boolean isMap(JMethod method) {
        return maps.contains(method);
    }

    boolean isFlatMap(JMethod method) {
        return flatMaps.contains(method);
    }

    boolean isForEach(JMethod method) {
        return forEaches.contains(method);
    }

    boolean isConcat(JMethod method) {
        return method.equals(concat);
    }

    boolean isOrElse(JMethod method) {
        return method.equals(orElse);
    }

    boolean isCollect(JMethod method) {
        return method.equals(collect);
    }

    boolean isSimpleReduce(JMethod method) {
        return method.equals(simpleReduce);
    }

    boolean isReduceOut(JMethod method) {
        return method.equals(reduceOut);
    }

    boolean isReduceOutPrl(JMethod method) {
        return method.equals(reduceOutPrl);
    }

    boolean isIterate(JMethod method) {
        return method.equals(iterate);
    }

    boolean isGenerate(JMethod method) {
        return method.equals(generate);
    }

    boolean isConservModeledOp(JMethod method) {
        return conservModeled.contains(method);
    }

    boolean isRetEdgeTransferCallee(JMethod method) {
        return retTransferCallee.contains(method);
    }

    boolean isPassEdgeTransferCallee(JMethod method) {
        return passTransferCallee.contains(method);
    }

    boolean isExit(JMethod method) {
        return exits.contains(method);
    }

    Set<JMethod> getExits() {
        return exits;
    }

    Set<Type> getStrmPossibleTypes() {
        return potentialTypes;
    }
}
