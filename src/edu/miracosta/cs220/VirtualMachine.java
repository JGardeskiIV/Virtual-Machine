/************************************************************************************
 *
 * Class name:    VirtualMachine
 * Description:   Determines .vm files to translate through user input or command-line
 *                arguments, translating all specified files to a single .asm file.
 *
 * History:       Mar. 13, J, author, testing of Parser methods
 *                Mar. 14, J, testing of CodeWriter methods & overall part 1 translation
 *                TODO  -   refactor translation into a translate() method,
 *                TODO  -   and build method(s) for opening a single file, checking
 *                TODO  -   command-line arguments, or browsing a directory
 *
 * Methods:       Public:   main(String)
 *
 *                Private:
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.FileNotFoundException;

public class VirtualMachine {

    public static void main(String[] args) {
        Parser.Command commandType;
        String command, arg1, arg2;
        try {
            Parser parser = new Parser("StaticTest.vm");
            CodeWriter codeWriter = new CodeWriter("StaticTest.asm", "StaticTest.vm");
            while(parser.hasMoreCommands()) {
                parser.advance();
                command = parser.getCommand();
                commandType = parser.getCommandType();
                arg1 = parser.getArg1();
                arg2 = parser.getArg2();
                if (commandType == Parser.Command.C_ARITHMETIC) {
                    codeWriter.writeArithmetic(command);
                } else if (commandType == Parser.Command.C_POP ||
                           commandType == Parser.Command.C_PUSH) {
                    codeWriter.writePushPop(commandType, arg1, Integer.parseInt(arg2));
                } else {

                }
            }
            codeWriter.close();
        } catch (FileNotFoundException e ) {

        }
    }
}
