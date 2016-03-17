/************************************************************************************
 *
 * Class name:    VirtualMachine
 * Description:   Determines .vm files to translate through user input or command-line
 *                arguments, translating all specified files to a single .asm file.
 *
 * History:       Mar. 13, J, author, testing of Parser methods
 *                Mar. 14, J, testing of CodeWriter methods & overall part 1 translation
 *                Mar. 16, J, finalizing of JFileChooser usage & part 1 translation
 *                TODO  -   refactor translation into a translate() method,
 *
 * Methods:       Public:   main(String)
 *
 *                Private:
 *
 ************************************************************************************/
package edu.miracosta.cs220;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

public class VirtualMachine {

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

        try {
            File[] filesToTranslate;
            if (args.length == 1) {
                System.out.println("command line arg = " + args[0]);
                filesToTranslate = getFileArray(args[0]);
            } else {
                filesToTranslate = useFileChooser();
            }

            //  If no files have been gathered, something went wrong
            if (filesToTranslate == null) {
                throw new FileNotFoundException("No files available from command-line or JFileChooser.");
            }

            //	Get to work translating!
            Parser parser;
            Parser.Command commandType;
            String command, arg1, arg2;
            String currentVMfile = filesToTranslate[0].getPath();   //  Start with the first file
            CodeWriter codeWriter = new CodeWriter(convertFileName(currentVMfile));

            //	Establish Part 1 driver first - iterate through each line in
            //	the selected .vm file, [advance()], determine its' command,
            //	and call the appropriate CodeWriter method, until translation
            //	is complete.
            for( File file : filesToTranslate ) {
                //  Grab the name of each file to create a new Parser object and update codeWriter.
                currentVMfile = file.getPath();

                parser = new Parser(currentVMfile);
                codeWriter.setFileName(convertFileName(currentVMfile));

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
                    if (commandType == Parser.Command.C_ARITHMETIC) {
                        codeWriter.writeArithmetic(command);
                    } else if (commandType == Parser.Command.C_POP ||
                            commandType == Parser.Command.C_PUSH) {
                        codeWriter.writePushPop(commandType, arg1, Integer.parseInt(arg2));
                    } else {
                        //  Program flow control handling goes here
                        //  TODO - part 2 (and anywhere else)
                    }
                }
            }
            //  Clean up [Parser.hasMoreCommands() should have closed the last parser object]
            codeWriter.close();

        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /*****************************************
     * Translation & File Management Helpers *
     *****************************************/

    //  STUB - TODO - refactor for loop from main's try block into this method (and any others needed)!
    private static void translate() {

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
        return fileName.substring(0, fileName.indexOf(".")) + ".asm";
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
     * of VM files or the directory of a VM program to translate to assembly.
     *
     * PRECONDITION:	command-line arguments have NOT been supplied
     * POSTCONDITION:	the user has selected files to be translated,
     *					or an error occurred, and the array returned = null
     *
     * @return	-	an array of files to be translated to assembly, or null
     * @throws	FileNotFoundException	-	if selected file cannot be opened by getFileArray()
     */
    private static File[] useFileChooser() throws FileNotFoundException {
        JFileChooser chooser = new JFileChooser();

        //	Allow selection of a file or directory
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        //	Allow the selection of ONLY .vm files, but as many as desired.
        chooser.setMultiSelectionEnabled(true);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("VM Files", "vm");
        chooser.setFileFilter(filter);

        //	Show the JFileChooser
        int returnVal = chooser.showDialog(null, "Translate");
        //	Handle results
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            //	Selection can be 1 file, 1 or more files, or a directory
            File first = chooser.getSelectedFile();
            if (first.isDirectory()) {
                //	If it's a directory, use helper to gather .vm files to translate
                return getFileArray(first.getPath());
            } else {
                //	Otherwise, just return the selected file(s)
                return chooser.getSelectedFiles();
            }
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
