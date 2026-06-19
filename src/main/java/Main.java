import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();

            if (input.equals("exit")) {
                break;
            }

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }

            else if (input.startsWith("type ")) {
                String cmd = input.substring(5);

                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    System.out.println(cmd + ": not found");
                }
            }

            else {
                System.out.println(input + ": command not found");
            }
        }

        sc.close();
    }
}