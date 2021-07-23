package net.jgp.books.spark.ch05.lab100_pi_compute;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.api.java.function.ReduceFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Computes Pi.
 *
 * @author jgp
 */
public class PiComputeApp implements Serializable {
    private static final long serialVersionUID = -1546L;
    private static long counter = 0;

    /**
     * Mapper class, creates the map of dots
     *
     * @author jgp
     */
    private final class DartMapper
            implements MapFunction<Row, Integer> {
        private static final long serialVersionUID = 38446L;

        @Override
        public Integer call(Row r) throws Exception {
            double x = Math.random() * 2 - 1;
            double y = Math.random() * 2 - 1;
            counter++;
            if (counter % 100000 == 0) {
                System.out.println("" + counter + " darts thrown so far");
            }
            return (x * x + y * y <= 1) ? 1 : 0;
        }
    }

    /**
     * Reducer class, reduces the map of dots
     *
     * @author jgp
     */
    private final class DartReducer implements ReduceFunction<Integer> {
        private static final long serialVersionUID = 12859L;

        @Override
        public Integer call(Integer x, Integer y) {
            return x + y;
        }
    }

    /**
     * main() is your entry point to the application.
     *
     * @param args
     */
    public static void main(String[] args) {
        PiComputeApp app = new PiComputeApp();
            app.start(25);
            //using a 100 slices on a single laptop with 4 cores derailes the program
        //java.lang.OutOfMemoryError: Java heap space
    }

    /**
     * The processing code.
     * @param slices multiplied by 100_000 it constitutes the number of throws
     */
    private void start(int slices) {
        int numberOfThrows = 100_000 * slices;
        System.out.println("About to throw " + numberOfThrows
                + " darts, ready? Stay away from the target!");

        long t0 = System.currentTimeMillis();
        SparkSession spark = SparkSession
                .builder()
                .appName("Spark Pi")
                //.master("local[*]") // all available cpu resources (cores/threads)
                .master("local[4]") //limit the number of resources
                .getOrCreate();
        long t1 = System.currentTimeMillis();
        System.out.println("Session initialized in " + (t1 - t0) + " ms");

        List<Integer> l = new ArrayList<>(numberOfThrows);
        for (int i = 0; i < numberOfThrows; i++) {
            l.add(i);
        }
        Dataset<Row> incrementalDf = spark
                .createDataset(l, Encoders.INT())
                .toDF();

        long t2 = System.currentTimeMillis();
        System.out.println("Initial dataframe built in " + (t2 - t1) + " ms");

        //will contain 1 for every throw within the circle and 0 for every throw outside the circle
        Dataset<Integer> dartsDs = incrementalDf
                .map(new DartMapper(), Encoders.INT());

        long t3 = System.currentTimeMillis();
        System.out.println("Throwing darts done in " + (t3 - t2) + " ms");

        //calculating the amounts of darts in the circle
        int dartsInCircle = dartsDs.reduce(new DartReducer());
        long t4 = System.currentTimeMillis();
        System.out.println("Analyzing result in " + (t4 - t3) + " ms");

        System.out
                .println("Pi is roughly " + 4.0 * dartsInCircle / numberOfThrows);

        spark.stop();
    }
}
