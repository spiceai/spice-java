/*
Copyright 2026 The Spice.ai OSS Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ai.spice;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * The result of a {@link SpiceClient#nsql(NsqlRequest)} call: the SQL the
 * runtime's configured LLM generated from a natural-language query, and the
 * rows it produced.
 */
public class NsqlResponse {
    @SerializedName("sql")
    private String sql;

    @SerializedName("row_count")
    private int rowCount;

    @SerializedName("schema")
    private NsqlSchema schema;

    @SerializedName("data")
    private List<Map<String, Object>> data;

    /**
     * The query the model generated. Worth logging: a surprising result is
     * usually a surprising query.
     *
     * @return the generated SQL
     */
    public String getSql() {
        return this.sql;
    }

    /**
     * The number of rows returned.
     *
     * @return the row count
     */
    public int getRowCount() {
        return this.rowCount;
    }

    /**
     * The schema describing the columns in {@link #getData()}.
     *
     * @return the schema, or an empty one when the runtime omitted it
     */
    public NsqlSchema getSchema() {
        return this.schema == null ? new NsqlSchema() : this.schema;
    }

    /**
     * The rows, each keyed by column name. Values are decoded from JSON, so
     * they carry JSON's types rather than the Arrow types named in {@link
     * #getSchema()} — numbers arrive as {@code Double}. Use {@link
     * SpiceClient#nsqlGenerateSql(NsqlRequest)} with {@code query} or {@code
     * queryWithParams} when Arrow-typed results matter.
     *
     * @return the rows, or an empty list when the runtime returned none
     */
    public List<Map<String, Object>> getData() {
        return this.data == null ? Collections.emptyList() : this.data;
    }
}
