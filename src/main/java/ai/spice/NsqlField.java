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

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * Describes one column of an {@link NsqlResponse}.
 */
public class NsqlField {
    @SerializedName("name")
    private String name;

    @SerializedName("data_type")
    private JsonElement dataType;

    @SerializedName("nullable")
    private boolean nullable;

    /**
     * The column name.
     *
     * @return the column name
     */
    public String getName() {
        return this.name;
    }

    /**
     * The column's Arrow type, in the runtime's raw JSON encoding. Simple
     * types encode as a quoted string ({@code "Utf8"}, {@code "Int64"});
     * parameterized ones as an object (for example
     * {@code {"Timestamp":["Nanosecond",null]}}) — returned as-is rather than
     * modeled, since the shape varies by type.
     *
     * @return the raw data type
     */
    public JsonElement getDataType() {
        return this.dataType;
    }

    /**
     * Whether the column admits nulls.
     *
     * @return true if the column is nullable
     */
    public boolean isNullable() {
        return this.nullable;
    }
}
