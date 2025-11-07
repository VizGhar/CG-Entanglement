import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class TestPlayer {
    public static void main(String[] args) {
        try {
            File f = new File("26.txt");
            Scanner s = new Scanner(f);
            while (s.hasNext()) {
                System.out.println(s.nextLine());
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
