/*
Copyright 2024 The Spice.ai OSS Authors

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

package ai.spice.example;

import org.apache.arrow.flight.FlightStream;

import ai.spice.SpiceClient;

/**
 * Example of using Spice.ai client to query data from Spice.ai Cloud
 * _JAVA_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED" mvn exec:java -Dexec.mainClass="ai.spice.example.ExampleSpiceCloudPlatform"
 */
public class ExampleSpiceCloudPlatform {

    // Create free Spice.ai account to obtain API_KEY: https://spice.ai/login
    final static String API_KEY = "api-key";

    public static void main(String[] args) {
        try {
            SpiceClient client = SpiceClient.builder()
                    .withApiKey(API_KEY)
                    .withSpiceCloud()
                    .build();

            FlightStream res = client.query("SELECT * FROM eth.recent_blocks LIMIT 10;");

            while (res.next()) {
                System.out.println(res.getRoot().contentToTSVString());
            }
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }
}
