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

import pascal.taie.Main;
import pascal.taie.analysis.misc.IRDumper;
import picocli.CommandLine;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ArtifactEntry {

    private static final String BENCHMARK_HOME = "java-benchmarks";

    private static final String BENCHMARK_INFO = "java-benchmarks/benchmark-info.yml";

    private static final String STM_BENCH_HOME = "stream-benchmarks";

    private static final String STM_BENCH_INFO = "stream-benchmarks/benchmark-info.yml";

    private static final Map<String, BenchmarkInfo> benchmarkInfos =
            BenchmarkInfo.load(BENCHMARK_INFO);

    private static final Map<String, BenchmarkInfo> stmBenchmarkInfos =
            BenchmarkInfo.load(STM_BENCH_INFO);

    private static final boolean DUMP_IR = true;

    private static final boolean DUMP_RECALL = true;

    @CommandLine.Option(names = "-cs", defaultValue = "ci")
    private String cs;

    @CommandLine.Option(names = "-java", defaultValue = "6")
    private int jdk;

    @CommandLine.Option(names = "-advanced", defaultValue = "null")
    private String advanced;

    @CommandLine.Option(names = "-stream", defaultValue = "0")
    private int stream;

    @CommandLine.Option(names = "-distinguishStringConstant", defaultValue = "null")
    private String distinguishStrCons;

    @CommandLine.Parameters
    private List<String> benchmarks;

    public static void main(String[] args) {
        ArtifactEntry runner = CommandLine.populateCommand(new ArtifactEntry(), args);
        runner.runAll();
    }

    private void runAll() {
        if (benchmarks == null) {
            throw new IllegalArgumentException("benchmarks are not given");
        }
        benchmarks.forEach(this::run);
    }

    private void run(String benchmark) {
        System.out.println("\nAnalyzing " + benchmark);
        Main.main(composeArgs(benchmark));
    }

    private String[] composeArgs(String benchmark) {
        BenchmarkInfo info = stream == 0 ? benchmarkInfos.get(benchmark) : stmBenchmarkInfos.get(benchmark);
        List<String> args = new ArrayList<>();
        int jdkVersion = jdk != 0 ? jdk : info.jdk();
        Collections.addAll(args,
                "-java", Integer.toString(jdkVersion),
                "-acp", buildClassPath(info.apps()),
                "-cp", buildClassPath(info.libs()),
                "-wc",
                "-m", info.main());
        if (info.allowPhantom()) {
            args.add("--allow-phantom");
        }
        if (stream == 1 || stream == 2) {
            Collections.addAll(args, "-ap");
        }
        args.add("--pre-build-ir");
        Map<String, String> ptaArgs = getPtaArgs(info);
        Collections.addAll(args,
                "-a", "pta=" + ptaArgs.entrySet()
                        .stream()
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining(";")),
                "-a", "may-fail-cast",
                "-a", "poly-call");
        if (DUMP_IR) {
            Collections.addAll(args, "-a", IRDumper.ID);
        }
        if (DUMP_RECALL) {
            Collections.addAll(args, "-a",
                    "cg=dump-methods:true;dump-call-edges:true");
        }
        return args.toArray(new String[0]);
    }

    private Map<String, String> getPtaArgs(BenchmarkInfo info) {
        Map<String, String> ptaArgs = new HashMap<>();
        ptaArgs.put("distinguish-string-constants", distinguishStrCons);
        ptaArgs.put("merge-string-objects", "false");
        ptaArgs.put("cs", cs);
        ptaArgs.put("advanced", advanced);
        if (stream == 0) {
            ptaArgs.put("reflection-inference", "null");
            ptaArgs.put("reflection-log", new File(BENCHMARK_HOME, info.reflectionLog()).toString());
        } else if (stream == 1){
            ptaArgs.put("reflection-inference", "string-constant");
            ptaArgs.put("dump-ci", "true");
        } else if (stream == 2) {
            ptaArgs.put("implicit-entries", "true");
            ptaArgs.put("reflection-inference", "solar");
            ptaArgs.put("dump-ci", "true");
        }
        return ptaArgs;
    }

    private String buildClassPath(List<String> paths) {
        return paths.stream()
                .map(this::extendCP)
                .flatMap(List::stream)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private List<String> extendCP(String path) {
        File file = new File(stream == 0 ? BENCHMARK_HOME : STM_BENCH_HOME, path);
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
