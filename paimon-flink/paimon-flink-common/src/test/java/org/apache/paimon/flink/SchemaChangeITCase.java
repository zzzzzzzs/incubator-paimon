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

package org.apache.paimon.flink;

import org.apache.paimon.testutils.assertj.AssertionUtils;

import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ITCase for schema changes. */
public class SchemaChangeITCase extends CatalogITCaseBase {

    // TODO cover more cases.
    @Test
    public void testAddColumn() {
        sql("CREATE TABLE T (a STRING, b DOUBLE, c FLOAT)");
        sql("INSERT INTO T VALUES('aaa', 1.2, 3.4)");
        sql("ALTER TABLE T ADD d INT");
        List<Row> result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647),\n"
                                + "  `b` DOUBLE,\n"
                                + "  `c` FLOAT,\n"
                                + "  `d` INT\n"
                                + ")");
        sql("INSERT INTO T VALUES('bbb', 4.5, 5.6, 5)");
        result = sql("SELECT * FROM T");
        assertThat(result.toString()).isEqualTo("[+I[aaa, 1.2, 3.4, null], +I[bbb, 4.5, 5.6, 5]]");

        // add column with after position
        sql("ALTER TABLE T ADD e INT AFTER b");
        result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647),\n"
                                + "  `b` DOUBLE,\n"
                                + "  `e` INT,\n"
                                + "  `c` FLOAT,\n"
                                + "  `d` INT");
        sql("INSERT INTO T VALUES('ccc', 2.3, 6, 5.6, 5)");
        result = sql("SELECT * FROM T");
        assertThat(result.toString())
                .isEqualTo(
                        "[+I[aaa, 1.2, null, 3.4, null], +I[bbb, 4.5, null, 5.6, 5], +I[ccc, 2.3, 6, 5.6, 5]]");

        // add column with first position
        sql("ALTER TABLE T ADD f STRING FIRST");
        result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `f` VARCHAR(2147483647),\n"
                                + "  `a` VARCHAR(2147483647),\n"
                                + "  `b` DOUBLE,\n"
                                + "  `e` INT,\n"
                                + "  `c` FLOAT,\n"
                                + "  `d` INT");

        sql("INSERT INTO T VALUES('flink', 'fff', 45.34, 4, 2.45, 12)");
        result = sql("SELECT * FROM T");
        assertThat(result.toString())
                .isEqualTo(
                        "[+I[null, aaa, 1.2, null, 3.4, null], +I[null, bbb, 4.5, null, 5.6, 5],"
                                + " +I[null, ccc, 2.3, 6, 5.6, 5], +I[flink, fff, 45.34, 4, 2.45, 12]]");

        // add multiple columns.
        sql("ALTER TABLE T ADD ( g INT, h BOOLEAN ) ");
        sql("INSERT INTO T VALUES('ggg', 'hhh', 23.43, 6, 2.34, 34, 23, true)");
        result = sql("SELECT * FROM T");
        assertThat(result.toString())
                .isEqualTo(
                        "[+I[null, aaa, 1.2, null, 3.4, null, null, null], +I[null, bbb, 4.5, null, 5.6, 5, null, null],"
                                + " +I[null, ccc, 2.3, 6, 5.6, 5, null, null], +I[flink, fff, 45.34, 4, 2.45, 12, null, null],"
                                + " +I[ggg, hhh, 23.43, 6, 2.34, 34, 23, true]]");
    }

    @Test
    public void testDropColumn() {
        sql(
                "CREATE TABLE T (a STRING PRIMARY KEY NOT ENFORCED, b STRING, c STRING, d INT, e FLOAT)");
        sql("INSERT INTO T VALUES('aaa', 'bbb', 'ccc', 10, 3.4)");
        sql("ALTER TABLE T DROP e");
        sql("INSERT INTO T VALUES('ddd', 'eee', 'fff', 20)");
        List<Row> result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `c` VARCHAR(2147483647),\n"
                                + "  `d` INT,");
        result = sql("SELECT * FROM T");
        assertThat(result.toString()).isEqualTo("[+I[aaa, bbb, ccc, 10], +I[ddd, eee, fff, 20]]");

        sql("ALTER TABLE T DROP (c, d)");
        result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `b` VARCHAR(2147483647),");

        sql("INSERT INTO T VALUES('ggg', 'hhh')");
        result = sql("SELECT * FROM T");
        assertThat(result.toString()).isEqualTo("[+I[aaa, bbb], +I[ddd, eee], +I[ggg, hhh]]");
    }

    @Test
    public void testRenameColumn() {
        sql("CREATE TABLE T (a STRING PRIMARY KEY NOT ENFORCED, b STRING, c STRING)");
        sql("INSERT INTO T VALUES('paimon', 'bbb', 'ccc')");
        sql("ALTER TABLE T RENAME c TO c1");
        List<Row> result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `c1` VARCHAR(2147483647)");
        result = sql("SELECT a, b, c1 FROM T");
        assertThat(result.toString()).isEqualTo("[+I[paimon, bbb, ccc]]");

        // column do not exist.
        assertThatThrownBy(() -> sql("ALTER TABLE T RENAME d TO d1"))
                .hasMessageContaining("The column `d` does not exist in the base table.");

        // target column exist.
        assertThatThrownBy(() -> sql("ALTER TABLE T RENAME a TO b"))
                .hasMessageContaining("The column `b` already existed in table schema.");
    }

    @Test
    public void testDropPrimaryKey() {
        sql("CREATE TABLE T (a STRING PRIMARY KEY NOT ENFORCED, b STRING, c STRING)");
        assertThatThrownBy(() -> sql("ALTER TABLE T DROP a"))
                .hasMessageContaining(
                        "Failed to execute ALTER TABLE statement.\n"
                                + "The column `a` is used as the primary key.");
    }

    @Test
    public void testDropPartitionKey() {
        sql(
                "CREATE TABLE MyTable (\n"
                        + "    user_id BIGINT,\n"
                        + "    item_id BIGINT,\n"
                        + "    behavior STRING,\n"
                        + "    dt STRING,\n"
                        + "    hh STRING,\n"
                        + "    PRIMARY KEY (dt, hh, user_id) NOT ENFORCED\n"
                        + ") PARTITIONED BY (dt, hh)");
        assertThatThrownBy(() -> sql("ALTER TABLE MyTable DROP dt"))
                .hasMessageContaining(
                        "Failed to execute ALTER TABLE statement.\n"
                                + "The column `dt` is used as the partition keys.");
    }

    @Test
    public void testModifyColumnType() {
        sql(
                "CREATE TABLE T (a STRING PRIMARY KEY NOT ENFORCED, b STRING, c STRING, d INT, e FLOAT)");
        sql("INSERT INTO T VALUES('paimon', 'bbb', 'ccc', 1, 3.4)");
        sql("ALTER TABLE T MODIFY e DOUBLE");
        sql("INSERT INTO T VALUES('flink', 'ddd', 'eee', 4, 6.7)");
        List<Row> result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `c` VARCHAR(2147483647),\n"
                                + "  `d` INT,\n"
                                + "  `e` DOUBLE,");
        result = sql("SELECT * FROM T");
        assertThat(result.toString())
                .isEqualTo(
                        "[+I[flink, ddd, eee, 4, 6.7], +I[paimon, bbb, ccc, 1, 3.4000000953674316]]");

        assertThatThrownBy(() -> sql("ALTER TABLE T MODIFY c DOUBLE"))
                .hasMessageContaining(
                        "Could not execute ALTER TABLE PAIMON.default.T\n" + "  MODIFY `c` DOUBLE");
    }

    @Test
    public void testModifyColumnPosition() {
        sql(
                "CREATE TABLE T (a STRING PRIMARY KEY NOT ENFORCED, b STRING, c STRING, d INT, e DOUBLE)");
        sql("INSERT INTO T VALUES('paimon', 'bbb', 'ccc', 1, 3.4)");
        sql("ALTER TABLE T MODIFY b STRING FIRST");
        List<Row> result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `c` VARCHAR(2147483647),\n"
                                + "  `d` INT,\n"
                                + "  `e` DOUBLE,");

        sql("INSERT INTO T VALUES('aaa', 'flink', 'ddd', 2, 5.7)");
        result = sql("SELECT * FROM T");
        assertThat(result.toString())
                .isEqualTo("[+I[aaa, flink, ddd, 2, 5.7], +I[bbb, paimon, ccc, 1, 3.4]]");

        sql("ALTER TABLE T MODIFY e DOUBLE AFTER c");
        result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `c` VARCHAR(2147483647),\n"
                                + "  `e` DOUBLE,\n"
                                + "  `d` INT,");

        sql("INSERT INTO T VALUES('sss', 'ggg', 'eee', 4.7, 10)");
        result = sql("SELECT * FROM T");
        assertThat(result.toString())
                .isEqualTo(
                        "[+I[aaa, flink, ddd, 5.7, 2], +I[sss, ggg, eee, 4.7, 10], +I[bbb, paimon, ccc, 3.4, 1]]");

        //  move self to first test
        assertThatThrownBy(() -> sql("ALTER TABLE T MODIFY b STRING FIRST"))
                .satisfies(
                        AssertionUtils.anyCauseMatches(
                                UnsupportedOperationException.class,
                                "Cannot move itself for column b"));

        //  move self to after test
        assertThatThrownBy(() -> sql("ALTER TABLE T MODIFY b STRING AFTER b"))
                .satisfies(
                        AssertionUtils.anyCauseMatches(
                                UnsupportedOperationException.class,
                                "Cannot move itself for column b"));

        // missing column
        assertThatThrownBy(() -> sql("ALTER TABLE T MODIFY h STRING FIRST"))
                .hasMessageContaining(
                        "Try to modify a column `h` which does not exist in the table");

        assertThatThrownBy(() -> sql("ALTER TABLE T MODIFY h STRING AFTER d"))
                .hasMessageContaining(
                        "Try to modify a column `h` which does not exist in the table");
    }

    @Test
    public void testModifyNullability() {
        sql(
                "CREATE TABLE T (a STRING PRIMARY KEY NOT ENFORCED, b STRING, c STRING, d INT, e FLOAT NOT NULL)");
        List<Row> result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `c` VARCHAR(2147483647),\n"
                                + "  `d` INT,\n"
                                + "  `e` FLOAT NOT NULL,");

        sql("ALTER TABLE T MODIFY e FLOAT");
        result = sql("SHOW CREATE TABLE T");
        assertThat(result.toString())
                .contains(
                        "CREATE TABLE `PAIMON`.`default`.`T` (\n"
                                + "  `a` VARCHAR(2147483647) NOT NULL,\n"
                                + "  `b` VARCHAR(2147483647),\n"
                                + "  `c` VARCHAR(2147483647),\n"
                                + "  `d` INT,\n"
                                + "  `e` FLOAT");

        assertThatThrownBy(() -> sql("ALTER TABLE T MODIFY c STRING NOT NULL"))
                .hasMessageContaining(
                        "Could not execute ALTER TABLE PAIMON.default.T\n"
                                + "  MODIFY `c` STRING NOT NULL");
    }

    @Test
    public void testSetAndRemoveOption() throws Exception {
        sql("CREATE TABLE T (a STRING, b STRING, c STRING)");
        sql("ALTER TABLE T SET ('xyc'='unknown1', 'abc'='unknown2')");

        Map<String, String> options = table("T").getOptions();
        assertThat(options).containsEntry("xyc", "unknown1");
        assertThat(options).containsEntry("abc", "unknown2");

        sql("ALTER TABLE T RESET ('xyc', 'abc')");

        options = table("T").getOptions();
        assertThat(options).doesNotContainKey("xyc");
        assertThat(options).doesNotContainKey("abc");
    }

    @Test
    public void testSetAndResetImmutableOptions() throws Exception {
        // bucket-key is immutable
        sql("CREATE TABLE T1 (a STRING, b STRING, c STRING)");

        assertThatThrownBy(() -> sql("ALTER TABLE T1 SET ('bucket-key' = 'c')"))
                .getRootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Change 'bucket-key' is not supported yet.");

        sql("CREATE TABLE T2 (a STRING, b STRING, c STRING) WITH ('bucket-key' = 'c')");
        assertThatThrownBy(() -> sql("ALTER TABLE T2 RESET ('bucket-key')"))
                .getRootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Change 'bucket-key' is not supported yet.");

        // write-mode is immutable
        assertThatThrownBy(() -> sql("ALTER TABLE T1 SET ('write-mode' = 'append-only')"))
                .getRootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Change 'write-mode' is not supported yet.");

        sql("CREATE TABLE T3 (a STRING, b STRING, c STRING) WITH ('write-mode' = 'append-only')");
        assertThatThrownBy(() -> sql("ALTER TABLE T3 RESET ('write-mode')"))
                .getRootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Change 'write-mode' is not supported yet.");

        // merge-engine is immutable
        sql(
                "CREATE TABLE T4 (a STRING, b STRING, c STRING) WITH ('merge-engine' = 'partial-update')");
        assertThatThrownBy(() -> sql("ALTER TABLE T4 RESET ('merge-engine')"))
                .getRootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Change 'merge-engine' is not supported yet.");

        // sequence.field is immutable
        sql("CREATE TABLE T5 (a STRING, b STRING, c STRING) WITH ('sequence.field' = 'b')");
        assertThatThrownBy(() -> sql("ALTER TABLE T5 SET ('sequence.field' = 'c')"))
                .getRootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Change 'sequence.field' is not supported yet.");
    }
}
