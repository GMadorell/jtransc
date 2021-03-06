package javatest.lang;

/**
 * Created by mike on 4/11/15.
 */
public class SystemTest {

    public static void main(String[] args) throws Throwable {
        systemOutTest();
        systemPropertiesTest();
    }

    private static void systemOutTest() {
        System.out.print("HELLO");
        System.out.println(" WORLD!");
        System.out.println("HELLO WORLD!");
    }

    private static void systemPropertiesTest() {
        System.out.println("java.runtime.name:" + System.getProperty("java.runtime.name"));
        System.out.println("path.separator:" + System.getProperty("path.separator"));
    }

}