package org.sf.feeling.decompiler.util;

import static org.sf.feeling.decompiler.util.TextAssert.assertEquivalent;

import org.junit.Test;

public class DecompilerOutputUtilTest {

    @Test
    public void testToStringExampleOneFernFlower() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                package sample.one;

                import java.util.Arrays;

                public class ExampleOne {
                    public void run() {
                        for (String value : Arrays.asList("a", "b")) {// 8 9
                            System.out.println(value);// 10
                        }
                    }// 12
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*    */ package sample.one;
                        /*    */
                        /*    */ import java.util.Arrays;
                        /*    */
                        /*    */
                        /*    */ public class ExampleOne {
                            /*    */    public void run() {
                                /*  8 */       for (String value : Arrays.asList("a", "b")) {
                                    /*    */
                                    /* 10 */          System.out.println(value);
                                /*    */       }
                            /*    */    }
                        /*    */ }
                        """, output);
    }

    @Test
    public void testToStringExampleTwoFernFlower() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                package sample.two;

                public class ExampleTwo {
                    public void run() {
                        System.out.println("Hello");// 6
                    }// 7
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*   */ package sample.two;
                        /*   */
                        /*   */
                        /*   */ public class ExampleTwo {
                            /*   */    public void run() {
                                /* 6 */       System.out.println("Hello");
                            /*   */    }
                        /*   */ }
                        """, output);
    }

    @Test
    public void testToStringExampleThreeFernFlower() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                import java.util.*;

                public class ExampleThree {
                    public void run() {
                        Set<String> values = new HashSet();// 6
                        values.add("x");// 7
                        values.add("y");// 8

                        for (String value : values) {// 9
                            System.out.println(value);// 10
                        }
                    }// 12
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*    */ import java.util.*;
                        /*    */
                        /*    */
                        /*    */ public class ExampleThree {
                            /*    */    public void run() {
                                /*  6 */       Set<String> values = new HashSet();
                                /*  7 */       values.add("x");
                                /*  8 */       values.add("y");
                                /*  9 */       for (String value : values) {
                                    /* 10 */          System.out.println(value);
                                /*    */       }
                            /*    */    }
                        /*    */ }
                        """, output);
    }

    @Test
    public void testToStringExampleFourFernFlower() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""

                public class ExampleFour {
                    public void run() {
                        System.out.println("No package and no import");// 4
                    }// 5
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*   */
                        /*   */ public class ExampleFour {
                            /*   */    public void run() {
                                /* 4 */       System.out.println("No package and no import");
                            /*   */    }
                        /*   */ }
                        """, output);
    }

    @Test
    public void testToStringExampleOneProcyon() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                package sample.one;

                import java.util.*;

                public class ExampleOne
                {
                    public void run() {
                        /* 8*/        final List<String> data = Arrays.asList("a", "b");
                        /* 9*/        for (final String value : data) {
                            /*10*/            System.out.println(value);
                        }
                    }
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*    */ package sample.one;
                        /*    */
                        /*    */ import java.util.*;
                        /*    */
                        /*    */ public class ExampleOne
                        /*    */ {
                            /*    */     public void run() {
                                /*  8 */         final List<String> data = Arrays.asList("a", "b");
                                /*  9 */         for (final String value : data) {
                                    /* 10 */             System.out.println(value);
                                /*    */         }
                            /*    */     }
                        /*    */ }
                        """, output);
    }

    @Test
    public void testToStringExampleTwoProcyon() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                package sample.two;

                public class ExampleTwo
                {
                    public void run() {
                        /*6*/        System.out.println("Hello");
                    }
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*   */ package sample.two;
                        /*   */
                        /*   */ public class ExampleTwo
                        /*   */ {
                            /*   */     public void run() {
                                /* 6 */         System.out.println("Hello");
                            /*   */     }
                        /*   */ }
                        """, output);
    }

    @Test
    public void testToStringExampleThreeProcyon() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                import java.util.*;

                public class ExampleThree
                {
                    public void run() {
                        /* 6*/        final Set<String> values = new HashSet<String>();
                        /* 7*/        values.add("x");
                        /* 8*/        values.add("y");
                        /* 9*/        for (final String value : values) {
                            /*10*/            System.out.println(value);
                        }
                    }
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""
                        /*    */ import java.util.*;
                        /*    */
                        /*    */ public class ExampleThree
                        /*    */ {
                            /*    */     public void run() {
                                /*  6 */         final Set<String> values = new HashSet<String>();
                                /*  7 */         values.add("x");
                                /*  8 */         values.add("y");
                                /*  9 */         for (final String value : values) {
                                    /* 10 */             System.out.println(value);
                                /*    */         }
                            /*    */     }
                        /*    */ }
                        """, output);
    }

    @Test
    public void testToStringExampleFourProcyon() {
        DecompilerOutputUtil decompilerOutputUtil = new DecompilerOutputUtil("""
                public class ExampleFour
                {
                    public void run() {
                        /*4*/        System.out.println("No package and no import");
                    }
                }
                """);
                decompilerOutputUtil.realign();
                String output = decompilerOutputUtil.toString();
                assertEquivalent("""

                        /*   */ public class ExampleFour{
                            /*   */     public void run() {
                                /* 4 */         System.out.println("No package and no import");
                            /*   */     }
                        /*   */ }
                        """, output);
    }

    @Test
    public void testTryCatchFernFlower() {
        DecompilerOutputUtil util = new DecompilerOutputUtil("""
                package test;

                public class ExampleTry {
                    public static void main(String[] args) {
                        try {
                            Thread.sleep(100L);// 7
                        } catch (InterruptedException e) {// 8
                            e.printStackTrace();// 9
                        }

                    }// 11
                }
                """
                );

        util.realign();
        String output = util.toString();

        assertEquivalent("""
                /*    */ package test;
                /*    */
                /*    */
                /*    */ public class ExampleTry {
                    /*    */   public static void main(String[] args) {
                        /*    */     try {
                            /*  7 */       Thread.sleep(100L);
                        /*  8 */     } catch (InterruptedException e) {
                            /*  9 */       e.printStackTrace();
                        /*    */     }
                        /*    */
                    /*    */   }
                /*    */ }
                """, output);
    }

}
