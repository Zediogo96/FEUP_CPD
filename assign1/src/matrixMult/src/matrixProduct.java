import java.util.Scanner;

public class matrixProduct {
    public static void OnMult(int lin) {
        int i, j, k;

        double[][] pha = new double[lin][lin];
        double[][] phb = new double[lin][lin];
        double[][] phc = new double[lin][lin];

        for (i = 0; i < lin; i++)
            for (j = 0; j < lin; j++)
                pha[i][j] = 1.0;

        for (i = 0; i < lin; i++)
            for (j = 0; j < lin; j++)
                phb[i][j] = (double) i + 1;

        double startTime = System.currentTimeMillis();

        for (i = 0; i < lin; i++) {
            for (j = 0; j < lin; j++) {
                for (k = 0; k < lin; k++) {
                    phc[i][j] += pha[i][k] * phb[k][j];
                }
            }
        }

        double endTime = System.currentTimeMillis();
        double numSeconds = ((endTime - startTime) / 1000);

        System.out.println("Time: " + numSeconds + " seconds");

        System.out.println("Result matrix: ");
        for (j = 0; j < Math.min(10, lin); j++)
            System.out.print(phc[1][j] + " ");

        System.out.println("\n");
    }

    public static void OnMultLine(int lin) {
        int i, j, k;

        double[][] pha = new double[lin][lin];
        double[][] phb = new double[lin][lin];
        double[][] phc = new double[lin][lin];

        for (i = 0; i < lin; i++)
            for (j = 0; j < lin; j++)
                pha[i][j] = 1.0;

        for (i = 0; i < lin; i++)
            for (j = 0; j < lin; j++)
                phb[i][j] = (double) i + 1;

        double startTime = System.currentTimeMillis();

        for (i = 0; i < lin; i++) {
            for (k = 0; k < lin; k++) {
                for (j = 0; j < lin; j++) {
                    phc[i][j] += pha[i][k] * phb[k][j];
                }
            }
        }

        double endTime = System.currentTimeMillis();
        double numSeconds = ((endTime - startTime) / 1000);

        System.out.println("Time: " + numSeconds + " seconds");

        System.out.println("Result matrix: ");
        for (j = 0; j < Math.min(10, lin); j++)
            System.out.print(phc[1][j] + " ");

        System.out.println("\n");
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        int lin, op;
        System.out.println("TP 1 - Java version\n");

        while (true) {
            System.out.println("1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("0. Exit");
            System.out.print("Selection?: ");
            op = in.nextInt();
            System.out.println();
            if (op == 0) break;

            System.out.print("Dimensions: ");
            lin = in.nextInt();
            System.out.println();

            switch (op) {
                case 1 -> OnMult(lin);
                case 2 -> OnMultLine(lin);
            }
        }

        in.close();
    }
}
