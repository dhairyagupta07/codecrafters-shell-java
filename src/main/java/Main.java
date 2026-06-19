import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();

            // exit stage (usually already required)
            if (input.equals("exit 0")) {
                break;
            }

            // invalid command handling (this is #CZ2)
            System.out.println(input + ": command not found");
        }

        sc.close();
    }
}