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

package ai.spice;

import java.util.concurrent.ExecutionException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

public class UserAgentTest
        extends TestCase {
    public void testUserAgent() throws ExecutionException, InterruptedException {
        // use a regex to match the expected user agent
        String regex = "spice-java \\d+\\.\\d+\\.\\d+ \\((Linux|Windows|Darwin)/[\\d\\w\\.\\-\\_]+ (x86_64|aarch64|i386)\\)";
        Pattern pattern = Pattern.compile(regex);

        String userAgent = Config.getUserAgent();
        Matcher matcher = pattern.matcher(userAgent);

        assertTrue("User agent did not match the expected pattern: " + userAgent, matcher.matches());
    }
}
