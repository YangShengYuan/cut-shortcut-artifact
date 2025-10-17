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

package pascal.taie.analysis.pta;

import pascal.taie.Main;
import pascal.taie.analysis.misc.IRDumper;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@CommandLine.Command
public class StreamTestRunner {
    private static final String TEST_HOME = "stream-benchmarks";

    private static final String TEST_INFO = "stream-benchmarks/benchmark-info.yml";

    private static final Map<String, BenchmarkInfo> testInfos =
            BenchmarkInfo.load(TEST_INFO);

    private static final boolean DUMP_IR = true;

    @CommandLine.Option(names = "-cs", defaultValue = "ci")
    private String cs;

    @CommandLine.Option(names = "-java", defaultValue = "8")
    private int jdk;

    @CommandLine.Option(names = "-advanced", defaultValue = "null")
    private String advanced;

    @CommandLine.Option(names = "-distinguishStringConstant", defaultValue = "null")
    private String distinguishStrCons;

    @CommandLine.Parameters
    private List<String> tests;

    public static void main(String[] args) {
        StreamTestRunner runner = CommandLine.populateCommand(new StreamTestRunner(), args);
        runner.runAll();
    }

    private void runAll() {
        if (tests == null) {
            throw new IllegalArgumentException("testcases are not given");
        }
        tests.forEach(this::run);
    }

    private void run(String test) {
        System.out.println("\nAnalyzing " + test);
        Main.main(composeArgs(test));
    }

    private String[] composeArgs(String benchmark) {
        BenchmarkInfo info = testInfos.get(benchmark);
        List<String> args = new ArrayList<>();
        int jdkVersion = jdk;
        Collections.addAll(args,
                "-java", Integer.toString(jdkVersion),
                "-acp", buildClassPath(info.apps()),
                "-cp", buildClassPath(info.libs()),
                "-ap",
                "-wc",
                "-m", info.main());
        if (info.allowPhantom()) {
            args.add("--allow-phantom");
        }
        Map<String, String> ptaArgs = Map.of(
                "dump", "false",
                "dump-ci", "true",
                "implicit-entries", "true",
                "only-app", "false",
                "distinguish-string-constants", distinguishStrCons,
                "merge-string-objects", "false",
                "cs", cs,
                "advanced", advanced,
                "reflection-inference", "solar");
        Collections.addAll(args,
                "-a", "pta=" + ptaArgs.entrySet()
                        .stream()
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining(";")),
                "-a", "may-fail-cast",
                "-a", "poly-call");
        if (DUMP_IR) {
            // dump IR
            Collections.addAll(args, "-a", IRDumper.ID);
        }
        return args.toArray(new String[0]);
    }

    private String buildClassPath(List<String> paths) {
        return paths.stream()
                .map(this::extendCP)
                .flatMap(List::stream)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private List<String> extendCP(String path) {
        File file = new File(TEST_HOME, path);
        List<String> paths = new ArrayList<>();
        if (isJar(file)) {
            paths.add(file.toString());
        } else if (file.isDirectory()) {
            paths.add(file.toString());
            for (File item : Objects.requireNonNull(file.listFiles())) {
                if (isJar(item)) {
                    paths.add(item.toString());
                }
            }
        } else {
            throw new RuntimeException(path + " is neither a directory nor a JAR");
        }
        return paths;
    }

    private static boolean isJar(File file) {
        return file.getName().endsWith(".jar");
    }
}
