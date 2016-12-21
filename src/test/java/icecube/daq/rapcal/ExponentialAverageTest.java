package icecube.daq.rapcal;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests ExponentialAverage.java
 */
public class ExponentialAverageTest
{

    public static final double EPSILON = .0000001;

    @BeforeClass
    public static void setupLogging()
    {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    @Before
    public void setUp() throws Exception
    {
    }

    @AfterClass
    public static void tearDown()
    {
        BasicConfigurator.resetConfiguration();
    }

    @Test
    public void testConstruction()
    {

        ExponentialAverage subject =
                new ExponentialAverage(.1, .001, 5);

        assertTrue("Initial average should be NaN",
                Double.isNaN(subject.getAverage()));

    }

    @Test
    public void testSetupPhase()
    {
        //
        // test behavior of outliers during setup
        //

        int setupSamples = 3;
        double outlierThreshold = .0001;
        double baseValue = 1223.675;
        double sample_1 = baseValue + .1 * outlierThreshold;
        double sample_2 = baseValue + .3 * outlierThreshold;
        double sample_3 = baseValue + .5 * outlierThreshold;
        double sample_4 = baseValue + .7 * outlierThreshold;
        double sample_5 = baseValue + .9 * outlierThreshold;
        double sample_6 = baseValue + .99 * outlierThreshold;

        double outlier_1 = baseValue + 2.1 * outlierThreshold;
        double outlier_2 = baseValue + 2.3 * outlierThreshold;
        double outlier_3 = baseValue + 2.5 * outlierThreshold;
        double outlier_4 = baseValue + 2.7 * outlierThreshold;
        double outlier_5 = baseValue + 2.9 * outlierThreshold;

        double expWt = .1;
        ExponentialAverage subject =
                new ExponentialAverage(expWt, outlierThreshold, setupSamples);

        String wantAccept = "value not accepted during setup";
        String wantReject = "value not rejected during setup";

        // sample - accepted
        assertTrue(wantAccept, subject.add(sample_1));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{sample_1}),
                subject.getAverage(), EPSILON);

        // sample - accepted
        assertTrue(wantAccept, subject.add(sample_2));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{sample_1, sample_2}),
                subject.getAverage(), EPSILON);

        //outlier - accepted
        assertTrue(wantAccept, subject.add(outlier_1));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{outlier_1}),
                subject.getAverage(), EPSILON);

        //outlier - accepted
        assertTrue(wantAccept, subject.add(outlier_2));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{outlier_1, outlier_2}),
                subject.getAverage(), EPSILON);

        //sample - accepted
        assertTrue(wantAccept, subject.add(sample_3));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{sample_3}),
                subject.getAverage(), EPSILON);

        //sample - accepted
        assertTrue(wantAccept, subject.add(sample_4));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{sample_3, sample_4}),
                subject.getAverage(), EPSILON);

        //sample - accepted
        assertTrue(wantAccept, subject.add(sample_5));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{sample_3, sample_4, sample_5}),
                subject.getAverage(), EPSILON);

        //outlier - rejected
        assertFalse(wantReject, subject.add(outlier_3));
        assertEquals(wantReject,
                expAvg(expWt, new double[]{sample_3, sample_4, sample_5}),
                subject.getAverage(), EPSILON);

        //outlier - rejected
        assertFalse(wantReject, subject.add(outlier_4));
        assertEquals(wantReject,
                expAvg(expWt, new double[]{sample_3, sample_4, sample_5}),
                subject.getAverage(), EPSILON);

        // sample - accepted
        assertTrue(wantAccept, subject.add(sample_6));
        assertEquals(wantAccept,
                expAvg(expWt, new double[]{sample_3, sample_4, sample_5, sample_6}),
                subject.getAverage(), EPSILON);

        //outlier - rejected
        assertFalse(wantReject, subject.add(outlier_5));
        assertEquals(wantReject,
                expAvg(expWt, new double[]{sample_3, sample_4, sample_5, sample_6}),
                subject.getAverage(), EPSILON);
    }

    static double expAvg(double wt, double[] samples)
    {
        double avg = samples[0];
        for (int i = 1; i < samples.length; i++)
        {
            avg = (avg + wt * samples[i]) / (1.0 + wt);
        }
        return avg;
    }
}
