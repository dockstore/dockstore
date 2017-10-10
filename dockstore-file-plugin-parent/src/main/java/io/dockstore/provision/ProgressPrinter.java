/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.provision;

import java.io.Console;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * A printer of the progress for file provisoning plugins.
 *
 * @author dyuen
 */
public class ProgressPrinter {
    private static final int SIZE_OF_PROGRESS_BAR = 50;
    private boolean printedBefore = false;
    private BigDecimal progress = new BigDecimal(0);

    /**
     * Call to report on progress
     *
     * @param totalBytesTransferred bytes transferred so far
     * @param streamSize            total bytes to be transferred
     */
    public void handleProgress(long totalBytesTransferred, long streamSize) {

        BigDecimal numerator = BigDecimal.valueOf(totalBytesTransferred);
        BigDecimal denominator = BigDecimal.valueOf(streamSize);
        BigDecimal fraction = numerator.divide(denominator, new MathContext(2, RoundingMode.HALF_EVEN));
        if (fraction.equals(progress)) {
            /* don't bother refreshing if no progress made */
            return;
        }

        BigDecimal outOfTwenty = fraction.multiply(new BigDecimal(SIZE_OF_PROGRESS_BAR));
        BigDecimal percentage = fraction.movePointRight(2);
        StringBuilder builder = new StringBuilder();
        if (printedBefore) {
            builder.append('\r');
        }

        builder.append("[");
        for (int i = 0; i < SIZE_OF_PROGRESS_BAR; i++) {
            if (i < outOfTwenty.intValue()) {
                builder.append("#");
            } else {
                builder.append(" ");
            }
        }

        builder.append("] ");
        builder.append(percentage.setScale(0, BigDecimal.ROUND_HALF_EVEN).toPlainString()).append("%");

        Console console = System.console();
        if (console != null || numerator.equals(denominator)) {
            System.out.print(builder);
        }
        // track progress
        printedBefore = true;
        progress = fraction;
    }
}
