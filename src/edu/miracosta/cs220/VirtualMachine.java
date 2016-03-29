/************************************************************************************
 *
 * Class name:    VirtualMachine
 * Description:   Determines .vm file(s) to translate through user input or command-line
 *                arguments, translating all specified files to a single .asm file.
 *
 * History:       Mar. 13, J, author, testing of Parser methods
 *                Mar. 14, J, testing of CodeWriter methods & overall part 1 translation
 *                Mar. 16, J, finalizing of JFileChooser usage & part 1 translation
 *				  Mar. 19, J, adjusted VM file/directory gathering & handling
 *                Mar. 28, J, refactored translation into a translate() method,
 *							  added repeated file/directory selection
 *
 * Methods:       Public:   main(String)
 *
 *                Private:	translate(File[], CodeWriter), getBootstrap(String),
 *							convertFileName(String), getFileArray(String),
 *							useFileChooser(), useSystemLookAndFeel()
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

class VirtualMachine {

    /**
     * VMFilter is intended to be utilized in conjunction with the File.listFiles(FilenameFilter)
     * method for use in constructing a File array of only virtual machine files in a directory.
     */
    private static class VMFilter implements FilenameFilter {
        /**
         * Tests if a specified file should be included in a file list.
         *
         * PRECONDITION:    a directory has been successfully opened
         * POSTCONDITION:   a file should be added or excluded from a file list
         *
         * @param   dir         -   the directory in which the file was found
         * @param   pathname    -   the name of the file
         * @return              -   true if and only if pathname should be included
         *                          in the file list; false otherwise
         */
        @Override
        public boolean accept(File dir, String pathname) {
            return pathname.toLowerCase().endsWith(".vm");
        }
    }

    /**
     * Constructs a single .asm file from any number of .vm files.
     * ->	First checks command-line arguments for VM file or directory
     *		data; if not present, uses a JFileChooser to gather said data
     *		and perform VM to Assembly translation, provided data is valid.
     *
     * PRECONDITION:	command-line arguments may have been supplied
     * POSTCONDITION:	the translated .asm file is in the same directory
     *					as the one given (directly or to contain the .vm file)
     *
     * @param	args	-	the supplied command-line arguments
     */
    public static void main(String[] args) {

        useSystemLookAndFeel();

        //	Be helpful!
        String instructions = "This VM allows a single file or directory to be entered\n" +
                " via the command-line, or the repeated selection of such\n" +
                " using a JFileChooser. Simply close the file chooser or\n" +
                " click \"cancel\" when finished translating all projects.";

        JOptionPane.showMessageDialog(null, instructions, "Instructions", JOptionPane.INFORMATION_MESSAGE);

        try {
            File input; // a file or dir
            File[] filesToTranslate;
            CodeWriter codeWriter;
            //	Flag for repeated file/directory selection
            boolean translateAgain = true;

            do {
                //	Get a file or directory from the command-line or a JFileChooser
                if (args.length == 1) {
                    System.out.println("Command-line arg: " + args[0]);
                    input = new File(args[0]);
                    //	Only accept one file or directory via command-line
                    translateAgain = false;
                } else {
                    input = useFileChooser();
                }

                //	If user closed JFileChooser or command-line args invalid, end program.
                if (input == null) {
                    System.out.println("No file or directory selected.");
                    break;
                }

                //	Otherwise, determine if the input is a file or directory
                if (input.isDirectory()) {
                    //	Directory: get all .vm files in the directory
                    filesToTranslate = getFileArray(input.getPath());
                } else {
                    //	File: get the directory & file paths
                    filesToTranslate = new File[1];
                    filesToTranslate[0] = input;
                    input = input.getParentFile();
                }
                //	By this point, input is the parent directory to write in

                //  If no files have been gathered, however, something went wrong
                if (filesToTranslate == null || filesToTranslate.length == 0) {
                    System.err.println("No .vm files found in \"" + input.getPath() + "\"");
                    break;
                }

                //	Establish the ONLY codeWriter for translation
                System.out.println("Processing " + input.getPath());
                String outFileName = convertFileName(input.getName());	//	.asm name to write to
                boolean includeBootstrap = getBootstrap(outFileName);	//	user's choice to include bootstrap code

                //	Build it & translate
                codeWriter = new CodeWriter(input, outFileName, includeBootstrap);
                translate(filesToTranslate, codeWriter);

                //  Clean up [Parser.hasMoreCommands() handles parsers in translate(File[], CodeWriter)]
                codeWriter.close();
                System.out.println("Translation complete to: " + outFileName + "\n");
            } while (translateAgain);
        } catch (FileNotFoundException e) {
            //	Check any Parser/CodeWriter/Command-line errors
            e.printStackTrace();
        }
        System.out.println("Program closing...");
        //	Run garbage collector to prevent random InterruptedException...?
        System.gc();
    }

    /*****************************************
     * Translation & File Management Helpers *
     *****************************************/

    /**
     * Iterates through each line of each file in the passed in array [advance()],
     * determines its' command, and calls the appropriate CodeWriter write method
     * until translation for the passed in program has completed.
     *
     * PRECONDITION:	filesToTranslate & codeWriter are not null
     * POSTCONDITION:	the translation of a file or directory has been completed
     *
     * @param	filesToTranslate	-	an array of files to translate into a single
     *									.asm file (handled by codeWriter)
     * @param	codeWriter			-	a CodeWriter object to handle translation
     *									of each .vm file in filesToTranslate into
     *									the appropriate assembly code
     *
     * @throws	FileNotFoundException	-	if a Parser cannot be opened for a file
     *										within the filesToTranslate array
     */
    private static void translate(File[] filesToTranslate, CodeWriter codeWriter) throws FileNotFoundException {
        //	Setup
        Parser parser;
        Parser.Command commandType;
        String command, arg1, arg2;
        String currentVMfileName;

        /*	Iterate through each line in the selected .vm file, [advance()],
         *	determine its' command, and call the appropriate CodeWriter method
         *  until translation is complete.
         */
        for( File file : filesToTranslate ) {
            //  Grab the name of each file to create a new Parser object and update codeWriter.
            currentVMfileName = file.getName();
            System.out.println("Processing " + currentVMfileName);

            //	Create a new Parser for every file, & update codeWriter
            parser = new Parser(file.getPath());
            codeWriter.setFileName(convertFileName(currentVMfileName));

            //  Translate as long as lines are available
            while(parser.hasMoreCommands()) {
                //  Move to next line
                parser.advance();

                //  Update vars after parsing
                command = parser.getCommand();
                commandType = parser.getCommandType();
                arg1 = parser.getArg1();
                arg2 = parser.getArg2();

                //  Translate based on commandType
                if (commandType == Parser.Command.C_ARITHMETIC)
                {
                    codeWriter.writeArithmetic(command);
                }
                else if (commandType == Parser.Command.C_POP ||
                        commandType == Parser.Command.C_PUSH)
                {
                    codeWriter.writePushPop(commandType, arg1, Integer.parseInt(arg2));
                }
                else if (commandType == Parser.Command.C_LABEL)
                {
                    codeWriter.writeLabel(arg1);
                }
                else if (commandType == Parser.Command.C_GOTO)
                {
                    codeWriter.writeGoto(arg1);
                }
                else if (commandType == Parser.Command.C_IF)
                {
                    codeWriter.writeIfGoto(arg1);
                }
                else if (commandType == Parser.Command.C_CALL)
                {
                    codeWriter.writeCall(arg1, Integer.parseInt(arg2));
                }
                else if (commandType == Parser.Command.C_FUNCTION)
                {
                    codeWriter.writeFunction(arg1, Integer.parseInt(arg2));
                }
                else if (commandType == Parser.Command.C_RETURN)
                {
                    codeWriter.writeReturn();
                }
                //	else commandType == Parser.Command.C_NONE
            }
            //	file has no more lines and parser has been closed
        }
        //	all files have been translated
    }

    /**
     * Uses a JOptionPane to ask the user whether bootstrap code should be
     * included in the translated .asm file.
     *
     * PRECONDITION:	a file or directory has been selected
     * POSTCONDITION:	bootstrap inclusion has been returned and can be
     *					passed to the CodeWriter constructor
     *
     * @param	outputFileName	-	the name of the .asm file to be created
     * @return					-	true if bootstrap code should be included, false otherwise
     */
    private static boolean getBootstrap(String outputFileName)
    {
        int choice = JOptionPane.showConfirmDialog(null,
                "Include bootstrap code for " + outputFileName + "?",
                "Bootstrap Selection",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * Converts a file name ending in ".vm" to a file name ending in ".asm"
     *
     * PRECONDITION:    fileName ends with ".vm" (check with endsWith())
     * POSTCONDITION:   fileName is returned as ending in ".asm"
     *
     * @param   fileName    -   a .vm file name
     * @return              -   an .asm file name
     */
    private static String convertFileName(String fileName) {
        int fileExt = fileName.indexOf(".");
        if (fileExt != -1) {
            fileName = fileName.substring(0, fileExt);
        }
        return fileName + ".asm";
    }

    /**
     * Determines if a String represents a file name or directory, and
     * returns an array of the files to be translated.
     *
     * PRECONDITION:	N/A
     * POSTCONDITION:	at least one file name or directory has been gathered
     *					and returned, or an error occurred, and the returned
     *					array is null
     *
     * @param	pathname    -	the name of a file or directory
     * @return			    -	an array of files to be translated to assembly, or null
     *
     * @throws	FileNotFoundException	-	if pathname cannot be found/opened
     */
    private static File[] getFileArray(String pathname) throws FileNotFoundException {
        if (pathname == null) {
            //  File(String) constructor throws an NPE if null is supplied.
            return null;
        } else {
            //  pathname can represent a file or directory
            File temp = new File(pathname);
            if (pathname.endsWith(".vm")) {
                //  File, return it as an array of 1
                return temp.listFiles();
            } else {
                //  Directory, return ONLY .vm files
                return temp.listFiles(new VMFilter());
            }
        }
    }

    /****************************************
     * JFileChooser & Look and Feel Helpers *
     ****************************************/

    /**
     * Constructs and shows a JFileChooser to the user for the selection
     * of a VM file or the directory of a VM program to translate to assembly.
     *
     * PRECONDITION:	command-line arguments have NOT been supplied
     * POSTCONDITION:	the user has selected a file or directory to be translated,
     *					or an error occurred, and the array returned = null
     *
     * @return	-	a file or directory to be translated to assembly, or null
     * @throws	FileNotFoundException	-	if selected file cannot be opened by getFileArray()
     */
    private static File useFileChooser() throws FileNotFoundException {
        //	Construct a JFileChooser with the directory of the source code
        //	as the current directory
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));

        //	Allow selection of a file or directory
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        //	Allow the selection of ONLY a .vm file or directory.
        FileNameExtensionFilter filter = new FileNameExtensionFilter("VM Files", "vm");
        chooser.setFileFilter(filter);

        //	Show the JFileChooser
        int returnVal = chooser.showDialog(null, "Translate");
        //	Handle results
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            //	Selection can be 1 file, 1 or more files, or a directory
            return chooser.getSelectedFile();
        } else {
            return null;
        }
    }

    /**
     * Uses the system's look and feel, if available, for a JFileChooser (any/all GUI elements).
     *
     * PRECONDITION:	N/A
     * POSTCONDITION:	the system's L&F has been applied, or many error messages
     *					have filled the console screen
     */
    private static void useSystemLookAndFeel() {
        //	Use the system's look and feel (if available)
        try {
            //	Use the system's look and feel for the JFileChooser (set L&F FIRST!)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("System Look and Feel unavailable. Stack Trace: ");
            e.printStackTrace();
            System.out.println("End Stack Trace.\n");
        }
    }
}


