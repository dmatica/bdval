/*
 * Copyright (C) 2008-2010 Institute for Computational Biomedicine,
 *                         Weill Medical College of Cornell University
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bdval.signalquality;

/**
 * The result from a Kolmogorov-Smirnov Test.
 *
 * @author Kevin Dorff
 */
public class KolmogorovSmirnovTestResult {
    /**
     * The p-Value.
     */
    private final double pValue;
    /**
     * The p-Value.
     */
    private final double testStatistic;

    /**
     * Create a KolmogorovSmirnovTestResult.
     * @param pValue the pValue for the result
     * @param testStatistic the test statistic for the result
     */
    public KolmogorovSmirnovTestResult(final double pValue, final double testStatistic) {
        super();
        this.pValue = pValue;
        this.testStatistic = testStatistic;
    }

    /**
     * Get the p-value.
     * @return the p-value.
     */
    public double getPValue() {
        return pValue;
    }

    /**
     * Get the test statistic.
     * @return the test statistic
     */
    public double getTestStatistic() {
        return testStatistic;
    }
}
