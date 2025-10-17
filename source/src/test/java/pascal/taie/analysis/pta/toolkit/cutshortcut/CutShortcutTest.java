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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pascal.taie.analysis.Tests;

public class CutShortcutTest {

    private static final String DIR = "cutshortcut/local";
    //advanced:cut-shortcut;
    private static final String ARG = "advanced:cut-shortcut-l;cs:ci";

    @ParameterizedTest
    @ValueSource(strings = {
            // test cases for field access pattern
//            "BasicFieldStore",
//            "MultiFieldStore",
//            "ComplexFieldStore",
//            "ComplexFieldStore2",
//            "ReflectionFieldStore",
//            "NonFieldStore",
//            "BasicFieldLoad",
//            "ComplexFieldLoad",
//            "ReflectionFieldLoad",
//            "NonFieldLoad",
//            "MultiFieldLoad",
//            "ComplexFieldLoad2",
//            "RelayEdge"
            // test cases for local flow pattern
            "BasicLocalFlow",
            "Dispatch",
            "NonLocalFlow",
            "NonLocalFlow2",
            "ComplexLocalFlow",
            "ReflectionLocalFlow",
            "StaticLocalFlow",
            // test cases for container access pattern
//            "ArrayEntrance",
//            "BasicArrayBlockingQueue",
//            "BasicArrayList",
//            "BasicEntrySet",
//            "BasicHashMap",
//            "BasicHashSet",
//            "BasicVector",
//            "BasicPriorityQueue",
//            "BasicSynchronizedCollection",
//            "BasicSynchronizedList",
//            "BasicUnmodifiableCollection",
//            "BasicSynchronizedMap",
//            "BasicDelayQueue",
//            "ContainerAndCS",
//            "ContainerReentrancy",
//            "CustomMap",
//            "CustomSet",
//            "CustomList",
//            "CustomSimpleEntry",
//            "ExitMatch",
//            "MultiContainer",
//            "NestedContainer",
//            "CustomSet3",
//            "MapClone",
//            "UtilMethods",
//            "ArrayAsList",         // need array replication analysis
//            "ReflectiveContainer", // need to change string merge = null in Test.java
            //test cases for stream modeling
//            "SimpleStream",
//            "SimpleOptional",
//            "StreamFromArray",
//            "StreamConcat",
//            "BuildStream",
//            "StreamIterator",
            // test cases for arraycopy
//            "ArrayCopy"
    })
    void test(String mainClass) {
        Tests.testPTA(true, DIR, mainClass, ARG);
    }
}
