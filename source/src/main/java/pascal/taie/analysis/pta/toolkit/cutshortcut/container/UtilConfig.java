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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import pascal.taie.config.ConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Configuration for a Util Class, e.g., java.util.Collections
 */
record UtilConfig(@JsonProperty String className,
                  @JsonProperty List<ContainerConfiger.UtilPrivateCont> privates,
                  @JsonProperty List<ContainerConfiger.UtilBatchEn> batchEns,
                  @JsonProperty List<ContainerConfiger.UtilArrayEn> arrayEns,
                  @JsonProperty List<ContainerConfiger.UtilEntrance> entrances,
                  @JsonProperty List<ContainerConfiger.UtilTransfer> transfers,
                  @JsonProperty List<ContainerConfiger.Reentrancy> reentrancy) {

    @JsonCreator
    UtilConfig(
            @JsonProperty("util-class") String className,
            @JsonProperty("private-class") List<ContainerConfiger.UtilPrivateCont> privates,
            @JsonProperty("util-batch-en") List<ContainerConfiger.UtilBatchEn> batchEns,
            @JsonProperty("util-array-en") List<ContainerConfiger.UtilArrayEn> arrayEns,
            @JsonProperty("util-entrance") List<ContainerConfiger.UtilEntrance> entrances,
            @JsonProperty("util-transfer") List<ContainerConfiger.UtilTransfer> transfers,
            @JsonProperty("reentrancy") List<ContainerConfiger.Reentrancy> reentrancy) {
        this.className = className;
        this.privates = privates;
        this.batchEns = batchEns;
        this.arrayEns = arrayEns;
        this.entrances = entrances;
        this.transfers = transfers;
        this.reentrancy = reentrancy;
    }

    static List<UtilConfig> parseConfigs(InputStream content) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JavaType type = mapper.getTypeFactory()
                .constructCollectionType(List.class, UtilConfig.class);
        try {
            return mapper.readValue(content, type);
        } catch (IOException e) {
            throw new ConfigException("Failed to read analysis config file", e);
        }
    }

    @Override
    public String className() {
        return className;
    }

    @Override
    public List<ContainerConfiger.UtilPrivateCont> privates() {
        return privates;
    }

    @Override
    public List<ContainerConfiger.UtilBatchEn> batchEns() {
        return batchEns;
    }

    @Override
    public List<ContainerConfiger.UtilArrayEn> arrayEns() {
        return arrayEns;
    }

    @Override
    public List<ContainerConfiger.UtilEntrance> entrances() {
        return entrances;
    }

    @Override
    public List<ContainerConfiger.UtilTransfer> transfers() {
        return transfers;
    }

    public List<ContainerConfiger.Reentrancy> reentrancy() {
        return reentrancy;
    }
}
