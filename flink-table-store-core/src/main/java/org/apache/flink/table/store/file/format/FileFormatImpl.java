/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.store.file.format;

import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.connector.file.table.factories.BulkReaderFormatFactory;
import org.apache.flink.connector.file.table.factories.BulkWriterFormatFactory;
import org.apache.flink.connector.file.table.format.BulkDecodingFormat;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.factories.FactoryUtil;

/** A {@link FileFormat} which discovers reader and writer from format identifier. */
public class FileFormatImpl extends FileFormat {

    private final ClassLoader classLoader;
    private final String formatIdentifier;
    private final ReadableConfig formatOptions;

    public FileFormatImpl(
            ClassLoader classLoader, String formatIdentifier, ReadableConfig formatOptions) {
        this.classLoader = classLoader;
        this.formatIdentifier = formatIdentifier;
        this.formatOptions = formatOptions;
    }

    protected BulkDecodingFormat<RowData> getDecodingFormat() {
        return FactoryUtil.discoverFactory(
                        classLoader, BulkReaderFormatFactory.class, formatIdentifier)
                .createDecodingFormat(null, formatOptions); // context is useless
    }

    @Override
    protected EncodingFormat<BulkWriter.Factory<RowData>> getEncodingFormat() {
        return FactoryUtil.discoverFactory(
                        classLoader, BulkWriterFormatFactory.class, formatIdentifier)
                .createEncodingFormat(null, formatOptions); // context is useless
    }
}