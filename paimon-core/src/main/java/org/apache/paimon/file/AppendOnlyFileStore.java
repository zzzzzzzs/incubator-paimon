/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.file;

import org.apache.paimon.CoreOptions;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.file.operation.AppendOnlyFileStoreRead;
import org.apache.paimon.file.operation.AppendOnlyFileStoreScan;
import org.apache.paimon.file.operation.AppendOnlyFileStoreWrite;
import org.apache.paimon.file.schema.SchemaManager;
import org.apache.paimon.format.FileFormatDiscover;
import org.apache.paimon.fs.FileIO;
import org.apache.paimon.types.RowType;

import java.util.Comparator;

/** {@link FileStore} for reading and writing {@link InternalRow}. */
public class AppendOnlyFileStore extends AbstractFileStore<InternalRow> {

    private final RowType bucketKeyType;
    private final RowType rowType;

    public AppendOnlyFileStore(
            FileIO fileIO,
            SchemaManager schemaManager,
            long schemaId,
            CoreOptions options,
            RowType partitionType,
            RowType bucketKeyType,
            RowType rowType) {
        super(fileIO, schemaManager, schemaId, options, partitionType);
        this.bucketKeyType = bucketKeyType;
        this.rowType = rowType;
    }

    @Override
    public AppendOnlyFileStoreScan newScan() {
        return newScan(false);
    }

    @Override
    public AppendOnlyFileStoreRead newRead() {
        return new AppendOnlyFileStoreRead(
                fileIO,
                schemaManager,
                schemaId,
                rowType,
                FileFormatDiscover.of(options),
                pathFactory());
    }

    @Override
    public AppendOnlyFileStoreWrite newWrite(String commitUser) {
        return new AppendOnlyFileStoreWrite(
                fileIO,
                newRead(),
                schemaId,
                commitUser,
                rowType,
                pathFactory(),
                snapshotManager(),
                newScan(true),
                options);
    }

    private AppendOnlyFileStoreScan newScan(boolean checkNumOfBuckets) {
        return new AppendOnlyFileStoreScan(
                partitionType,
                bucketKeyType.getFieldCount() == 0 ? rowType : bucketKeyType,
                rowType,
                snapshotManager(),
                schemaManager,
                schemaId,
                manifestFileFactory(),
                manifestListFactory(),
                options.bucket(),
                checkNumOfBuckets);
    }

    @Override
    public Comparator<InternalRow> newKeyComparator() {
        return null;
    }
}