/************************************************************************************
 *
 * Class name:    Parser
 * Description:   Handles the parsing of a single .vm file, and encapsulates access
 *                to the input code. It reads VM commands, parses them, and provides
 *                convenient access to their components. In addition, it removes all
 *                whitespace and comments.
 *
 * History:       Mar. 12, J, author, stubs & documentation based off of Ch. 7 UML
 *                Mar. 13, J, defined and tested all methods
 *                Mar. 14, J, cleaned up unneeded methods & finalized Parser for part 1
 *				  Mar. 18, J, fixed bug with isArithmeticCMD() [contains instead of equals]
 *
 * Methods:       Public:   Parser(String), hasMoreCommands(), advance()
 *                          getCommandType(), getCommand(), getArg1(), getArg2()
 *
 *                Private:  cleanLine(), parseCommandType(), isArithmeticCMD(), parse()
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

class Parser {

    /*************
     * Constants *
     *************/
    enum Command {
        C_ARITHMETIC,
        C_PUSH,
        C_POP,
        C_LABEL,
        C_GOTO,
        C_IF,
        C_FUNCTION,
        C_RETURN,
        C_CALL,
        C_NONE  //  Blank lines permitted & ignored
    }
    //  Options for the C_ARITHMETIC Command type
    private static final String[] C_ARITH_STRINGS = {"add",  "sub",  "neg",
            "eq",   "gt",   "lt",
            "and",  "or",   "not"};

    //  Options for all other Command types, aligned with Command enum.
    private static final String[] C_OTHER_STRINGS = { "push",    "pop",      "label",
            "goto",    "if-goto",  "function",
            "return",  "call"};

    /**********************
     * Instance Variables *
     **********************/

    //  File Management & Internal Debugging
    private Scanner inputFile;
    private String rawLine;

    //  Parsed Command Parts
    private String cleanLine;
    private Command commandType;
    private String command;
    private String arg1;
    private String arg2;

    /****************
     * Constructors *
     ****************/

    /**
     * Opens input file/stream and prepares to parse it.
     *
     * PRECONDITION:    a .vm file name to be opened has been collected/received
     * POSTCONDITION:   the file has been opened and is ready for parsing,
     *                  or a FileNotFoundException has been thrown
     *
     * @param   inFileName  -   the name of the file to be opened and read from
     */
    Parser(String inFileName) throws  FileNotFoundException {
        if (inFileName != null) {
            inputFile = new Scanner(new File(inFileName));
            rawLine = "";
            cleanLine = "";
            commandType = null;
            command = "";
            arg1 = "";
            arg2 = "";
        } else {
            throw new FileNotFoundException("No input file name provided to Parser constructor.");
        }
    }

    /***********************
     * Public File Methods *
     ***********************/

    /**
     * Determines whether or not more commands are left in the file.
     * If there are no more commands left, the stream is closed.
     *
     * PRECONDITION:    the file stream has been opened
     * POSTCONDITION:   the file is ready for more reading (true) OR
     *                  the file has been closed (false)
     *
     * @return      -   true if there are more commands to be read, false otherwise
     */
    boolean hasMoreCommands() {
        //  Scanner's hasNextLine() throws an IllegalStateException if the scanner is closed. Check this first.
        try {
            if (inputFile.hasNextLine()) {
                //  inputFile is still open and there are more lines to be read
                return true;
            } else {
                //  No lines left & inputFile is still open -> close it.
                inputFile.close();
                return false;
            }
        } catch (IllegalStateException e) {
            //  inputFile has been closed by a previous call to hasMoreCommands()
            return false;
        }
    }

    /**
     * Reads next line from file and parses it into the appropriate instance variables.
     *
     * PRECONDITION:    there are more lines in the file to parse (check with hasMoreCommands() first)
     *                  & initially, there is no current command
     * POSTCONDITION:   the next instruction has been read in and cleaned, and the parse method is
     *                  breaking the instruction into its necessary parts
     */
    void advance() {
        //  Read the line in
        rawLine = inputFile.nextLine();
        //  Clean it up
        cleanLine();
        //  Break it down
        parse();
    }

    /******************
     * Helper Methods *
     ******************/

    /**
     * Cleans the command in rawLine by removing non-essential parts from the line.
     *
     * PRECONDITION:    rawLine contains the current VM command and is not null (advance() has been called)
     * POSTCONDITION:   cleanLine holds the rawLine, but with no comments or leading or ending whitespace
     */
    private void cleanLine() {
        cleanLine = rawLine;
        int commentIndex = rawLine.indexOf("//");
        if (commentIndex != -1) {
            cleanLine = rawLine.substring(0, commentIndex);
        }
        cleanLine = cleanLine.trim();
    }

    /**
     * Determines the command type of the command in cleanLine, and updates commandType accordingly.
     *
     * PRECONDITION:    the VM command in cleanLine has no comments or leading or ending whitespace (cleanLine() has been called)
     *                  and the command instance variable holds the current command (parse() has been called)
     * POSTCONDITION:   commandType holds the current VM command's type, as a Command
     */
    private void parseCommandType() {
        if (command.equals("")) {
            //  VM command is a blank line or comment
            commandType = Command.C_NONE;
        } else if (isArithmeticCMD()) {
            //  VM command performs arithmetic or logic operations on the stack
            commandType = Command.C_ARITHMETIC;
        } else {
            //  Get all possible Commands
            Command[] types = Command.values();
            for(int i = 0; i < C_OTHER_STRINGS.length; i++) {
                //  Valid commands are not case-sensitive [push = Push = PUSH]
                if (command.toLowerCase().equals(C_OTHER_STRINGS[i])) {
                    //  Arithmetic has been handled - start from "push" & set command type if match is found
                    commandType = types[i + 1];
                    return;
                }
            }
        }
    }

    /**
     * Used by parseCommandType() to check if the VM command in cleanLine is a valid arithmetic and logical stack command.
     *
     * PRECONDITION:    the command in cleanLine has no comments or leading or ending whitespace (cleanLine() has been called)
     *                  and the command instance variable holds the current command (parse() has been called)
     * POSTCONDITION:   returns true if the command in cleanLine is of type C_ARITHMETIC, false otherwise
     *
     * @return  -   true if a C_ARITH_STRINGS element is found in cleanLine, false otherwise
     */
    private boolean isArithmeticCMD() {
        for( String element : C_ARITH_STRINGS ) {
            //  Valid commands are not case-sensitive [and = And = AND]
            if (cleanLine.toLowerCase().equals(element)) {
                return true;
            }
        }
        //  If no match, VM command in cleanLine is not a C_ARITHMETIC command.
        return false;
    }

    /**
     * Parses the line depending on the command type.
     *
     * PRECONDITION:    a new command has been read in and cleaned -> advance() and cleanLine have been called
     * POSTCONDITION:   the current command has been updated across the command, commandType, arg1, and arg2
     *                  instance variables
     */
    private void parse() {
        /*  Ch. 7, pg. 130
         * "VM command...arguments are separated from each other and from the command part by one or more spaces."
         *  ->  So, split the cleaned line, using whitespace as a delimiter, into command, arg1, and arg2.
         */
        //  Split the VM command on 1 or more spaces in between command & the two potential arguments
        String[] parts = cleanLine.split("\\s+");

        /*  There are 0-3 potential number of parts. So, for each, avoid an out of bounds exception,
         *  and map either the found part, or an empty string, to each instance variable in turn.
         */
        //  if parts[0] exists -> command = parts[0]
        //  else -> command = ""
        //  i++ -> arg1 -> repeat for arg2
        int i = 0;
        command = i < parts.length ? parts[i++] : "";
        arg1 = i < parts.length ? parts[i++] : "";
        arg2 = i < parts.length ? parts[i] : "";

        //  Finally, determine the command type
        parseCommandType();
    }

    /***********
     * Getters *
     ***********/

    /**
     * Getter for command type.
     *
     * PRECONDITION:    the type has already been determined (advance() has been called -> parse() -> parseCommandType())
     * POSTCONDITION:   N/A
     *
     * @return      -   the command type as a Command
     */
    Command getCommandType() {
        return commandType;
    }

    /**
     * Getter for the command part of a VM command
     *
     * PRECONDITION:    the command instance variable holds the current command (advance() has been called -> parse())
     * POSTCONDITION:   N/A
     *
     * @return      -   the command part of the current VM command, or an empty string
     */
    String getCommand() {
        return command;
    }

    /**
     * Getter for argument 1 of a VM command
     *
     * PRECONDITION:    the arg1 instance variable holds the first argument of the current command
     *                  (advance() has been called -> parse())
     *                  the current command is not C_RETURN (check with getCommandType() first)
     * POSTCONDITION:   N/A
     *
     * @return      -   the first argument of the current VM command, or an empty string
     */
    String getArg1() {
        return arg1;
    }

    /**
     * Getter for argument 2 of a VM command
     *
     * PRECONDITION:    the arg2 instance variable holds the second argument of the current command
     *                  (advance() has been called -> parse())
     *                  the current command is C_PUSH, C_POP, C_FUNCTION, or C_CALL (check with getCommandType() first)
     * POSTCONDITION:   N/A
     *
     * @return      -   the second argument of the current VM command, or an empty string
     */
    String getArg2() {
        return arg2;
    }
}


