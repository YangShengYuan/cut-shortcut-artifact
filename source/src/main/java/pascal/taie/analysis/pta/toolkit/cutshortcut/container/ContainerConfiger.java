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
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.generics.ClassGSignature;
import pascal.taie.language.generics.MethodGSignature;
import pascal.taie.language.generics.TypeParameter;
import pascal.taie.language.type.NullType;
import pascal.taie.language.type.Type;
import pascal.taie.language.type.TypeSystem;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * processing container access configs and
 * provide methods for retrieving necessary info.
 */
class ContainerConfiger {

    /**
     * records for parsing config
     */
    record ConfigEntrance(String sig, Integer index) {
    }
    record ConfigBatchEntrance(String sig, Integer index) {
    }
    record BatchExit(String sig, Integer index) {
    }
    record ConfigArrayEntrance(String sig, Integer index, Integer unmodified) {
    }
    record ConfigBatchArrayEn(String sig, Integer index) {
    }
    record ConfigTransfer(String sig, String fromkind, String tokind) {
    }
    record InnerSource(String in, String invosig, Integer index) {
    }
    record UtilPrivateCont(String cont, List<String> allocsig) {
    }
    record UtilBatchEn(String sig, Integer from, Integer to, String kind) {
    }
    record UtilArrayEn(String sig, Integer from, Integer to, Integer unmodified) {
    }
    record UtilEntrance(String sig, Integer from, Integer to, String kind) {
    }
    record UtilTransfer(String sig, Integer from, Integer to) {
    }
    record Reentrancy(String in, String invosig) {
    }

    /**
     *record used in handler
     */
    record BatchEnInfo(Integer fromIdx, Integer toIdx, ContainerKind kind) {
    }
    record EntranceInfo(Integer sourceIdx, Integer contIdx, RetrieveKind kind) {
    }
    record ArrayEnInfo(Integer arrayIdx, Integer contIdx, Boolean unmodified) {
    }
    record BatchArrayEnInfo(Integer arrayIdx, Integer contIdx) {
    }
    record TransferInfo(Integer fromIdx, Integer toIdx, RetrieveKind fromKind, RetrieveKind toKind){
    }
    record InnerSourceInfo(JMethod sig, Integer index, RetrieveKind kind) {
    }

    /**
     * classes and types
     */
    // top class that may be containers and corresponding containerKind
    private final Map<JClass, ContainerKind> containerTopClass2Kind;
    // configured Tc
    private final Map<JClass, ContainerKind> configuredContainers;
    // configured Tv
    private final Set<JClass> configuredViews;
    // configured Tr = Tc | Tv
    private final Set<JClass> configuredRelatedClass;
    // T_r's innerClass closure
    private final Set<JClass> inScopeClass;
    // Types of hosts
    // T(hosts)
    private Set<Type> hostRelatedPointerTypes;
    // C(hosts)
    private Set<JClass> hostRelatedPointerClass;
    private final Set<JClass> arrayReplicationScope;

    /**
     * container related APIs
     */
    private final MultiMap<JMethod, RetrieveKind> exits;
    private final MultiMap<JMethod, EntranceInfo> entrances;
    private final MultiMap<JMethod, TransferInfo> transfers;
    private final Map<JMethod, BatchEnInfo> batchEntrances;
    private final Map<JMethod, ArrayEnInfo> arrayEntrances;
    private final Map<JMethod, BatchArrayEnInfo> batchArrayEns;


    private final MultiMap<JMethod, InnerSourceInfo> innerSources;
    private final Set<JMethod> unsoundOps;
    private final MultiMap<JMethod, JMethod> reentrancies;

    /**
     * util class APIs
     */
    private final Map<JClass, ContainerKind> utilPrivateConts;
    private final Map<JMethod, JClass> utilPrivateContAlloc;

    /**
     * stream related APIs
     */
    private final Set<JMethod> stmSimple2Cont;

    /**
     * lambda related APIs
     */
    private final Set<JMethod> collForEachs;
    Type collectionType;
    private  JMethod iterableForEach;
    private final Set<JMethod> mapForEachs;
    private final Set<JMethod> listReplaceAlls;
    private final Set<JMethod> mapReplaceAlls;
    private final Set<JMethod> mapMerges;
    private final Set<JMethod> mapComputeIfPresents;
    private final Set<JMethod> mapComputeIfAbsents;
    private JMethod stmCollectorCollect;
    private final Set<JMethod> collectorCollCreation;

    /**
     * transfer filtering methods
     */
    private final Set<JMethod> retTransferCallee;
    private final Set<JMethod> passTransferCallee;

    /**
     * utils
     */
    private final ClassHierarchy hierarchy;
    private final TypeSystem typeSystem;
    private final int jdk;

    ContainerConfiger(ClassHierarchy hierarchy,
                      TypeSystem typeSystem,
                      int jdk) {
        this.hierarchy = hierarchy;
        this.typeSystem = typeSystem;
        configuredContainers = Maps.newMap();
        configuredViews = Sets.newSet();
        containerTopClass2Kind = Maps.newMap();
        configuredRelatedClass = Sets.newSet();
        inScopeClass = Sets.newSet();
        exits = Maps.newMultiMap();
        entrances = Maps.newMultiMap();
        transfers = Maps.newMultiMap();
        batchEntrances = Maps.newMap();
        arrayEntrances = Maps.newMap();
        batchArrayEns = Maps.newMap();
        innerSources = Maps.newMultiMap();
        unsoundOps = Sets.newSet();
        reentrancies = Maps.newMultiMap();
        utilPrivateConts = Maps.newMap();
        utilPrivateContAlloc = Maps.newMap();
        stmSimple2Cont = Sets.newSet();
        collForEachs = Sets.newSet();
        mapForEachs = Sets.newSet();
        listReplaceAlls = Sets.newSet();
        mapReplaceAlls = Sets.newSet();
        mapMerges = Sets.newSet();
        mapComputeIfPresents = Sets.newSet();
        mapComputeIfAbsents = Sets.newSet();
        retTransferCallee = Sets.newSet();
        passTransferCallee = Sets.newSet();
        collectorCollCreation = Sets.newSet();
        arrayReplicationScope = Sets.newSet();
        this.jdk = jdk;
        //record some types
        setTypes();
        //read config from yml
        readConfig();
        //complete config
        completeConfig();
    }

    private void readConfig() {
        //for map config
        InputStream mapContent = ContainerConfiger.class
                .getClassLoader()
                .getResourceAsStream("cut-shortcut/map-jdk" + jdk + ".yml");
        MapConfig.parseConfigs(mapContent).forEach(this::processMapConfig);

        //for collection config
        InputStream collectionContent = ContainerConfiger.class
                .getClassLoader()
                .getResourceAsStream("cut-shortcut/collection-jdk" + jdk + ".yml");
        CollectionConfig.parseConfigs(collectionContent).forEach(this::processCollectionConfig);

        //for util config
        InputStream utilContext = ContainerConfiger.class
                .getClassLoader()
                .getResourceAsStream("cut-shortcut/utils-jdk" + jdk + ".yml");
        UtilConfig.parseConfigs(utilContext).forEach(this:: processUtilConfig);
    }

    /**
     * process one map/dictionary config
     */
    void processMapConfig(MapConfig config) {
        String className = config.className();
        parseClass(className, ContainerKind.MAP);
        // map value exits
        List<String> mapVExits = config.valueExit();
        parseExit(className, mapVExits, RetrieveKind.MAP_V);
        // map key exits
        List<String> mapKExits = config.keyExit();
        parseExit(className, mapKExits, RetrieveKind.MAP_K);
        //map entry exits
        List<String> mapEExits = config.entryExit();
        parseExit(className, mapEExits, RetrieveKind.MAP_E);
        //map key entrances
        List<ConfigEntrance> mapKEntrances = config.keyEntrance();
        parseEntrance(className, mapKEntrances, RetrieveKind.MAP_K);
        //map value entrances
        List<ConfigEntrance> mapVEntrances = config.valueEntrance();
        parseEntrance(className, mapVEntrances, RetrieveKind.MAP_V);
        //map batch entrance
        List<ConfigBatchEntrance> mapBatchEntrances = config.batchEntrance();
        parseBatchEntrance(className, mapBatchEntrances, ContainerKind.MAP);
        //map array entrance
        List<ConfigArrayEntrance> mapArrayEntrances = config.arrayEntrance();
        parseArrayEntrance(className, mapArrayEntrances);
        // map bacth array entrance
        List<ConfigBatchArrayEn> mapBatchArrayEns = config.batchArrayEn();
        parseBatchArrayEn(className, mapBatchArrayEns);
        //map transfers
        List<ConfigTransfer> mapTransfers = config.transfer();
        parseTransfer(className, mapTransfers);
        //map replaceAll
        List<String> mapReplAlls = config.replaceAll();
        parseMethods(className, mapReplAlls, mapReplaceAlls);
        //map inner-K
        List<InnerSource> mapInnerK = config.innerK();
        parseInnerSource(className, mapInnerK, RetrieveKind.MAP_K);
        //map inner-V
        List<InnerSource> mapInnerV = config.innerV();
        parseInnerSource(className, mapInnerV, RetrieveKind.MAP_V);
        //unsound operation
        List<String> unsoundRecords = config.unsound();
        parseMethods(className, unsoundRecords, unsoundOps);
        //view
        List<String> views = config.view();
        parseView(views);
    }

    /**
     * process one collection class config
     */
    void processCollectionConfig(CollectionConfig config) {
        // collection class
        String className = config.className();
        parseClass(className, ContainerKind.COLLECTION);
        // collection exits
        List<String> colExits = config.exit();
        parseExit(className, colExits, RetrieveKind.COL_ITEM);
        //collection entrances
        List<ConfigEntrance> colEntrances = config.entrance();
        parseEntrance(className, colEntrances, RetrieveKind.COL_ITEM);
        //collection batch exit
        List<BatchExit> colBatchExits = config.batchExit();
        parseBatchExit(className, colBatchExits, ContainerKind.COLLECTION);
        //collection batch entrance
        List<ConfigBatchEntrance> colBatchEntrances = config.batchEntrance();
        parseBatchEntrance(className, colBatchEntrances, ContainerKind.COLLECTION);
        //collection array entrance
        List<ConfigArrayEntrance> colArrayEntrances = config.arrayEntrance();
        parseArrayEntrance(className, colArrayEntrances);
        //collection transfers
        List<String> colTransferStr = config.transfer();
        if (colTransferStr != null) {
            List<ConfigTransfer> colTransfers = colTransferStr
                    .stream()
                    .map(s -> new ConfigTransfer(s, "COL_ITEM", "COL_ITEM"))
                    .toList();
            parseTransfer(className, colTransfers);
        }
        //list replaceAll
        List<String> listReplAlls = config.replaceAll();
        parseMethods(className, listReplAlls, listReplaceAlls);
        //unsound operation
        List<String> unsoundRecords = config.unsound();
        parseMethods(className, unsoundRecords, unsoundOps);
        //view
        List<String> views = config.view();
        parseView(views);
    }

    /**
     * process one util class config
     */
    void processUtilConfig(UtilConfig config) {
        // util class
        String className = config.className();
        JClass utilClass = hierarchy.getClass(className);
        if (utilClass == null) {
            return;
        }
        addArrayReflectionScope(utilClass);
        // util private containers
        List<UtilPrivateCont> privateConts = config.privates();
        parsePrivateCont(className, privateConts);
        // util batch entrances
        List<UtilBatchEn> utilBatchEns = config.batchEns();
        if (utilBatchEns != null) {
            utilBatchEns.forEach(batchEnRecord -> {
                JMethod batchEn = hierarchy.getMethod(getMethodSig(className, batchEnRecord.sig));
                if (batchEn != null) {
                    this.batchEntrances.put(batchEn,
                            new BatchEnInfo(batchEnRecord.from, batchEnRecord.to,
                                    ContainerKind.valueOf(batchEnRecord.kind)));
                }
            });
        }
        // util array entrances
        List<UtilArrayEn> utilArrayEns = config.arrayEns();
        if (utilArrayEns != null) {
            utilArrayEns.forEach(arrayEnRecord -> {
                JMethod arrayEn  = hierarchy.getMethod(getMethodSig(className, arrayEnRecord.sig));
                if (arrayEn != null) {
                    this.arrayEntrances.put(arrayEn,
                            new ArrayEnInfo(arrayEnRecord.from, arrayEnRecord.to,
                                    arrayEnRecord.unmodified == 1));
                }
            });
        }
        // util entrances
        List<UtilEntrance> utilEntrances = config.entrances();
        if (utilEntrances != null) {
            utilEntrances.forEach(enRecord -> {
                JMethod entrance = hierarchy.getMethod(getMethodSig(className, enRecord.sig));
                if (entrance != null) {
                    this.entrances.put(entrance,
                            new EntranceInfo(enRecord.from, enRecord.to, RetrieveKind.valueOf(enRecord.kind)));
                }
            });
        }
        // util transfers
        List<UtilTransfer> utilTransfers = config.transfers();
        if (utilTransfers != null) {
            Set<RetrieveKind> transferableKind = Sets.newSet(Arrays.asList(
                    RetrieveKind.COL_ITEM, RetrieveKind.MAP_ALL,
                    RetrieveKind.MAP_K, RetrieveKind.MAP_V));
            utilTransfers.forEach(transRecord -> {
                JMethod transfer = hierarchy.getMethod(getMethodSig(className, transRecord.sig));
                if (transfer != null) {
                    transferableKind.forEach(kind -> {
                        this.transfers.put(transfer,
                                new TransferInfo(transRecord.from, transRecord.to, kind, kind));
                    });
                }
            });
        }
        // util reentrancy
        List<Reentrancy> utilReentrancy = config.reentrancy();
        if (utilReentrancy != null) {
            utilReentrancy.forEach(reenterRecord -> {
                JMethod reentrancy = hierarchy.getMethod(getMethodSig(className, reenterRecord.in));
                JMethod invoSig = hierarchy.getMethod(reenterRecord.invosig);
                if (reentrancy != null && invoSig != null) {
                    this.reentrancies.put(reentrancy, invoSig);
                }
            });
        }
    }

    private void parseClass(String className, ContainerKind kind) {
        JClass containerClass = hierarchy.getClass(className);
        if (containerClass == null) {
            return;
        }
        for (JClass topClass : containerTopClass2Kind.keySet()) {
            if (hierarchy.isSubclass(topClass, containerClass)) {
                configuredContainers.put(containerClass, kind);
                return;
            }
        }
        configuredViews.add(containerClass);
    }

    private void parseExit(String className, List<String> exits, RetrieveKind kind) {
        if (exits != null) {
            exits.forEach(exitSig -> {
                JMethod exit = hierarchy.getMethod(getMethodSig(className, exitSig));
                if (exit != null) {
                    this.exits.put(exit, kind);
                }
            });
        }
    }

    private void parseEntrance(String className, List<ConfigEntrance> entrances, RetrieveKind kind) {
        if (entrances != null) {
            entrances.forEach(entranceRecord -> {
                JMethod entrance = hierarchy.getMethod(getMethodSig(className, entranceRecord.sig()));
                if (entrance != null) {
                    this.entrances.put(entrance,
                            new EntranceInfo(entranceRecord.index, -1, kind));
                }
            });
        }
    }

    private void parseBatchExit(String className, List<BatchExit> batchExits, ContainerKind kind) {
        if (batchExits != null) {
            batchExits.forEach(batchExitRecord -> {
                JMethod batchExit = hierarchy.getMethod(getMethodSig(className, batchExitRecord.sig));
                if (batchExit != null) {
                    this.batchEntrances.put(batchExit,
                            new BatchEnInfo(-1, batchExitRecord.index, kind));
                }
            });
        }
    }

    private void parseBatchEntrance(String className, List<ConfigBatchEntrance> batchEntrances, ContainerKind kind) {
        if (batchEntrances != null) {
            batchEntrances.forEach(batchEntranceRecord -> {
                JMethod batchEntrance = hierarchy.getMethod(getMethodSig(className,
                        batchEntranceRecord.sig));
                if (batchEntrance != null) {
                    this.batchEntrances.put(batchEntrance,
                            new BatchEnInfo(batchEntranceRecord.index, -1, kind));
                }
            });
        }
    }

    private void parseArrayEntrance(String className, List<ConfigArrayEntrance> arrayEntrances) {
        if (arrayEntrances != null) {
            arrayEntrances.forEach(arrayEntranceRecord -> {
                JMethod arrayEntrance = hierarchy.getMethod(getMethodSig(className,
                        arrayEntranceRecord.sig));
                if (arrayEntrance != null) {
                    this.arrayEntrances.put(arrayEntrance,
                            new ArrayEnInfo(arrayEntranceRecord.index, -1,
                                    arrayEntranceRecord.unmodified == 1));
                }
            });
        }
    }

    private void parseBatchArrayEn(String className, List<ConfigBatchArrayEn> batchArrayEns) {
        if (batchArrayEns != null) {
            batchArrayEns.forEach(batchArrayEnRecord -> {
                JMethod batchArrayEn = hierarchy.getMethod(getMethodSig(className,
                        batchArrayEnRecord.sig));
                if (batchArrayEn != null) {
                    this.batchArrayEns.put(batchArrayEn, new BatchArrayEnInfo(batchArrayEnRecord.index, -1));
                }
            });
        }
    }


    private void parseTransfer(String className, List<ConfigTransfer> transfers) {
        if (transfers != null) {
            transfers.forEach(transferRecord -> {
                JMethod transfer = hierarchy.getMethod(getMethodSig(className, transferRecord.sig));
                if (transfer != null) {
                    this.transfers.put(transfer,
                            new TransferInfo(-1, -2,
                                    RetrieveKind.valueOf(transferRecord.fromkind),
                                    RetrieveKind.valueOf(transferRecord.tokind)));
                }
            });
        }
    }

    private void parseInnerSource(String className, List<InnerSource> innerSources, RetrieveKind kind) {
        if (innerSources != null) {
            innerSources.forEach(innerSource -> {
                JMethod inMethod = hierarchy.getMethod(getMethodSig(className, innerSource.in));
                JMethod invoSig = hierarchy.getMethod(innerSource.invosig);
                int index = innerSource.index;
                if (inMethod != null && invoSig != null) {
                    this.innerSources.put(inMethod, new InnerSourceInfo(invoSig, index, kind));
                }
            });
        }
    }

    private void parseMethods(String className, List<String> methods, Set<JMethod> records) {
        if (methods != null) {
            methods.forEach(sig -> {
                JMethod method = hierarchy.getMethod(getMethodSig(className, sig));
                if (method != null) {
                    records.add(method);
                }
            });
        }
    }


    private void parseView(List<String> views) {
        if (views != null) {
            views.forEach(v -> {
                JClass viewClass = hierarchy.getClass(v);
                if (viewClass != null) {
                    this.configuredViews.add(viewClass);
                }
            });
        }
    }

    private void parsePrivateCont(String utilName, List<UtilPrivateCont> privateConts) {
        if (privateConts != null) {
            privateConts.forEach(privateCont -> {
                String className = privateCont.cont;
                JClass contClass = hierarchy.getClass(className);
                if (contClass != null) {
                    JClass mapClass = hierarchy.getClass("java.util.Map");
                    if (hierarchy.isSubclass(mapClass, contClass)) {
                        utilPrivateConts.put(contClass, ContainerKind.MAP);
                    } else {
                        utilPrivateConts.put(contClass, ContainerKind.COLLECTION);
                    }
                    List<String> allocSigs = privateCont.allocsig;
                    if (allocSigs != null) {
                        allocSigs.forEach(sig -> {
                            JMethod alloc = hierarchy.getMethod(getMethodSig(utilName, sig));
                            if (alloc != null) {
                                this.utilPrivateContAlloc.put(alloc, contClass);
                            }
                        });
                    }
                }
            });
        }
    }

    /**
     * record potential types/class for containers
     */
    private void setTypes() {
        // compute potential type/class for host-related pointers
        collectionType = typeSystem.getType("java.util.Collection");
        Set<Type> types = Sets.newSet();
        Set<JClass> classes = Sets.newSet();
        Arrays.asList("java.util.Collection", "java.util.Map", "java.util.Dictionary",
                "java.util.Iterator", "java.util.ListIterator", "java.util.Enumeration",
                "java.util.Spliterator", "java.util.Map$Entry", jdk == 8 ? "java.lang.Iterable" : "").forEach(typeName -> {
            Type type = typeSystem.getType(typeName);
            JClass jclass = hierarchy.getClass(typeName);
            if (type != null && jclass != null) {
                types.add(type);
                classes.add(jclass);
            }
        });
        hostRelatedPointerTypes = types;
        hostRelatedPointerClass = classes;
        // record container top class and containerKind
        JClass colClass = hierarchy.getClass("java.util.Collection");
        JClass mapClass = hierarchy.getClass("java.util.Map");
        JClass dicClass = hierarchy.getClass("java.util.Dictionary");
        JClass simpleEntryClass = hierarchy.getClass("java.util.AbstractMap$SimpleEntry");
        JClass spliteratorClass = hierarchy.getClass("java.util.Spliterator");
        JClass iteratorClass = hierarchy.getClass("java.util.Iterator");
        containerTopClass2Kind.put(colClass, ContainerKind.COLLECTION);
        containerTopClass2Kind.put(mapClass, ContainerKind.MAP);
        containerTopClass2Kind.put(dicClass, ContainerKind.MAP);
        containerTopClass2Kind.put(simpleEntryClass, ContainerKind.MAP);
        if (jdk == 8) {
            containerTopClass2Kind.put(spliteratorClass, ContainerKind.COLLECTION);
            containerTopClass2Kind.put(iteratorClass, ContainerKind.COLLECTION);
        }
    }

    private void completeConfig() {
        // record configured related class and in-scope class
        configuredRelatedClass.addAll(configuredViews);
        configuredRelatedClass.addAll(configuredContainers.keySet());
        configuredRelatedClass.forEach(this::addInScope);
        configuredRelatedClass.forEach(this::addArrayReflectionScope);

        // reentrancy for queue drainTos
        Set<String> drainToClass = Sets.newSet(Arrays.asList(
                "java.util.concurrent.DelayQueue", "java.util.concurrent.ArrayBlockingQueue",
                "java.util.concurrent.LinkedBlockingDeque", "java.util.concurrent.LinkedBlockingQueue",
                "java.util.concurrent.PriorityBlockingQueue", "java.util.concurrent.SynchronousQueue",
                "java.util.concurrent.LinkedTransferQueue"));
        JMethod colAdd = hierarchy.getMethod("<java.util.Collection: boolean add(java.lang.Object)>");
        drainToClass.forEach(c -> {
            JMethod drainTo1 = hierarchy.getMethod(getMethodSig(c, "int drainTo(java.util.Collection)"));
            JMethod drainTo2 = hierarchy.getMethod(getMethodSig(c, "int drainTo(java.util.Collection,int)"));
            if (drainTo1 != null && colAdd != null) {
                this.reentrancies.put(drainTo1, colAdd);
            }
            if (drainTo2 != null && colAdd != null) {
                this.reentrancies.put(drainTo2, colAdd);
            }
        });
        
        // config to support lambdaForEach
        Set<String> colForEachSigs = Sets.newSet(Arrays.asList(
                "<java.lang.Iterable: void forEach(java.util.function.Consumer)>",
                "<java.util.Iterator: void forEachRemaining(java.util.function.Consumer)>",
                "<java.util.Spliterator: void forEachRemaining(java.util.function.Consumer)>",
                "<java.util.Spliterator: boolean tryAdvance(java.util.function.Consumer)>"
        ));
        colForEachSigs.forEach(sig -> {
            JMethod forEach = hierarchy.getMethod(sig);
            if (forEach != null) {
                collForEachs.add(forEach);
            }
        });
        JMethod mapForEach = hierarchy.getMethod(
                "<java.util.Map: void forEach(java.util.function.BiConsumer)>");
        if (mapForEach != null) {
            mapForEachs.add(mapForEach);
        }
        this.iterableForEach = hierarchy.getMethod(
                "<java.lang.Iterable: void forEach(java.util.function.Consumer)>");

        // config to support map merge
        JMethod mapMerge = hierarchy.getMethod(
                "<java.util.Map: java.lang.Object merge(java.lang.Object,java.lang.Object,java.util.function.BiFunction)>");
        if (mapMerge != null) {
            mapMerges.add(mapMerge);
        }

        // config to support map computeIfPresent and compute
        JMethod mapCompute = hierarchy.getMethod(
                "<java.util.Map: java.lang.Object compute(java.lang.Object,java.util.function.BiFunction)>");
        JMethod mapComputeIfPresent = hierarchy.getMethod(
                "<java.util.Map: java.lang.Object computeIfPresent(java.lang.Object,java.util.function.BiFunction)>");
        if (mapCompute != null) {
            mapComputeIfPresents.add(mapCompute);
        }
        if (mapComputeIfPresent != null) {
            mapComputeIfPresents.add(mapComputeIfPresent);
        }

        // config to support map computeIfAbsent
        JMethod computeIfAbsent = hierarchy.getMethod(
                "<java.util.Map: java.lang.Object computeIfAbsent(java.lang.Object,java.util.function.Function)>");
        if (computeIfAbsent != null) {
            mapComputeIfAbsents.add(computeIfAbsent);
        }

        // config to support stream -> container transformation
        Set<String> stm2ContSig = Sets.newSet(Arrays.asList(
                "<java.util.stream.AbstractPipeline: java.util.Spliterator spliterator()>",
                "<java.util.stream.ReferencePipeline: java.util.Iterator iterator()>"));
        stm2ContSig.forEach(sig -> {
            JMethod m = hierarchy.getMethod(sig);
            if (m != null) {
                stmSimple2Cont.add(m);
            }
        });

        // config to support stream -> container by Collector
        stmCollectorCollect = hierarchy.getMethod(
                "<java.util.stream.ReferencePipeline: java.lang.Object collect(java.util.stream.Collector)>");

        Set<String> collectorCollCreationAPIs = Sets.newSet(Arrays.asList(
                "<java.util.stream.Collectors: java.util.stream.Collector toList()>",
                "<java.util.stream.Collectors: java.util.stream.Collector toSet()>",
                "<java.util.stream.Collectors: java.util.stream.Collector toCollection(java.util.function.Supplier)>"
                ));
        collectorCollCreationAPIs.forEach(sig -> {
            JMethod collector = hierarchy.getMethod(sig);
            if (collector != null) {
                collectorCollCreation.add(collector);
            }
        });

        // complete related methods in configured subclass
        MultiMap<JMethod, RetrieveKind> deltaExit = Maps.newMultiMap();
        MultiMap<JMethod, EntranceInfo> deltaEntrance = Maps.newMultiMap();
        MultiMap<JMethod, TransferInfo> deltaTransfer = Maps.newMultiMap();
        Map<JMethod, BatchEnInfo> deltaBatchEn = Maps.newMap();
        Map<JMethod, ArrayEnInfo> deltaArrayEn = Maps.newMap();
        Map<JMethod, BatchArrayEnInfo> deltaBatchArrayEn = Maps.newMap();
        Set<JMethod> deltaUnsoundOp = Sets.newSet();
        MultiMap<JMethod, InnerSourceInfo> deltaInnerSrc = Maps.newMultiMap();
        Set<JMethod> deltaCollForEach = Sets.newSet();
        Set<JMethod> deltaMapForEach = Sets.newSet();
        Set<JMethod> deltaListReplaceAll = Sets.newSet();
        Set<JMethod> deltaMapReplaceAll = Sets.newSet();
        Set<JMethod> deltaMapMerge = Sets.newSet();
        Set<JMethod> deltaMapComputeIfPresent = Sets.newSet();
        Set<JMethod> deltaMapComputeIfAbsent = Sets.newSet();
        hierarchy.allClasses()
                .filter(configuredRelatedClass::contains)
                .map(JClass::getDeclaredMethods)
                .flatMap(java.util.Collection::stream)
                .forEach(m -> {
                    // exit
                    this.exits.forEach((exit, retrieveType) -> {
                        if (compareMethodSig(m, exit)) {
                            deltaExit.put(m, retrieveType);
                        }
                    });
                    // entrance
                    this.entrances.forEach((entrance, info) -> {
                        if (compareMethodSig(m, entrance)) {
                            deltaEntrance.put(m, info);
                        }
                    });
                    // transfer
                    this.transfers.forEach((transfer, pair) -> {
                        if (compareMethodSig(m, transfer)) {
                            deltaTransfer.put(m, pair);
                        }
                    });
                    // batch-en
                    this.batchEntrances.forEach((batchEn, info) -> {
                        if (compareMethodSig(m, batchEn)) {
                            deltaBatchEn.put(m, info);
                        }
                    });
                    // array-en
                    this.arrayEntrances.forEach((arrayEn, info) -> {
                        if (compareMethodSig(m, arrayEn)) {
                            deltaArrayEn.put(m, info);
                        }
                    });
                    // batch array en
                    this.batchArrayEns.forEach((batchArrayEn, info) -> {
                        if (compareMethodSig(m, batchArrayEn)) {
                            deltaBatchArrayEn.put(m, info);
                        }
                    });
                    // unsound
                    this.unsoundOps.forEach(unsound -> {
                        if (compareMethodSig(m, unsound)) {
                            deltaUnsoundOp.add(m);
                        }
                    });
                    // inner-s
                    this.innerSources.forEach((inMethod, info) -> {
                        if (compareMethodSig(m, inMethod)) {
                            deltaInnerSrc.put(m, info);
                        }
                    });
                    // coll lambda forEach
                    this.collForEachs.forEach(forEach -> {
                        if (compareMethodSig(m, forEach)) {
                            deltaCollForEach.add(m);
                        }
                    });
                    // map lambda forEach
                    this.mapForEachs.forEach(forEach -> {
                        if (compareMethodSig(m, forEach)) {
                            deltaMapForEach.add(m);
                        }
                    });
                    // list replaceAll
                    this.listReplaceAlls.forEach(replAll -> {
                        if (compareMethodSig(m, replAll)) {
                            deltaListReplaceAll.add(m);
                        }
                    });
                    // map replaceAll
                    this.mapReplaceAlls.forEach(replAll -> {
                        if (compareMethodSig(m, replAll)) {
                            deltaMapReplaceAll.add(m);
                        }
                    });
                    // map merge
                    this.mapMerges.forEach(merge -> {
                        if (compareMethodSig(m, merge)) {
                            deltaMapMerge.add(m);
                        }
                    });
                    // map computeIfPresent
                    this.mapComputeIfPresents.forEach(ifPresent -> {
                        if (compareMethodSig(m, ifPresent)) {
                            deltaMapComputeIfPresent.add(m);
                        }
                    });
                    // map computeIfAbsent
                    this.mapComputeIfAbsents.forEach(ifAbsent -> {
                        if (compareMethodSig(m, ifAbsent)) {
                            deltaMapComputeIfAbsent.add(m);
                        }
                    });
                });
        this.exits.putAll(deltaExit);
        this.entrances.putAll(deltaEntrance);
        this.transfers.putAll(deltaTransfer);
        this.batchEntrances.putAll(deltaBatchEn);
        this.arrayEntrances.putAll(deltaArrayEn);
        this.batchArrayEns.putAll(deltaBatchArrayEn);
        this.unsoundOps.addAll(deltaUnsoundOp);
        this.innerSources.putAll(deltaInnerSrc);
        this.collForEachs.addAll(deltaCollForEach);
        this.mapForEachs.addAll(deltaMapForEach);
        this.listReplaceAlls.addAll(deltaListReplaceAll);
        this.mapReplaceAlls.addAll(deltaMapReplaceAll);
        this.mapMerges.addAll(deltaMapMerge);
        this.mapComputeIfPresents.addAll(deltaMapComputeIfPresent);
        this.mapComputeIfAbsents.addAll(deltaMapComputeIfAbsent);

        this.retTransferCallee.addAll(transfers.keySet());
        this.retTransferCallee.addAll(utilPrivateContAlloc.keySet());
        this.retTransferCallee.addAll(stmSimple2Cont);
        this.retTransferCallee.add(stmCollectorCollect);

        this.passTransferCallee.addAll(collForEachs);
        this.passTransferCallee.addAll(mapForEachs);
        this.passTransferCallee.addAll(listReplaceAlls);
        this.passTransferCallee.addAll(mapReplaceAlls);
        this.passTransferCallee.addAll(mapMerges);
        this.passTransferCallee.addAll(mapComputeIfPresents);
        this.passTransferCallee.addAll(mapComputeIfAbsents);

        // complete array replication scope for stream precise handling
        arrayReplicationScope.add(hierarchy.getClass("java.util.TimSort"));
        arrayReplicationScope.add(hierarchy.getClass("java.util.ComparableTimSort"));
    }

    private void addInScope(JClass jClass) {
        inScopeClass.add(jClass);
        hierarchy.getDirectInnerClassesOf(jClass).forEach(this::addInScope);
    }

    private void addArrayReflectionScope(JClass jClass) {
        arrayReplicationScope.add(jClass);
        hierarchy.getDirectInnerClassesOf(jClass).forEach(this::addArrayReflectionScope);
    }

    private boolean compareMethodSig(JMethod subM, JMethod supM) {
        JClass subClass = subM.getDeclaringClass();
        JClass supClass = supM.getDeclaringClass();
        Map<String, String> typeParamMapping;
        if (supClass != subClass && hierarchy.isSubclass(supClass, subClass)) {
            if (subM.getName().equals(supM.getName())) {
                if (subM.getSubsignature().equals(supM.getSubsignature())) {
                    return true;
                }
                if (subM.getParamCount() != supM.getParamCount()) {
                    return false;
                }
                if (subClass.getGSignature() != null && supClass.getGSignature() != null) {
                    typeParamMapping = compareMethodSigHelper(subClass, supClass);
                    // replace type param in supM's gSig
                    if (typeParamMapping != null && supM.getGSignature() != null) {
                        String replacedGSig = supM.getGSignature().toString();
                        for (Map.Entry<String, String> entry : typeParamMapping.entrySet()) {
                            String t1 = entry.getKey();
                            String t2 = entry.getValue();
                            replacedGSig = replacedGSig.replace(t1, t2);
                        }
                        if (subM.getGSignature() != null && subM.getGSignature().toString().equals(replacedGSig)) {
                            return true;
                        }
                        String[] replacedSigSplit = replacedGSig.split("\\(");
                        String replacedSigWithName = replacedSigSplit[0] + supM.getName()
                                + "(" + replacedSigSplit[1].replace(" ", "");
                        if (subM.getSubsignature().toString().equals(replacedSigWithName)) {
                            return true;
                        }
                    }
                }
                if (subM.getGSignature() != null && supM.getGSignature() != null) {
                    MethodGSignature subGSig = subM.getGSignature();
                    MethodGSignature supGSig = supM.getGSignature();
                    if (subGSig.getParameterSigs().size() == supGSig.getParameterSigs().size()
                            && subGSig.getTypeParams().size() == supGSig.getTypeParams().size()) {
                        for (int i = 0; i < subGSig.getTypeParams().size(); i++) {
                            if (!subGSig.getTypeParams().get(i).equals(supGSig.getTypeParams().get(i))) {
                                return false;
                            }
                        }
                        for (int i = 0; i < subGSig.getParameterSigs().size(); i++) {
                            if (!subGSig.getParameterSigs().get(i).equals(supGSig.getParameterSigs().get(i))) {
                                return false;
                            }
                        }
                        return subGSig.getResultSignature().equals(supGSig.getResultSignature());
                    }
                }
            }
        }
        return false;
    }

    private Map<String, String> compareMethodSigHelper(JClass subClass, JClass supClass) {
        Map<String, String> re = Maps.newMap();
        List<String> mapToSpecificType = new ArrayList<>();
        // sub class has generic sig
        ClassGSignature subGSignature = subClass.getGSignature();
        assert subGSignature != null;
        if (subGSignature.getSuperClass() != null) {
            // find from extended class
            subGSignature.getSuperClass().getSignatures().get(0).typeArgs().forEach(t -> {
                mapToSpecificType.add(t.toString());
            });
        } else if (subGSignature.getSuperInterfaces() != null
                && subGSignature.getSuperInterfaces().size() > 0) {
            // find from implemented class
            subGSignature.getSuperInterfaces().get(0).getSignatures().get(0).typeArgs().forEach(t -> {
                mapToSpecificType.add(t.toString());
            });
        }
        assert supClass.getGSignature() != null;
        List<TypeParameter> supTypeParams = supClass.getGSignature().getTypeParams();
        if (supTypeParams.size() != mapToSpecificType.size()) {
            return null;
        }
        for (int i = 0; i < supTypeParams.size(); i++) {
            re.put(supTypeParams.get(i).toString(), mapToSpecificType.get(i));
        }
        return re;
    }

    static String getMethodSig(String className, String sig) {
        return "<" + className + ": " + sig + ">";
    }

    boolean isConfiguredContainerClass(JClass jClass) {
        return configuredContainers.containsKey(jClass);
    }

    boolean isConfiguredContainerObj(Obj obj) {
        return isConfiguredContainerClass(hierarchy.getClass(obj.getType().getName()));
    }

    boolean isConfiguredRelatedClass(JClass jClass) {
        return configuredRelatedClass.contains(jClass);
    }


    boolean isConfiguredRelatedObj(Obj obj) {
        return isConfiguredRelatedClass(hierarchy.getClass(obj.getType().getName()));
    }

    boolean isContainerObj(Obj obj) {
        return isContainerClass(hierarchy.getClass(obj.getType().getName()));
    }

    boolean isContainerClass(JClass jClass) {
        for (JClass topClass : containerTopClass2Kind.keySet()) {
            if (hierarchy.isSubclass(topClass, jClass)) {
                return true;
            }
        }
        return false;
    }

    ContainerKind getContainerCollectionOrMap(JClass jClass) {
        for (JClass topClass : containerTopClass2Kind.keySet()) {
            if (hierarchy.isSubclass(topClass, jClass)) {
                return containerTopClass2Kind.get(topClass);
            }
        }
        return null;
    }

    boolean isRelatedObj(Obj obj) {
        return isRelatedClass(hierarchy.getClass(obj.getType().getName()));
    }

    /**
     * judge if a class is related, may not be configured
     */
    boolean isRelatedClass(JClass jClass) {
        if (jClass == null) {
            return false;
        }
        for (JClass c : this.hostRelatedPointerClass) {
            if (hierarchy.isSubclass(c, jClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return null if not configured, and the corresponding kind if configured
     */
    ContainerKind getContainerKind(JClass jClass) {
        return configuredContainers.getOrDefault(jClass, null);
    }

    boolean isExit(JMethod method) {
        return exits.containsKey(method);
    }

    Set<RetrieveKind> getExitRetrieveKind(JMethod method) {
        return exits.get(method);
    }

    boolean isEntrance(JMethod method) {
        return entrances.containsKey(method);
    }

    Set<EntranceInfo> getEntranceInfo(JMethod method) {
        return entrances.get(method);
    }

    boolean isTransfer(JMethod method) {
        return transfers.containsKey(method);
    }

    Set<TransferInfo> getTransferInfo(JMethod method) {
        return transfers.get(method);
    }

    boolean isBatchEntrance(JMethod method) {
        return batchEntrances.containsKey(method);
    }

    BatchEnInfo getBatchEntrance(JMethod method) {
        return batchEntrances.get(method);
    }

    boolean isArrayEntrance(JMethod method) {
        return arrayEntrances.containsKey(method);
    }

    ArrayEnInfo getArrayEntrance(JMethod method) {
        return arrayEntrances.get(method);
    }

    boolean isBatchArrayEn(JMethod method) {
        return batchArrayEns.containsKey(method);
    }

    BatchArrayEnInfo getBatchArrayEn(JMethod method) {
        return batchArrayEns.get(method);
    }

    boolean isInnerSourceInMethod(JMethod method) {
        return innerSources.containsKey(method);
    }

    Set<InnerSourceInfo> getInnerSource(JMethod method) {
        return innerSources.get(method);
    }

    /**
     * a reentrancy represents some specific call-site inside a configured
     * container class, where an entrance/batch-entrance is called.
     * In current implementation, hosts are not passed from "receiver" to "this",
     * so reentrancy in most methods do not affect precision, with no necessity
     * to handle. (except for a few in some batch-exit methods and util classes.)
     */
    boolean isReentrancy(Invoke callSite) {
        JMethod caller = callSite.getContainer();
        MethodRef methodRef = callSite.getInvokeExp().getMethodRef();
        if (this.reentrancies.containsKey(caller)) {
            for (JMethod sig : reentrancies.get(caller)) {
                JMethod resolvedRef = methodRef.resolve();
                if (resolvedRef.equals(sig)) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isUnsoundOp(JMethod method) {
        return this.unsoundOps.contains(method);
    }

    boolean isMapEntryObj(Obj obj) {
        JClass objClass = hierarchy.getClass(obj.getType().getName());
        if (objClass == null) {
            return false;
        }
        JClass mapEntry = hierarchy.getClass("java.util.Map$Entry");
        return hierarchy.isSubclass(mapEntry, objClass);
    }

    /**
     * @return all configured subClass of given class
     */
    Set<JClass> getAllConfiguredSubClass(JClass jClass) {
        return hierarchy.getAllSubclassesOf(jClass)
                .stream()
                .filter(this::isConfiguredRelatedClass)
                .collect(Collectors.toSet());
    }

    boolean isInScopeClass(JClass jClass) {
        return inScopeClass.contains(jClass);
    }

    Set<JClass> getInScopeClass() {
        return inScopeClass;
    }

    boolean isArrayReplicationInScope(JClass c) {
        return arrayReplicationScope.contains(c);
    }

    Set<JClass> getArrayRepInScopeClass() {
        return arrayReplicationScope;
    }

    Set<Type> getHostRelatedPointerType() {
        return hostRelatedPointerTypes;
    }

    boolean isStmSimple2Cont(JMethod method) {
        return stmSimple2Cont.contains(method);
    }

    boolean isCollForEach(JMethod method) {
        return collForEachs.contains(method);
    }

    boolean isIterableForEach(JMethod method) {
        return method.equals(iterableForEach);
    }

    boolean isMapForEach(JMethod method) {
        return mapForEachs.contains(method);
    }

    boolean isListReplAll(JMethod method) {
        return listReplaceAlls.contains(method);
    }

    boolean isMapReplAll(JMethod method) {
        return mapReplaceAlls.contains(method);
    }

    boolean isMapMerge(JMethod method) {
        return mapMerges.contains(method);
    }

    boolean isMapComputeIfPresent(JMethod method) {
        return mapComputeIfPresents.contains(method);
    }

    boolean isMapComputeIfAbsent(JMethod method) {
        return mapComputeIfAbsents.contains(method);
    }

    ContainerKind getUtilPrivateContKind(JClass jClass) {
        return utilPrivateConts.get(jClass);
    }

    boolean isUtilPrivateContAlloc(JMethod method) {
        return utilPrivateContAlloc.containsKey(method);
    }

    JClass getUtilPrivateContAllocClass(JMethod method) {
        return utilPrivateContAlloc.get(method);
    }

    boolean isRetEdgeTransferCallee(JMethod method) {
        return retTransferCallee.contains(method);
    }

    boolean isPassEdgeTransferCallee(JMethod method) {
        return passTransferCallee.contains(method);
    }

    boolean isStmCollectorCollect(JMethod method) {
        return method.equals(this.stmCollectorCollect);
    }

    boolean isCollectorCollCreation(JMethod method) {
        return this.collectorCollCreation.contains(method);
    }

    Set<JMethod> getExits() {
        return this.exits.keySet();
    }

    /**
     * recursively compute outer class, return if is a configured map class.
     */
    Set<JClass> getOuterMapClass(JClass allocateInClass, Set<JClass> result) {
        JClass mapClass = hierarchy.getClass("java.util.Map");
        if (hierarchy.isSubclass(mapClass, allocateInClass)) {
            result.add(allocateInClass);
        }
        if (allocateInClass.getOuterClass() != null) {
            JClass outer = allocateInClass.getOuterClass();
            return getOuterMapClass(outer, result);
        } else {
            return result;
        }
    }

    Type getPrivateMapAllocationType(JMethod callee) {
        String simpleName = callee.getName() + "(";
        String typeName;
        if (simpleName.contains("unmodifiableMap(")) {
            typeName = "java.util.Collections$UnmodifiableMap";
        } else if (simpleName.contains("unmodifiableSortedMap(")) {
            typeName = "java.util.Collections$UnmodifiableSortedMap";
        } else if (simpleName.contains("synchronizedMap(")) {
            typeName = "java.util.Collections$SynchronizedMap";
        } else if (simpleName.contains("synchronizedSortedMap(")) {
            typeName = "java.util.Collections$SynchronizedSortedMap";
        } else if (simpleName.contains("checkedMap(")) {
            typeName = "java.util.Collections$CheckedMap";
        } else if (simpleName.contains("checkedSortedMap(")) {
            typeName = "java.util.Collections$CheckedSortedMap";
        } else if (simpleName.contains("emptyMap(")) {
            typeName = "java.util.Collections$EmptyMap";
        } else if (simpleName.contains("singletonMap(")) {
            typeName = "java.util.Collections$SingletonMap";
        } else if (simpleName.contains("unmodifiableNavigableMap(")) {
            typeName = "java.util.Collections$UnmodifiableNavigableMap";
        } else if (simpleName.contains("synchronizedNavigableMap(")) {
            typeName = "java.util.Collections$SynchronizedNavigableMap";
        } else if (simpleName.contains("checkedNavigableMap(")) {
            typeName = "java.util.Collections$CheckedNavigableMap";
        } else {
            return NullType.NULL;
        }
        return typeSystem.getType(typeName);
    }
}
